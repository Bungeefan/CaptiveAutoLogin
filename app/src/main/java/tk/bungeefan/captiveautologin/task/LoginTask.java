package tk.bungeefan.captiveautologin.task;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.CaptivePortal;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.ViewModelProvider;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.bungeefan.captiveautologin.LoginUtil;
import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.Util;
import tk.bungeefan.captiveautologin.activity.MainActivity;
import tk.bungeefan.captiveautologin.activity.WebViewActivity;
import tk.bungeefan.captiveautologin.data.LoginViewModel;
import tk.bungeefan.captiveautologin.data.entity.Login;

public class LoginTask extends AsyncTask<String, String, String> {

    public static final String FAILED_EXTRA = "failed";
    public static final String WIFI_DATA_EXTRA = "WifiData";
    public static final String RESPONSE_EXTRA = "Response";
    private static final String URL_DETECT_PORTAL = "http://detectportal.firefox.com/success.txt";
    private static final String TAG = LoginTask.class.getSimpleName();
    public static boolean taskRunning = false;
    private final CaptivePortal captivePortal;
    private final Network network;
    private final Login loginData;
    private final CompositeDisposable mDisposable = new CompositeDisposable();
    private final NotificationManagerCompat mNotificationManager;
    private final ConnectivityManager mConnectivityManager;
    private final WeakReference<MainActivity> mContext;
    private final LoginViewModel mLoginViewModel;
    private URL lastUrl;
    private final String requestMethod = "POST";
    private boolean failed = false;
    private boolean silent;
    private final int notificationId = 0;

    public LoginTask(MainActivity context, Login loginData, CaptivePortal captivePortal, Network network, boolean silent) {
        if (taskRunning) {
            throw new IllegalThreadStateException("Another " + this.getClass().getSimpleName() + " is already running!");
        }
        this.mContext = new WeakReference<>(context);
        this.mLoginViewModel = new ViewModelProvider(mContext.get()).get(LoginViewModel.class);
        this.loginData = loginData;
        this.captivePortal = captivePortal;
        this.network = network;
        this.silent = silent;
        this.mNotificationManager = NotificationManagerCompat.from(mContext.get());
        this.mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        try {
            this.lastUrl = new URL(URL_DETECT_PORTAL);
        } catch (MalformedURLException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    private HttpURLConnection createConnection(URL connectUrl) throws IOException {
        return createConnection(connectUrl, false);
    }

    private HttpURLConnection createConnection(URL connectUrl, boolean withRedirects) throws IOException {
        this.lastUrl = connectUrl;
        HttpURLConnection conn = (HttpURLConnection) connectUrl.openConnection();
        conn.setInstanceFollowRedirects(withRedirects);
        Log.d(TAG, "Created connection to " + connectUrl);
        conn.setRequestProperty("User-Agent", Util.USER_AGENT);
        conn.setUseCaches(false);

        if (withRedirects) {
            // Maybe needed
            conn.connect();

            // normally, 3xx is redirect
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                if (status == HttpURLConnection.HTTP_MOVED_TEMP
                        || status == HttpURLConnection.HTTP_MOVED_PERM
                        || status == HttpURLConnection.HTTP_SEE_OTHER) {
                    String location = conn.getHeaderField("Location");
                    if (location != null) {
                        URL newUrl = new URL(location);
                        if (!connectUrl.equals(newUrl)) {
                            Log.d(TAG, "Following \"" + newUrl + "\" redirect because of HTTP Code: " + status);
                            conn.disconnect();
                            return createConnection(newUrl, true);
                        }
                    }
                }
            }
        }
        return conn;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        taskRunning = true;
        Log.d(TAG, this.getClass().getSimpleName() + " (" + loginData.getSSID() + ") started!");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext.get(), MainActivity.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(mContext.get().getString(R.string.login_in_progress, loginData.getSSID()))
                .setContentText(mContext.get().getString(R.string.login_try))
                .setProgress(100, 0, true)
                .setOngoing(true);
        if (silent) {
            builder.setPriority(NotificationCompat.PRIORITY_MIN);
        }
        mNotificationManager.notify(notificationId, builder.build());
        LoginUtil.bindNetwork(mConnectivityManager, network);
    }

    @Override
    protected String doInBackground(String... strings) {
        int responseCode = -1;
        String response = null;
        try {
            LoginUtil.TrustAllCertificates.install();
            HttpURLConnection conn = createConnection(lastUrl, true);
            String responseS = Util.readResponse(TAG, conn);
            if (LoginUtil.checkCaptivePortal(responseS)) {
                Log.d(TAG, "CaptivePortal detected");
                String formParams = LoginUtil.getFormParams(loginData.getUsername(), loginData.getPassword(), lastUrl, requestMethod, responseS);
                conn = createConnection(lastUrl);
                conn.setRequestMethod(requestMethod);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(conn.getOutputStream()))) {
                    out.write(formParams.getBytes(StandardCharsets.UTF_8));
                }
                Util.readResponse(TAG, conn);
//                lastUrl = new URL(lastUrl, conn.getHeaderField("Location"));
                //Check if success
                conn = createConnection(new URL(URL_DETECT_PORTAL), true);
                responseCode = conn.getResponseCode();
                if (!LoginUtil.checkCaptivePortal(Util.readResponse(TAG, conn))) {
                    response = mContext.get().getString(R.string.logged_in_successfully);
                    LoginUtil.reportCaptivePortal(captivePortal);
                }
            } else {
                responseCode = conn.getResponseCode();
                if (!silent) {
                    response = mContext.get().getString(R.string.already_logged_in);
                }
                LoginUtil.reportCaptivePortal(captivePortal);
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Error while logging in", e);
            response = mContext.get().getString(R.string.failed_with_error, e.getMessage());
            failed = true;
        }
        if ((response == null || response.isEmpty())) {
            response = mContext.get().getString(R.string.failed_with_http_code, responseCode);
            failed = true;
        }
        Log.d(TAG, "HTTP Response Code: " + responseCode);
        return response;
    }

    @Override
    protected void onPostExecute(String response) {
        super.onPostExecute(response);
        mConnectivityManager.bindProcessToNetwork(null);

        loginData.setLastLogin(System.currentTimeMillis());
        mDisposable.add(mLoginViewModel.getDatabase().loginDao().update(loginData)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                        },
                        throwable -> {
                            Log.e(TAG, "Unable to update last-login data", throwable);
                            Toast.makeText(mContext.get(), mContext.get().getString(R.string.error_persisting_changes), Toast.LENGTH_SHORT).show();
                        }
                )
        );

        if (response != null && !response.isEmpty()) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext.get(), MainActivity.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setContentTitle("(" + loginData.getSSID() + ") " + mContext.get().getString(!failed ? R.string.login_successful : R.string.login_failed))
                    .setContentText(response)
                    .setAutoCancel(true)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(response));
            PendingIntent pendingIntent;
            if (failed) {
                Intent resultIntent = new Intent(mContext.get(), MainActivity.class)
                        .putExtra(FAILED_EXTRA, failed)
                        .putExtra(WIFI_DATA_EXTRA, loginData.getId())
                        .putExtra(RESPONSE_EXTRA, response)
                        .putExtra(WebViewActivity.URL_EXTRA, lastUrl.toString());
                pendingIntent = PendingIntent.getActivity(mContext.get(), 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            } else {
                pendingIntent = PendingIntent.getActivity(mContext.get(), 0, new Intent(), PendingIntent.FLAG_CANCEL_CURRENT);
            }
            builder.setContentIntent(pendingIntent);
            if (silent) {
                builder.setPriority(NotificationCompat.PRIORITY_MIN);
            }
            mNotificationManager.notify(notificationId, builder.build());
        }
        if (failed && !silent) {
            mContext.get().loginFailed(captivePortal, loginData, response, lastUrl.toString());
        }

        mDisposable.clear();

        taskRunning = false;
        Log.d(TAG, this.getClass().getSimpleName() + " (" + loginData.getSSID() + ") finished!");
    }

    public LoginTask setSilent() {
        silent = true;
        return this;
    }

}
