package tk.bungeefan.captiveautologin.activity;

import static android.net.ConnectivityManager.ACTION_CAPTIVE_PORTAL_SIGN_IN;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.CaptivePortal;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SearchView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import tk.bungeefan.captiveautologin.ILoginFailed;
import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.Util;
import tk.bungeefan.captiveautologin.activity.fragment.AddWifiDialogFragment;
import tk.bungeefan.captiveautologin.activity.fragment.WifiDetailsFragment;
import tk.bungeefan.captiveautologin.data.WifiData;
import tk.bungeefan.captiveautologin.data.WifiDataAdapter;
import tk.bungeefan.captiveautologin.task.CheckUpdateTask;
import tk.bungeefan.captiveautologin.task.LoginTask;


public class MainActivity extends AppCompatActivity implements ILoginFailed {

    public static final String UPDATE_URL = "https://bungeefan.ddns.net/captiveautologin/";
    public static final String VERSION_URL = UPDATE_URL + "currentVersion.txt";
    public static final String FILENAME_URL = UPDATE_URL + "appName.txt";
    public static final String DOWNLOAD_URL = UPDATE_URL + "download/";
    public static final String CHANNEL_ID = "default_login";

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String FILE_NAME = "wifi_data";
    public static final String FULL_FILE_NAME = FILE_NAME + ".csv";
    private static final int RQ_ACCESS_FINE_LOCATION = 1234;
    private static final int RQ_ACCESS_FINE_LOCATION_DIALOG = 1235;
    private static final String MIME_TYPE_CSV = "text/csv";
    public List<WifiData> wifiDataList = new ArrayList<>();
    public WifiDataAdapter<WifiData> mListViewAdapter;
    private ListView listView;
    private SearchView searchView;
    private SwipeRefreshLayout swipeRefresh;

    private SharedPreferences prefs;
    private WifiManager mWifiManager;
    private NotificationManager mNotificationManager;
    private ConnectivityManager mConnectivityManager;

    private CaptivePortal captivePortal;
    private Network network;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String> importLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SettingsActivity.setTheme(prefs);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWifiManager = getSystemService(WifiManager.class);
        mNotificationManager = getSystemService(NotificationManager.class);
        mConnectivityManager = getSystemService(ConnectivityManager.class);

        Intent intent = getIntent();
        this.captivePortal = intent.getParcelableExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL);
        this.network = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK);
        if (captivePortal == null && savedInstanceState != null) {
            this.captivePortal = savedInstanceState.getParcelable(ConnectivityManager.EXTRA_CAPTIVE_PORTAL);
            this.network = savedInstanceState.getParcelable(ConnectivityManager.EXTRA_NETWORK);
        }

        if (intent.getBooleanExtra(LoginTask.FAILED_EXTRA, false)) {
            loginFailed(intent.getParcelableExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL), (WifiData) intent.getSerializableExtra(LoginTask.WIFI_DATA_EXTRA), intent.getStringExtra(LoginTask.RESPONSE_EXTRA), intent.getStringExtra(WebViewActivity.URL_EXTRA));
        }


        FloatingActionButton fab = findViewById(R.id.fab);
        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(R.attr.fabColor, typedValue, true)) {
            fab.setBackgroundTintList(ColorStateList.valueOf(getColor(typedValue.resourceId)));
            fab.setFocusable(true);
        }
        fab.setOnClickListener(v -> showInputDialog());


        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(() -> {
            checkForWifi();
            swipeRefresh.setRefreshing(false);
        });


        listView = findViewById(R.id.networkListView);
        listView.setAdapter(mListViewAdapter = new WifiDataAdapter<>(this, R.layout.list_item, wifiDataList));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            WifiData wifiData = mListViewAdapter.getItem(position);
            if (wifiData != null) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, RQ_ACCESS_FINE_LOCATION);
                } else {
                    String ssid = Util.replaceSSID(mWifiManager.getConnectionInfo().getSSID());
                    if (!ssid.equals(wifiData.getSSID())) {
                        DialogInterface.OnClickListener continueClick = (dialog, which) ->
                                Util.loginWifi(this, wifiData, captivePortal, network, false);
                        int title;
                        String message;
                        if (!Util.isUnknownSSID(ssid)) {
                            title = R.string.other_network_detected;
                            message = String.format(getString(R.string.another_network), ssid, wifiData.getSSID());
                        } else {
                            title = R.string.no_wifi_network_detected;
                            message = getString(R.string.not_connected_to_network);
                        }
                        new AlertDialog.Builder(this)
                                .setTitle(getString(title))
                                .setMessage(message)
                                .setNegativeButton(getString(R.string.login_although), continueClick)
                                .setPositiveButton(android.R.string.cancel, null)
                                .show();
                    } else {
                        Util.loginWifi(this, wifiData, captivePortal, network, false);
                    }
                }
            }
        });
        registerForContextMenu(listView);

        try {
            wifiDataList.addAll(Util.readData(this, TAG, null));
            mListViewAdapter.notifyDataSetChanged();
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }

        createNotificationChannel();

        checkUpdate(true);

        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL);
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.d(TAG, "Network available, checking for wifi...");
                checkForWifi(true);
            }
        };
        mConnectivityManager.registerNetworkCallback(builder.build(), callback);

        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument() {
            @NonNull
            @Override
            public Intent createIntent(@NonNull Context context, @NonNull String input) {
                return super.createIntent(context, input).setType(MIME_TYPE_CSV);
            }
        }, this::exportData);
        importLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::importData);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable(ConnectivityManager.EXTRA_CAPTIVE_PORTAL, captivePortal);
        savedInstanceState.putParcelable(ConnectivityManager.EXTRA_NETWORK, network);
    }

    private void checkUpdate(boolean unnecessaryOutputDisabled) {
        if (!CheckUpdateTask.taskRunning) {
            new CheckUpdateTask(this, unnecessaryOutputDisabled).execute();
        } else {
            Log.d(TAG, CheckUpdateTask.class.getSimpleName() + " already running!");
        }
    }

    private void createNotificationChannel() {
        mNotificationManager.deleteNotificationChannel("default"); //TODO Remove next version
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.login_notification_channel), NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(getString(R.string.login_notification_channel_description));
        channel.setShowBadge(true);
        mNotificationManager.createNotificationChannel(channel);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.permission_denied))
                        .setMessage(getString(R.string.permission_denied_description, String.join(", ", permissions)))
                        .setPositiveButton(getString(R.string.i_understand), null)
                        .show();
            } else {
                if (requestCode == RQ_ACCESS_FINE_LOCATION) {
                    checkForWifi();
                } else if (requestCode == RQ_ACCESS_FINE_LOCATION_DIALOG) {
                    showInputDialog();
                }
            }
        }
    }

    public void checkForWifi() {
        checkForWifi(false);
    }

    public void checkForWifi(boolean unnecessaryOutputDisabled) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (!unnecessaryOutputDisabled) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, RQ_ACCESS_FINE_LOCATION);
            }
        } else {
            Util.checkForWifi(this, wifiDataList, mWifiManager, captivePortal, network, unnecessaryOutputDisabled);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.option_items, menu);
        searchView = (SearchView) menu.findItem(R.id.app_bar_search).getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                mListViewAdapter.getFilter().filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                mListViewAdapter.getFilter().filter(newText);
                return false;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    private void showInputDialog() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, RQ_ACCESS_FINE_LOCATION_DIALOG);
        } else {
            showInputDialog(null);
        }
    }

    private void showInputDialog(WifiData oldWifiData) {
        AddWifiDialogFragment addWifiDialogFragment = AddWifiDialogFragment.newInstance(oldWifiData);
        addWifiDialogFragment.show(getSupportFragmentManager(), TAG);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.option_update:
                checkUpdate(false);
                break;
            case R.id.option_export:
                exportLauncher.launch(FILE_NAME);
                break;
            case R.id.option_import:
                importLauncher.launch("test/*");
                break;
            case R.id.option_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void exportData(Uri uri) {
        if (uri == null) return;

        String name = DocumentFile.fromSingleUri(this, uri).getName();
        Log.d(TAG, "Selected export uri: " + uri);
        try {
            Util.writeData(MainActivity.this, wifiDataList, uri);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.success)
                    .setMessage(String.format(getString(R.string.data_export_success), name))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            new AlertDialog.Builder(this)
                    .setTitle(R.string.error_title)
                    .setMessage(String.format(getString(R.string.data_export_failed), name))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void importData(Uri uri) {
        if (uri == null) return;

        String name = DocumentFile.fromSingleUri(this, uri).getName();
        try {
            List<WifiData> importedWifiDataList = Util.readData(this, TAG, uri);
            if (!importedWifiDataList.isEmpty()) {

                mListViewAdapter.addAll(importedWifiDataList);
                Util.writeData(MainActivity.this, this.wifiDataList, null);
                new AlertDialog.Builder(this)
                        .setTitle(R.string.success)
                        .setMessage(R.string.data_import_success)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.error_title)
                        .setMessage(R.string.no_data_found)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            new AlertDialog.Builder(this)
                    .setTitle(R.string.error_title)
                    .setMessage(String.format(getString(R.string.data_import_failed), name))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        getMenuInflater().inflate(R.menu.context_items, menu);
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo contextMenuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        WifiData wifiData = mListViewAdapter.getItem(contextMenuInfo.position);
        switch (item.getItemId()) {
            case R.id.context_edit:
                showInputDialog(wifiData);
                break;
            case R.id.context_delete:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.warning)
                        .setMessage(String.format(getString(R.string.removing_wifi), wifiData.getSSID()))
                        .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                            mListViewAdapter.remove(wifiData);
                            try {
                                Util.writeData(this, wifiDataList, null);
                            } catch (IOException e) {
                                Log.e(TAG, Log.getStackTraceString(e));
                                new AlertDialog.Builder(this)
                                        .setTitle(R.string.error_title)
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                break;
            case R.id.context_details:
                WifiDetailsFragment wifiDetailsFragment = WifiDetailsFragment.newInstance(wifiData);
                wifiDetailsFragment.show(getSupportFragmentManager(), TAG);
                break;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void loginFailed(CaptivePortal captivePortal, WifiData wifiData, String response, String url) {
        new AlertDialog.Builder(this)
                .setTitle(wifiData.getSSID() + " - " + getString(R.string.error)).setMessage(response + " " + getString(R.string.try_manual_login))
                .setPositiveButton(getString(R.string.login_manually), (dialog, which) -> {
                    try {
                        try {
                            Intent intent = new Intent(ACTION_CAPTIVE_PORTAL_SIGN_IN)
                                    .setPackage("com.android.captiveportallogin")
                                    .putExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL, captivePortal)
                                    .putExtra(ConnectivityManager.EXTRA_NETWORK, network);
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            Log.e(TAG, Log.getStackTraceString(e));
                            Intent browserIntent = new Intent(this, WebViewActivity.class);
                            browserIntent.putExtra(WebViewActivity.URL_EXTRA, url);
                            browserIntent.putExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL, captivePortal);
                            startActivity(browserIntent);
                        }
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, Log.getStackTraceString(e));
                        new AlertDialog.Builder(this)
                                .setTitle(getString(R.string.error))
                                .setMessage(String.format(getString(R.string.no_url_activity), url))
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                })
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }
}
