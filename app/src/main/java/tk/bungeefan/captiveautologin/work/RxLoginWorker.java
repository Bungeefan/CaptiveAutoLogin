package tk.bungeefan.captiveautologin.work;

import android.app.Notification;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import androidx.work.rxjava3.RxWorker;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import io.reactivex.rxjava3.core.Single;
import tk.bungeefan.captiveautologin.LoginUtil;
import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.Util;
import tk.bungeefan.captiveautologin.activity.MainActivity;
import tk.bungeefan.captiveautologin.data.AppDatabase;
import tk.bungeefan.captiveautologin.data.entity.Login;

public class RxLoginWorker extends RxWorker {

    public static final String LOGIN_DATA_ID = "loginDataId";
    public static final String LOGIN_DATA_SSID = "loginDataSSID";
    public static final String LOGIN_URL = "loginUrl";
    public static final String RESPONSE = "response";
    public static final String HTTP_RESPONSE_CODE = "httpResponseCode";
    private static final String TAG = RxLoginWorker.class.getSimpleName();
    private static final String URL_DETECT_PORTAL = "http://detectportal.firefox.com/success.txt";
    private static final int SOCKET_TIMEOUT_MS = 10 * 1000;
    private final AppDatabase database;
    private final ConnectivityManager mCm;
    private final String requestMethod = "POST";
    private final int notificationId = 0;
    private Network network;
    private boolean failed = false;

    public RxLoginWorker(Context context, WorkerParameters params) {
        super(context, params);
        this.database = AppDatabase.getInstance(context);
        this.mCm = context.getSystemService(ConnectivityManager.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            this.network = getNetwork();
        }
        if (this.network != null) {
            Log.d(TAG, "Received network for worker: " + this.network);
        } else {
            this.network = Util.getNetworkForCaptivePortal(mCm);
        }
    }

    private void cancelForegroundInfo() {
        NotificationManagerCompat.from(getApplicationContext()).cancel(notificationId);
    }

    @NonNull
    @Override
    public ListenableFuture<ForegroundInfo> getForegroundInfoAsync() {
        return Futures.immediateFuture(createForegroundInfo());
    }

    @NonNull
    private ForegroundInfo createForegroundInfo() {
        Context context = getApplicationContext();
        String id = MainActivity.CHANNEL_ID;
        String title = context.getString(R.string.login_in_progress, getInputData().getString(LOGIN_DATA_SSID));
        String text = getApplicationContext().getString(R.string.login_try);
        String cancel = getApplicationContext().getString(android.R.string.cancel);

        Notification notification = new NotificationCompat.Builder(context, id)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setProgress(100, 0, true)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_delete, cancel, WorkManager.getInstance(context)
                        .createCancelPendingIntent(getId()))
                .build();

        return new ForegroundInfo(notificationId, notification);
    }

    private HttpURLConnection createConnection(URL connectUrl, boolean withRedirects) throws IOException {
        Log.d(TAG, "Using network in createConnection: " + (network != null));

        HttpURLConnection conn = (HttpURLConnection) (network != null ? network.openConnection(connectUrl) : connectUrl.openConnection());
//        TrafficStats.setThreadStatsTag((int) Thread.currentThread().getId());

        conn.setConnectTimeout(SOCKET_TIMEOUT_MS);
        conn.setReadTimeout(SOCKET_TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", Util.USER_AGENT);
        conn.setUseCaches(false);
        Log.d(TAG, "Created connection to " + connectUrl);

        if (withRedirects) {
            HttpURLConnection newUrl = followRedirects(conn);
            if (newUrl != null) return newUrl;
        }

        return conn;
    }

    @Nullable
    private HttpURLConnection followRedirects(HttpURLConnection conn) throws IOException {
        // normally, 3xx is redirect
        int status = conn.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_SEE_OTHER) {
                String location = conn.getHeaderField("Location");
                if (location != null) {
                    URL newUrl = new URL(location);
                    if (!conn.getURL().equals(newUrl)) {
                        Log.d(TAG, "Following \"" + newUrl + "\" redirect because of HTTP Code: " + status);
                        conn.disconnect();
                        return createConnection(newUrl, true);
                    }
                }
            }
        }
        return null;
    }

    @NonNull
    @Override
    public Single<Result> createWork() {
        long loginId = getInputData().getLong(LOGIN_DATA_ID, -1);

        return database.loginDao().findById(loginId)
                .map(loginData -> {
                    final Data.Builder data = new Data.Builder();

                    onPreExecute(loginData);

                    int responseCode = -1;
                    String response = null;
                    HttpURLConnection conn = null;
                    try {
                        LoginUtil.TrustAllCertificates.install();

                        conn = createConnection(new URL(URL_DETECT_PORTAL), true);
                        String responseS = Util.readResponse(TAG, conn);
                        if (LoginUtil.checkCaptivePortal(responseS)) {
                            Log.d(TAG, "CaptivePortal detected");

                            String formParams = LoginUtil.getFormParams(loginData.getUsername(), loginData.getPassword(), conn.getURL(), requestMethod, responseS);
                            conn = createConnection(conn.getURL(), false);
                            conn.setRequestMethod(requestMethod);
                            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(conn.getOutputStream()))) {
                                out.write(formParams.getBytes(StandardCharsets.UTF_8));
                            }
                            //Ignore form response but read it
                            Util.readResponse(TAG, conn);

                            //Check success
                            conn = createConnection(new URL(URL_DETECT_PORTAL), false);
                            if (!LoginUtil.checkCaptivePortal(Util.readResponse(TAG, conn))) {
                                response = getApplicationContext().getString(R.string.logged_in_successfully);
                            }
                            responseCode = conn.getResponseCode();
                        } else {
                            responseCode = conn.getResponseCode();
                            Log.d(TAG, "HTTP Response Code: " + responseCode);
                            response = getApplicationContext().getString(R.string.already_connected);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error while logging in", e);
                        response = getApplicationContext().getString(R.string.failed_with_error, e.getMessage());
                        failed = true;
                    } finally {
                        if (conn != null) conn.disconnect();
                    }

                    if (response == null || response.isEmpty()) {
                        response = getApplicationContext().getString(R.string.failed_with_http_code, responseCode);
                        failed = true;
                    }

                    data.putString(RESPONSE, response);
                    if (conn != null) {
                        data.putString(LOGIN_URL, conn.getURL().toString());
                    }
                    data.putInt(HTTP_RESPONSE_CODE, responseCode);

                    onPostExecute(loginData);

                    if (failed) {
                        return Result.failure(data.build());
                    } else {
                        loginData.setLastLogin(System.currentTimeMillis());
                        database.loginDao().update(loginData).subscribe(() -> {
                                },
                                throwable -> {
                                    Log.e(TAG, "Unable to update last-login data", throwable);
                                    Toast.makeText(getApplicationContext(), getApplicationContext().getString(R.string.error_persisting_changes), Toast.LENGTH_SHORT).show();
                                }
                        );

                        return Result.success(data.build());
                    }
                });
    }

    private void onPreExecute(Login loginData) {
        Log.d(TAG, this.getClass().getSimpleName() + " (" + loginData.getSSID() + ") started");
    }

    private void onPostExecute(Login loginData) {
        cancelForegroundInfo();
        Log.d(TAG, this.getClass().getSimpleName() + " (" + loginData.getSSID() + ") finished");
    }

    @Override
    public void onStopped() {
        super.onStopped();
        cancelForegroundInfo();
    }

}
