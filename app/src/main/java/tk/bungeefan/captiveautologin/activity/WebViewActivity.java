package tk.bungeefan.captiveautologin.activity;

import android.graphics.Bitmap;
import android.net.CaptivePortal;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.net.MalformedURLException;
import java.net.URL;

import tk.bungeefan.captiveautologin.LoginUtil;
import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.Util;

public class WebViewActivity extends AppCompatActivity {

    public static final String URL_EXTRA = "url";
    private static final String TAG = WebViewActivity.class.getSimpleName();
    private static final int NETWORK_REQUEST_TIMEOUT_MS = 5 * 1000;

    private ConnectivityManager mCm;
    private WebView webView;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private boolean mReload = false;
    private URL mUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mUrl = getUrl();
        if (mUrl == null) {
            finishAndRemoveTask();
            return;
        }

        setContentView(R.layout.activity_web_view);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mCm = getSystemService(ConnectivityManager.class);

        webView = findViewById(R.id.webView);
        webView.clearCache(true);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(false);

        SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(() -> webView.reload());

        CaptivePortal captivePortal = getIntent().getParcelableExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                getSupportActionBar().setSubtitle(url);
                swipeRefresh.setRefreshing(true);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                swipeRefresh.setRefreshing(false);
                LoginUtil.reportCaptivePortal(captivePortal);
            }
        });

        final Network network = Util.getNetworkForCaptivePortal(mCm);
        if (network == null) {
            getSupportActionBar().setSubtitle("Loading...");
            requestNetworkForCaptivePortal();
        } else {
            setNetwork(network);
            webView.loadUrl(mUrl.toString());
        }
    }

    private URL getUrl() {
        String url = getIntent().getStringExtra(URL_EXTRA);
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            Log.d(TAG, "Invalid captive portal URL " + url, e);
        }
        return null;
    }

    private void setNetwork(Network network) {
        if (network != null) {
            mCm.bindProcessToNetwork(network);
        }
    }

    private void requestNetworkForCaptivePortal() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.d(TAG, "Network available: " + network);
                setNetwork(network);
                runOnUiThread(() -> {
                    if (mReload) {
                        webView.reload();
                    } else {
                        webView.loadUrl(mUrl.toString());
                    }
                });
            }

            @Override
            public void onUnavailable() {
                Log.d(TAG, "Network unavailable");
                runOnUiThread(() -> {
                    // Instead of not loading anything in webview, simply load the page and return
                    // HTTP error page in the absence of network connection.
                    webView.loadUrl(mUrl.toString());
                });
            }

            @Override
            public void onLost(Network lostNetwork) {
                Log.d(TAG, "Network lost");
                mReload = true;
            }
        };
        Log.d(TAG, "request Network for captive portal");
        mCm.requestNetwork(request, mNetworkCallback, NETWORK_REQUEST_TIMEOUT_MS);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();

        if (mNetworkCallback != null) {
            try {
                mCm.unregisterNetworkCallback(mNetworkCallback);
                Log.d(TAG, "Unregistered network request");
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error while unregistering network request", e);
            }
        }

        super.onDestroy();
    }
}
