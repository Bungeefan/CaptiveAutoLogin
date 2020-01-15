package tk.bungeefan.captiveautologin.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.CaptivePortal;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.task.LoginTask;

public class WebViewActivity extends AppCompatActivity {

    public static final String URL_EXTRA = "url";
    private static final String TAG = WebViewActivity.class.getSimpleName();
    private ConnectivityManager mConnectivityManager;
    private WebView webView;
    private String startUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        mConnectivityManager = getSystemService(ConnectivityManager.class);

        Intent intent = getIntent();
        startUrl = intent.getStringExtra(URL_EXTRA);
        CaptivePortal captivePortal = intent.getParcelableExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL);

        webView = findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                getSupportActionBar().setTitle(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                LoginTask.reportCaptivePortal(captivePortal);
            }
        });

        SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(() -> {
            webView.reload();
            swipeRefresh.setRefreshing(false);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        LoginTask.bindNetwork(mConnectivityManager, null);

        getSupportActionBar().setTitle("Loading...");
        webView.loadUrl(startUrl);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mConnectivityManager.bindProcessToNetwork(null);
    }

    @Override
    public void onBackPressed() {
        webView.goBack();
    }
}
