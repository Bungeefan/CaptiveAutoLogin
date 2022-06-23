package tk.bungeefan.captiveautologin;

import android.net.CaptivePortal;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.UnsupportedEncodingException;
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

public class LoginUtil {

    private static final String TAG = LoginUtil.class.getSimpleName();

    private LoginUtil() {
    }

    @NonNull
    public static String getFormParams(String username, String password, URL lastUrl, String requestMethod, String html) throws UnsupportedEncodingException {
        Document doc = Jsoup.parse(html);

        Map<String, String> params = new HashMap<>();
        Elements forms = doc.getElementsByTag("form");
        for (Element e : forms) {
            if (e.hasAttr("action") && !e.attr("action").isEmpty()) {
                try {
                    lastUrl = new URL(lastUrl, e.attr("action"));
                    Log.d(TAG, "Changed url to (" + lastUrl + ") because of form action attribute!");
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
            result.append(URLEncoder.encode(param.getKey(), StandardCharsets.UTF_8.name()));
            result.append('=');
            result.append(URLEncoder.encode(param.getValue(), StandardCharsets.UTF_8.name()));
        }
        return result.toString();
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

    public static boolean checkCaptivePortal(String responseText) {
        return responseText != null && !responseText.equals("success");
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
