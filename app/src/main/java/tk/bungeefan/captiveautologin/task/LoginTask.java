package tk.bungeefan.captiveautologin.task;

import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.CaptivePortal;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.Util;
import tk.bungeefan.captiveautologin.activity.MainActivity;
import tk.bungeefan.captiveautologin.activity.WebViewActivity;
import tk.bungeefan.captiveautologin.data.WifiData;

public class LoginTask extends AsyncTask<String, String, String> {

    public static final String FAILED_EXTRA = "failed";
    public static final String WIFI_DATA_EXTRA = "WifiData";
    public static final String RESPONSE_EXTRA = "Response";
    private static final String URL_DETECT_PORTAL = "http://detectportal.firefox.com/success.txt";
    private static final String TAG = LoginTask.class.getSimpleName();
    public static boolean taskRunning = false;
    private final CaptivePortal captivePortal;
    private final Network network;
    private final WifiData wifiData;
    private NotificationManagerCompat mNotificationManager;
    private ConnectivityManager mConnectivityManager;
    private SharedPreferences prefs;
    private WeakReference<MainActivity> mContext;
    private URL lastUrl;
    private String requestMethod = "POST";
    private boolean failed = false;
    private boolean unnecessaryOutputDisabled;
    private int notificationId = 0;

    public LoginTask(MainActivity context, WifiData wifiData, CaptivePortal captivePortal, Network network, boolean unnecessaryOutputDisabled) {
        if (taskRunning) {
            throw new IllegalThreadStateException("Another " + this.getClass().getSimpleName() + " is already running!");
        }
        this.mContext = new WeakReference<>(context);
        this.wifiData = wifiData;
        this.captivePortal = captivePortal;
        this.network = network;
        this.unnecessaryOutputDisabled = unnecessaryOutputDisabled;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(mContext.get());
        this.mNotificationManager = NotificationManagerCompat.from(mContext.get());
        this.mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        try {
            this.lastUrl = new URL(URL_DETECT_PORTAL);
        } catch (MalformedURLException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    public static void bindNetwork(ConnectivityManager mConnectivityManager, @Nullable Network network) {
        if (mConnectivityManager != null) {
            if (network == null) {
                NetworkRequest.Builder request = new NetworkRequest.Builder();
//                request.addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL);
                request.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
                mConnectivityManager.requestNetwork(request.build(), new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network availableNetwork) {
                        try {
                            mConnectivityManager.bindProcessToNetwork(availableNetwork);
                        } catch (IllegalStateException e) {
                            Log.e(TAG, "ConnectivityManager.NetworkCallback.onAvailable: ", e);
                        }
                    }
                });
            } else {
                mConnectivityManager.bindProcessToNetwork(network);
            }
        }
    }

    public static void reportCaptivePortal(CaptivePortal captivePortal) {
        if (captivePortal != null) {
            captivePortal.reportCaptivePortalDismissed();
        }
    }

    private HttpURLConnection createConnection(URL connectUrl) throws IOException {
        return createConnection(connectUrl, false);
    }

    private HttpURLConnection createConnection(URL connectUrl, boolean withRedirects) throws IOException {
        this.lastUrl = connectUrl;
        HttpURLConnection conn = (HttpURLConnection) connectUrl.openConnection();
        Log.d(TAG, "Created connection to " + connectUrl);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android;CaptiveLogin)");
        conn.setUseCaches(false);
        if (withRedirects) {
            int responseCode = conn.getResponseCode();
            // Maybe needed
            conn.connect();
            String location = conn.getHeaderField("Location");
            if (location != null) {
                URL newUrl = new URL(location);
                if (!connectUrl.equals(newUrl)) {
                    Log.d(TAG, "Following \"" + newUrl + "\" redirect because of HTTP Code: " + responseCode);
                    return createConnection(newUrl, true);
                }
            }
        }
        return conn;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        taskRunning = true;
        Log.d(TAG, this.getClass().getSimpleName() + " (" + wifiData.getSSID() + ") started!");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext.get(), MainActivity.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(String.format(mContext.get().getString(R.string.login_in_progress), wifiData.getSSID()))
                .setContentText(mContext.get().getString(R.string.login_try))
                .setProgress(100, 0, true)
                .setOngoing(true);
        if (unnecessaryOutputDisabled) {
            builder.setPriority(NotificationCompat.PRIORITY_MIN);
        }
        mNotificationManager.notify(notificationId, builder.build());
        bindNetwork(mConnectivityManager, network);
    }

    @Override
    protected String doInBackground(String... strings) {
        int responseCode = -1;
        String response = null;
        try {
            TrustAllCertificates.install();
            HttpURLConnection conn = createConnection(lastUrl, true);
            String responseS = Util.readResponse(TAG, conn);
            if (checkCaptivePortal(responseS)) {
                Log.d(TAG, "CaptivePortal detected");
                String formParams = getFormParams(responseS);
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
                if (!checkCaptivePortal(Util.readResponse(TAG, conn))) {
                    response = mContext.get().getString(R.string.logged_in_successfully);
                    reportCaptivePortal(captivePortal);
                }
            } else {
                responseCode = conn.getResponseCode();
                if (!unnecessaryOutputDisabled) {
                    response = mContext.get().getString(R.string.already_logged_in);
                }
                reportCaptivePortal(captivePortal);
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
            response = mContext.get().getString(R.string.failed_with_error) + ": " + e.getMessage();
            failed = true;
        }
        if ((response == null || response.isEmpty())) {
            response = mContext.get().getString(R.string.failed_with_http_code) + ": \"" + responseCode + "\"";
            failed = true;
        }
        Log.d(TAG, "HTTP Response Code: " + responseCode);
        return response;
    }

    @Override
    protected void onPostExecute(String response) {
        super.onPostExecute(response);
        mConnectivityManager.bindProcessToNetwork(null);

        wifiData.setLastLogin(System.currentTimeMillis());
        //Sorting adapter
        mContext.get().mListViewAdapter.sort((o1, o2) -> Long.compare(o2.getLastLogin(), o1.getLastLogin()));

        if (response != null && !response.isEmpty()) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext.get(), MainActivity.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setContentTitle("(" + wifiData.getSSID() + ") " + mContext.get().getString(!failed ? R.string.login_successful : R.string.login_failed))
                    .setContentText(response)
                    .setAutoCancel(true)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(response));
            if (failed) {
                Intent resultIntent = new Intent(mContext.get(), MainActivity.class)
                        .putExtra(FAILED_EXTRA, failed)
                        .putExtra(WIFI_DATA_EXTRA, wifiData)
                        .putExtra(RESPONSE_EXTRA, response)
                        .putExtra(WebViewActivity.URL_EXTRA, lastUrl.toString());
                PendingIntent pendingIntent = TaskStackBuilder.create(mContext.get()).addNextIntentWithParentStack(resultIntent).getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
                builder.setContentIntent(pendingIntent);
            }
            if (unnecessaryOutputDisabled) {
                builder.setPriority(NotificationCompat.PRIORITY_MIN);
            }
            mNotificationManager.notify(notificationId, builder.build());
        }
        if (failed && !unnecessaryOutputDisabled) {
            mContext.get().loginFailed(captivePortal, wifiData, response, lastUrl.toString());
        }
        taskRunning = false;
        Log.d(TAG, this.getClass().getSimpleName() + " (" + wifiData.getSSID() + ") finished!");
    }

    public LoginTask disableUnnecessaryOutput() {
        unnecessaryOutputDisabled = true;
        return this;
    }

    private boolean checkCaptivePortal(String responseText) {
        return responseText != null && !responseText.equals("success");
    }

    @NonNull
    private String getFormParams(String html) throws UnsupportedEncodingException {
        String username = wifiData.getUsername();
        String password = wifiData.getPassword(prefs);

        Document doc = Jsoup.parse(html);

        Map<String, String> params = new HashMap<>();
        Elements forms = doc.getElementsByTag("form");
        for (Element e : forms) {
            if (e.hasAttr("action") && !e.attr("action").isEmpty()) {
                try {
                    lastUrl = new URL(lastUrl, e.attr("action"));
                    Log.d(TAG, "Changed url to (" + lastUrl + ") cause of form action attribute!");
                } catch (MalformedURLException e1) {
                    Log.e(TAG, Log.getStackTraceString(e1));
                }
            }
            if (e.hasAttr("method") && !e.attr("method").isEmpty()) {
                String method = e.attr("method").toUpperCase();
                if (!requestMethod.equals(method)) {
                    requestMethod = method;
                    Log.d(TAG, "Changed request method to " + requestMethod);
                }
            }
        }

        Elements inputElements = doc.getElementsByTag("input");
        for (Element inputElement : inputElements) {
            String name = inputElement.attr("name");
            if (!name.isEmpty()) {
                String type = inputElement.attr("type");
                String value = inputElement.val();

                //Checkboxes are only submitted when checked but here it always gets submitted
                if (!type.equalsIgnoreCase("hidden")) {
                    if (name.toLowerCase().contains("username") || name.toLowerCase().contains("auth_user") || name.toLowerCase().contains("uname") || type.equalsIgnoreCase("text") || type.equalsIgnoreCase("email")) {
                        value = username;
                    } else if (name.toLowerCase().contains("password") || name.toLowerCase().contains("psw") || type.equalsIgnoreCase("password")) {
                        value = password;
                    }
                }
                Log.d(TAG, "Added input: " + inputElement);
                params.put(name, value);
            }
        }

        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> param : params.entrySet()) {
            if (result.length() != 0) {
                result.append('&');
            }
            result.append(URLEncoder.encode(param.getKey(), "UTF-8"));
            result.append('=');
            result.append(URLEncoder.encode(param.getValue(), "UTF-8"));
        }
        return result.toString();
    }

    public static final class TrustAllCertificates implements X509TrustManager, HostnameVerifier {
        /**
         * Installs a new {@link TrustAllCertificates} as trust manager and hostname verifier.
         */
        public static void install() {
            try {
                TrustAllCertificates trustAll = new TrustAllCertificates();

                // Install the all-trusting trust manager
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null,
                        new TrustManager[]{trustAll},
                        new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

                // Install the all-trusting host verifier
                HttpsURLConnection.setDefaultHostnameVerifier(trustAll);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new RuntimeException("Failed setting up all thrusting certificate manager.", e);
            }
        }

        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }

        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

}
