package tk.bungeefan.captiveautologin;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Updater {

    private static final String TAG = Updater.class.getSimpleName();
    private static final String ACTION_INSTALL_CALLBACK = "tk.bungeefan.captiveautologin.INSTALL_CALLBACK";

    public static long getLatestVersion(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        String response = Util.readResponse(TAG, conn);
        return Long.parseLong(response);
    }

    @NonNull
    public static String getUpdateFileName(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        String fileName = Util.readResponse(TAG, conn);
        if (fileName.isEmpty()) {
            throw new IllegalArgumentException("Received no update filename");
        }
        return fileName;
    }

    public static void installPackage(Context ctx, String packageName, InputStream in, long size) throws IOException {
        var params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        params.setAppPackageName(packageName);
        int sessionId = ctx.getPackageManager().getPackageInstaller().createSession(params);
        try (var session = ctx.getPackageManager().getPackageInstaller().openSession(sessionId)) {
            try (var out = session.openWrite("Apk", 0, size)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) != -1) {
                    out.write(buffer, 0, length);
                }
                session.fsync(out);
            }

            BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    String action = intent.getAction();

                    if (ACTION_INSTALL_CALLBACK.equals(action)) {
                        int result = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                        String statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                        String blockingPackage = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME);

                        String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
                        Log.d(TAG, "PackageInstallerCallback: result = " + result + ", packageName = " + packageName);
                        switch (result) {
                            case PackageInstaller.STATUS_SUCCESS:
                                Log.d(TAG, "Package " + packageName + " installation complete");
                                Toast.makeText(ctx, "Installation complete!", Toast.LENGTH_SHORT).show();
                                ctx.unregisterReceiver(this);
                                break;
                            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                                Log.d(TAG, "Init User action");
                                ctx.startActivity(intent.getParcelableExtra(Intent.EXTRA_INTENT));
                                break;
                            case PackageInstaller.STATUS_FAILURE_ABORTED:
                                Toast.makeText(ctx, ctx.getString(R.string.update_canceled), Toast.LENGTH_SHORT).show();
                                ctx.unregisterReceiver(this);
                                break;
                            default:
                                Log.e(TAG, "Installation failed." + (statusMessage != null ? " " + statusMessage : ""));
                                if (blockingPackage != null) {
                                    Log.e(TAG, "Blocking package: " + blockingPackage);
                                }
                                Toast.makeText(ctx, ctx.getString(R.string.update_failed) + (statusMessage != null ? "\n" + statusMessage : ""), Toast.LENGTH_LONG).show();
                                ctx.unregisterReceiver(this);
                        }
                    }
                }
            };

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_INSTALL_CALLBACK);
            ctx.registerReceiver(mIntentReceiver, intentFilter);

            session.commit(createIntentSender(ctx, sessionId));
        }
    }

    private static IntentSender createIntentSender(Context context, int sessionId) {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                new Intent(ACTION_INSTALL_CALLBACK),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
        return pendingIntent.getIntentSender();
    }

    public static void launchInstaller(Context context, Uri uri, String mimeType) {
        Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        installIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
        installIntent.setDataAndType(uri, mimeType);

        context.startActivity(installIntent);
    }
}
