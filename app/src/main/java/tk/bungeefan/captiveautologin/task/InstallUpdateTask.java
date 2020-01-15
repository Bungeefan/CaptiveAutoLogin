package tk.bungeefan.captiveautologin.task;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.Util;
import tk.bungeefan.captiveautologin.activity.MainActivity;

public class InstallUpdateTask extends AsyncTask<Void, Void, Void> {

    public static final String LOCAL_UPDATE_NAME = "AppUpdate.apk";
    private static final String TAG = InstallUpdateTask.class.getSimpleName();
    public static boolean taskRunning = false;
    private WeakReference<MainActivity> mContext;
    private DownloadManager mDownloadManager;
    private long downloadId;

    public InstallUpdateTask(MainActivity context) {
        if (taskRunning) {
            throw new IllegalThreadStateException("Another " + this.getClass().getSimpleName() + " is already running!");
        }
        this.mContext = new WeakReference<>(context);
        this.mDownloadManager = mContext.get().getSystemService(DownloadManager.class);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        taskRunning = true;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        final String destination = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/";
        try {
            Log.d(TAG, "Update available, downloading...");
            Snackbar.make(mContext.get().findViewById(R.id.content), mContext.get().getString(R.string.downloading_update), Snackbar.LENGTH_LONG).show();
            HttpURLConnection downloadConn = (HttpURLConnection) new URL(MainActivity.FILENAME_URL).openConnection();

            String fileName = Util.readResponse(TAG, downloadConn);
            if (!fileName.isEmpty()) {

                final File localDownloadFile = new File(destination + LOCAL_UPDATE_NAME);
                if (localDownloadFile.exists()) {
                    Log.d(TAG, "old update file deleted: " + localDownloadFile.delete());
                }
                final Uri localDownloadFileUri = Uri.fromFile(localDownloadFile);

                BroadcastReceiver onComplete = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context ctx, Intent intent) {
                        Snackbar.make(mContext.get().findViewById(R.id.content), mContext.get().getString(R.string.installing_new_update), Snackbar.LENGTH_LONG).show();
                        Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);

/*                        Uri apkURI = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ?
                                FileProvider.getUriForFile(ctx, BuildConfig.APPLICATION_ID + ".provider", localDownloadFile)
                                : localDownloadFileUri;*/

                        install.setDataAndType(mDownloadManager.getUriForDownloadedFile(downloadId), mDownloadManager.getMimeTypeForDownloadedFile(downloadId));
                        ctx.unregisterReceiver(this);

                        ctx.startActivity(install);
                        mContext.get().finish();
                    }
                };
                mContext.get().registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(MainActivity.UPDATE_URL + "download/" + fileName + ".apk"))
                        .setTitle(mContext.get().getString(R.string.app_name))
                        .setDescription(mContext.get().getString(R.string.downloading_update))
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                        .setDestinationUri(localDownloadFileUri);
                downloadId = mDownloadManager.enqueue(request);
            } else {
                Log.w(TAG, "Received no update filename");
            }
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        taskRunning = false;
    }
}
