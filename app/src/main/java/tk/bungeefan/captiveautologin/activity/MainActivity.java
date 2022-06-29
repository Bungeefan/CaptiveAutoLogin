package tk.bungeefan.captiveautologin.activity;

import android.Manifest;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.CaptivePortal;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.List;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.bungeefan.captiveautologin.ILoginFailed;
import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.Updater;
import tk.bungeefan.captiveautologin.Util;
import tk.bungeefan.captiveautologin.activity.fragment.AddWifiDialogFragment;
import tk.bungeefan.captiveautologin.activity.fragment.WifiDetailsFragment;
import tk.bungeefan.captiveautologin.data.LoginAdapter;
import tk.bungeefan.captiveautologin.data.LoginViewModel;
import tk.bungeefan.captiveautologin.data.entity.Login;

public class MainActivity extends AppCompatActivity implements ILoginFailed {

    public static final String UPDATE_URL = "https://bungeefan.ddns.net/captiveautologin/";
    public static final String VERSION_URL = UPDATE_URL + "currentVersion.txt";
    public static final String FILENAME_URL = UPDATE_URL + "appName.txt";
    public static final String DOWNLOAD_URL = UPDATE_URL + "download/";
    public static final String CHANNEL_ID = "default_login";
    public static final String PREF_PERMISSION_KEY = "LOCATION_PERMISSION_EXPLANATION";

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String FILE_NAME = "wifi_data";
    public static final String FULL_FILE_NAME = FILE_NAME + ".csv";
    private static final int RQ_ACCESS_FINE_LOCATION = 1234;
    private static final int RQ_ACCESS_FINE_LOCATION_DIALOG = 1235;
    private static final String MIME_TYPE_CSV = "text/csv";

    private final CompositeDisposable mDisposable = new CompositeDisposable();
    public LoginAdapter mListViewAdapter;
    private LoginViewModel mLoginViewModel;
    private RecyclerView recyclerView;
    private SearchView searchView;
    private SwipeRefreshLayout swipeRefresh;

    private SharedPreferences prefs;
    private WifiManager mWifiManager;
    private NotificationManager mNotificationManager;
    private ConnectivityManager mConnectivityManager;

    private CaptivePortal captivePortal;
    private String captivePortalUrl;
    private Network network;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String> importLauncher;
    private ConnectivityManager.NetworkCallback callback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initErrorHandler();

        mLoginViewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        mWifiManager = getSystemService(WifiManager.class);
        mNotificationManager = getSystemService(NotificationManager.class);
        mConnectivityManager = getSystemService(ConnectivityManager.class);

        Intent intent = getIntent();
        this.captivePortal = intent.getParcelableExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL);
        this.captivePortalUrl = intent.getStringExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL_URL);
        this.network = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK);
        if (captivePortal == null && savedInstanceState != null) {
            this.captivePortal = savedInstanceState.getParcelable(ConnectivityManager.EXTRA_CAPTIVE_PORTAL);
            this.captivePortal = savedInstanceState.getParcelable(ConnectivityManager.EXTRA_CAPTIVE_PORTAL_URL);
            this.network = savedInstanceState.getParcelable(ConnectivityManager.EXTRA_NETWORK);
        }

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> showInputDialog());

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(() -> {
            if (!mListViewAdapter.getCurrentList().isEmpty()) {
                if (checkAndRequestLocationPermission(RQ_ACCESS_FINE_LOCATION)) {
                    checkForWifi(mWifiManager.getConnectionInfo());
                }
            }
            swipeRefresh.setRefreshing(false);
        });

        recyclerView = findViewById(R.id.networkListView);
        recyclerView.setAdapter(mListViewAdapter = new LoginAdapter(loginData -> {
            if (loginData != null) {
                Consumer<Void> loginWifi = (unused) -> Util.loginWifi(this, loginData, captivePortal, network, false);

                String ssid = Util.replaceSSID(mWifiManager.getConnectionInfo().getSSID());
                int title = -1;
                String message = null;

                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    if (!ssid.equals(loginData.getSSID())) {
                        if (!Util.isUnknownSSID(ssid)) {
                            if (prefs.getBoolean("pref_network_mismatch_warning", true)) {
                                title = R.string.other_network_detected;
                                message = getString(R.string.another_network, ssid, loginData.getSSID());
                            }
                        } else {
                            title = R.string.no_network_detected;
                            message = getString(R.string.not_connected_to_network);
                        }
                    }
                }

                if (title != -1) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(title))
                            .setMessage(message)
                            .setNegativeButton(getString(R.string.login_anyway), (dialog, which) -> loginWifi.accept(null))
                            .setPositiveButton(android.R.string.cancel, null)
                            .show();
                    return;
                }

                loginWifi.accept(null);
            }
        }));

        mLoginViewModel.getAllLogins().observe(this, list -> mListViewAdapter.submitList(list));
        registerForContextMenu(recyclerView);

        createNotificationChannel();

        if (prefs.getBoolean("pref_auto_update_check", true)) {
            checkUpdate(true);
        }

        if (prefs.getBoolean("pref_auto_login_on_connect", true)) {
            callback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    WifiInfo wifiInfo;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        wifiInfo = (WifiInfo) mConnectivityManager.getNetworkCapabilities(network).getTransportInfo();
                    } else {
                        wifiInfo = mWifiManager.getConnectionInfo();
                    }
                    checkForWifi(wifiInfo, true);
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                    WifiInfo wifiInfo;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        wifiInfo = (WifiInfo) networkCapabilities.getTransportInfo();
                    } else {
                        wifiInfo = mWifiManager.getConnectionInfo();
                    }
                    checkForWifi(wifiInfo, true);
                }
            };
        }

        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument() {
            @NonNull
            @Override
            public Intent createIntent(@NonNull Context context, @NonNull String input) {
                return super.createIntent(context, input).setType(MIME_TYPE_CSV);
            }
        }, this::exportData);
        importLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::importData);

        getSupportFragmentManager().setFragmentResultListener(AddWifiDialogFragment.REQUEST_KEY, this, (requestKey, bundle) -> {
            Login newLogin = (Login) bundle.getSerializable(AddWifiDialogFragment.BUNDLE_KEY);
            if (newLogin == null) return;

            if (newLogin.getSSID().isEmpty()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.error_title)
                        .setMessage(R.string.empty_wifi_name)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            }

            if (newLogin.getId() == null) {
                mDisposable.add(mLoginViewModel.getDatabase().loginDao().findBySSID(newLogin.getSSID())
                        .subscribeOn(Schedulers.io())
                        .subscribe(logins -> {
                            if (logins.isEmpty()) {
                                mDisposable.add(
                                        mLoginViewModel.getDatabase().loginDao().insertAll(newLogin)
                                                .subscribeOn(Schedulers.io())
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .subscribe(() -> {
                                                        },
                                                        throwable -> {
                                                            Log.e(TAG, "Unable to save login data", throwable);
                                                            Snackbar.make(findViewById(R.id.content), getString(R.string.error_persisting_changes), Snackbar.LENGTH_SHORT).show();
                                                        })
                                );
                            } else {
                                runOnUiThread(() ->
                                        new MaterialAlertDialogBuilder(this)
                                                .setTitle(getString(R.string.error_title))
                                                .setMessage(getString(R.string.entry_already_exists, newLogin.getSSID()))
                                                .setPositiveButton(android.R.string.ok, null)
                                                .show()
                                );
                            }
                        }, throwable -> {
                            Log.e(TAG, "Unable to check login data", throwable);
                            Snackbar.make(findViewById(R.id.content), getString(R.string.error_persisting_changes), Snackbar.LENGTH_SHORT).show();
                        })
                );
            } else {
                mDisposable.add(mLoginViewModel.getDatabase().loginDao().update(newLogin)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                                },
                                throwable -> {
                                    Log.e(TAG, "Unable to update login data", throwable);
                                    Snackbar.make(findViewById(R.id.content), getString(R.string.error_persisting_changes), Snackbar.LENGTH_SHORT).show();
                                }
                        )
                );
            }
        });
    }

    private void initErrorHandler() {
        RxJavaPlugins.setErrorHandler(e -> {
            if (e instanceof UndeliverableException) {
                e = e.getCause();
            }
            if ((e instanceof IOException) || (e instanceof SocketException)) {
                // fine, irrelevant network problem or API that throws on cancellation
                return;
            }
            if (e instanceof InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return;
            }
            if ((e instanceof NullPointerException) || (e instanceof IllegalArgumentException)) {
                // that's likely a bug in the application
                Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), e);
                return;
            }
            if (e instanceof IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), e);
                return;
            }
            Log.w("Undeliverable exception received, not sure what to do", e);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (callback != null) {
                NetworkRequest.Builder builder = new NetworkRequest.Builder();
                builder.addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL);
                mConnectivityManager.registerNetworkCallback(builder.build(), callback);
                Log.d(TAG, "Registered network callback");
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (callback != null) {
                try {
                    mConnectivityManager.unregisterNetworkCallback(callback);
                    Log.d(TAG, "Unregistered network callback");
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Error while unregistering network callback", e);
                }
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // clear all the subscriptions
        mDisposable.clear();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable(ConnectivityManager.EXTRA_CAPTIVE_PORTAL, captivePortal);
        savedInstanceState.putString(ConnectivityManager.EXTRA_CAPTIVE_PORTAL_URL, captivePortalUrl);
        savedInstanceState.putParcelable(ConnectivityManager.EXTRA_NETWORK, network);
    }

    private void checkUpdate(boolean silent) {
        mDisposable.add(Observable.fromCallable(() -> Updater.getLatestVersion(MainActivity.VERSION_URL))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(latestVersion -> {
                    if (latestVersion != -1) {
                        try {
                            long currentVersion = Util.getVersionCode(this);

                            if (latestVersion > currentVersion) {
                                Log.d(TAG, "Update available, latest: " + latestVersion + ", current: " + currentVersion);
                                Snackbar snackbar = Snackbar.make(findViewById(R.id.content),
                                        getString(R.string.new_version_available, latestVersion), 10000);
                                TextView snackBarActionTextView = snackbar.getView().findViewById(R.id.snackbar_action);
                                snackBarActionTextView.setTextSize(16);
                                snackbar.setAction(R.string.new_version_action, v -> {
                                    Log.d(TAG, "Downloading update (" + latestVersion + ")");
                                    Snackbar.make(findViewById(R.id.content), getString(R.string.downloading_update), Snackbar.LENGTH_LONG).show();

                                    mDisposable.add(Observable.fromCallable(() -> Updater.getUpdateFileName(MainActivity.FILENAME_URL))
                                            .subscribeOn(Schedulers.io())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe(fileName -> downloadUpdate(MainActivity.DOWNLOAD_URL + fileName + ".apk"),
                                                    throwable -> {
                                                        Log.e(TAG, "Error while downloading update file name");
                                                        Snackbar.make(findViewById(R.id.content), getString(R.string.update_failed), Snackbar.LENGTH_SHORT).show();
                                                    })
                                    );
                                }).show();
                            } else if (!silent) {
                                Snackbar.make(findViewById(R.id.content), getString(R.string.version_up_to_date), Snackbar.LENGTH_SHORT).show();
                            }
                            return;
                        } catch (PackageManager.NameNotFoundException e) {
                            Log.e(TAG, "Failed to retrieve version info", e);
                        }
                    }

                    Snackbar.make(findViewById(R.id.content), getString(R.string.update_check_failed), Snackbar.LENGTH_SHORT).show();
                }, throwable -> {
                    Log.e(TAG, "Update Check failed", throwable);
                    Snackbar.make(findViewById(R.id.content), getString(R.string.update_check_failed), Snackbar.LENGTH_SHORT).show();
                })
        );
    }

    private void downloadUpdate(String uri) {
        var mDownloadManager = getSystemService(DownloadManager.class);

        BroadcastReceiver onComplete = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                long downloadId = intent.getExtras().getLong(DownloadManager.EXTRA_DOWNLOAD_ID);

                ctx.unregisterReceiver(this);
                try (var fileDescriptor = mDownloadManager.openDownloadedFile(downloadId);
                     var in = new BufferedInputStream(new FileInputStream(fileDescriptor.getFileDescriptor()))) {
                    Snackbar.make(findViewById(R.id.content), ctx.getString(R.string.installing_new_update), Snackbar.LENGTH_SHORT).show();

                    Log.d(TAG, "Installing update");
                    Updater.installPackage(ctx, ctx.getPackageName(), in, fileDescriptor.getStatSize());
//                    Updater.launchInstaller(ctx, mDownloadManager.getUriForDownloadedFile(downloadId), mDownloadManager.getMimeTypeForDownloadedFile(downloadId));
                } catch (IOException e) {
                    Log.e(TAG, "Failed to install update", e);
                    Snackbar.make(findViewById(R.id.content), ctx.getString(R.string.update_failed), Snackbar.LENGTH_SHORT).show();
                }
            }
        };
        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(uri))
                .setTitle(getString(R.string.app_name))
                .setDescription(getString(R.string.downloading_update))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        mDownloadManager.enqueue(request);
    }

    private void createNotificationChannel() {
        mNotificationManager.deleteNotificationChannel("default"); //TODO Remove next version
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.login_notification_channel), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(getString(R.string.login_notification_channel_description));
        channel.setShowBadge(true);
        mNotificationManager.createNotificationChannel(channel);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (requestCode == RQ_ACCESS_FINE_LOCATION) {
                    checkForWifi(mWifiManager.getConnectionInfo());
                } else if (requestCode == RQ_ACCESS_FINE_LOCATION_DIALOG) {
                    showInputDialog(null);
                }
            } else {
                var builder = new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.permission_denied))
                        .setMessage(getString(R.string.permission_denied_description))
                        .setPositiveButton(android.R.string.ok, null);

                Intent settingsIntent = new Intent()
                        .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:" + getPackageName()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (settingsIntent.resolveActivity(getPackageManager()) != null) {
                    builder.setNeutralButton(getString(R.string.open_permission_settings), (dialog, which) -> startActivity(settingsIntent));
                }

                builder.show();
            }
        }
    }

    public void checkForWifi(WifiInfo wifiInfo) {
        checkForWifi(wifiInfo, false);
    }

    public void checkForWifi(WifiInfo wifiInfo, boolean silent) {
        Util.checkForWifi(this, mListViewAdapter.getCurrentList(), wifiInfo, captivePortal, network, silent);
    }

    private boolean checkAndRequestLocationPermission(int requestCode) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            boolean hasShownPermissionExplanation = prefs.getBoolean(PREF_PERMISSION_KEY, false);
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) || !hasShownPermissionExplanation) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.rational_location_permission_title))
                        .setMessage(getString(R.string.rational_location_permission_description))
                        .setPositiveButton(getString(R.string.grant_permission), (dialog, which) -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, requestCode))
                        .show();
                if (!hasShownPermissionExplanation) {
                    prefs.edit().putBoolean(PREF_PERMISSION_KEY, true).apply();
                }
            } else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, requestCode);
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.option_items, menu);
        searchView = (SearchView) menu.findItem(R.id.app_bar_search).getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchDatabase(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchDatabase(newText);
                return false;
            }

            private void searchDatabase(String query) {
                mDisposable.add(
                        mLoginViewModel.getDatabase().loginDao().findByName(query)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribeOn(Schedulers.io())
                                .subscribe(logins -> mListViewAdapter.submitList(logins), throwable -> {
                                    Log.e(TAG, "Unable to query database", throwable);
                                    Snackbar.make(findViewById(R.id.content), getString(R.string.unknown_error), Snackbar.LENGTH_SHORT).show();
                                })
                );
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    private void showInputDialog() {
        if (!prefs.getBoolean(PREF_PERMISSION_KEY, false)) {
            if (!checkAndRequestLocationPermission(RQ_ACCESS_FINE_LOCATION_DIALOG)) {
                return;
            }
        }
        showInputDialog(null);
    }

    private void showInputDialog(Login login) {
        AddWifiDialogFragment addWifiDialogFragment = AddWifiDialogFragment.newInstance(login);
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
                importLauncher.launch("text/*");
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
            Util.writeData(MainActivity.this, mListViewAdapter.getCurrentList(), uri);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.success)
                    .setMessage(getString(R.string.data_export_success, name))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (IOException e) {
            Log.e(TAG, "Failed to export data", e);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.error_title)
                    .setMessage(getString(R.string.data_export_failed, name))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void importData(Uri uri) {
        if (uri == null) return;

        String name = DocumentFile.fromSingleUri(this, uri).getName();
        try {
            List<Login> importedDataList = Util.readData(this, uri);
            if (!importedDataList.isEmpty()) {
                mDisposable.add(mLoginViewModel.getDatabase().loginDao().insertAll(importedDataList)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.io())
                        .subscribe(() -> new MaterialAlertDialogBuilder(this)
                                        .setTitle(R.string.success)
                                        .setMessage(R.string.data_import_success)
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show(),
                                throwable -> {
                                    Log.e(TAG, "Failed to save imported logins", throwable);
                                    new MaterialAlertDialogBuilder(this)
                                            .setTitle(R.string.error_title)
                                            .setMessage(getString(R.string.data_import_failed, name))
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show();
                                }
                        )
                );
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.error_title)
                        .setMessage(R.string.no_data_found)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to import data", e);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.error_title)
                    .setMessage(getString(R.string.data_import_failed, name))
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
        Login loginData = mListViewAdapter.getSelectedItem();
        switch (item.getItemId()) {
            case R.id.context_edit:
                showInputDialog(loginData);
                break;
            case R.id.context_delete:
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.warning)
                        .setMessage(getString(R.string.removing_wifi, loginData.getSSID()))
                        .setPositiveButton(android.R.string.ok, (dialog, which) ->
                                mDisposable.add(mLoginViewModel.getDatabase().loginDao().delete(loginData)
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe(() -> {
                                            Snackbar.make(findViewById(R.id.content), getString(R.string.removed_wifi, loginData.getSSID()), Snackbar.LENGTH_SHORT).show();
                                        }, throwable -> {
                                            Log.e(TAG, "Failed to delete login data", throwable);
                                            Snackbar.make(findViewById(R.id.content), getString(R.string.error_persisting_changes), Snackbar.LENGTH_SHORT).show();
                                        })
                                )
                        )
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                break;
            case R.id.context_details:
                WifiDetailsFragment wifiDetailsFragment = WifiDetailsFragment.newInstance(loginData);
                wifiDetailsFragment.show(getSupportFragmentManager(), TAG);
                break;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void loginFailed(CaptivePortal captivePortal, Login loginData, String response, @Nullable String url) {
        var builder = new MaterialAlertDialogBuilder(this)
                .setTitle(loginData.getSSID() + " - " + getString(R.string.error_title))
                .setMessage((response != null ? response + "\n" : "") + getString(R.string.try_manual_login))
                .setNeutralButton(android.R.string.cancel, null);

        if (url != null || captivePortalUrl != null) {
            builder.setPositiveButton(getString(R.string.login_manually), (dialog, which) -> {
                try {
                    try {
                        Intent intent = new Intent(ConnectivityManager.ACTION_CAPTIVE_PORTAL_SIGN_IN)
                                .setPackage("com.android.captiveportallogin")
                                .putExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL, captivePortal)
                                .putExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL_URL, captivePortalUrl)
                                .putExtra(ConnectivityManager.EXTRA_NETWORK, network);
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Log.w(TAG, "Unable to launch system app for captive portals", e);
                        Intent browserIntent = new Intent(this, WebViewActivity.class);
                        browserIntent.putExtra(WebViewActivity.URL_EXTRA, url != null ? url : captivePortalUrl);
                        browserIntent.putExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL, captivePortal);
                        startActivity(browserIntent);
                    }
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "Unable to launch web-view activity", e);
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.error_title))
                            .setMessage(getString(R.string.no_url_activity, url))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            });
        }

        builder.show();
    }
}
