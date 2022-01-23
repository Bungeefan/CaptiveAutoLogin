package tk.bungeefan.captiveautologin.task;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.Util;
import tk.bungeefan.captiveautologin.activity.MainActivity;

public class UpdateAPKTask extends AsyncTask<Void, Void, Void> {

    private static final String TAG = UpdateAPKTask.class.getSimpleName();
    public static boolean taskRunning = false;
    private final WeakReference<MainActivity> mContext;
    private final DownloadManager mDownloadManager;
    private long downloadId;

    public UpdateAPKTask(MainActivity context) {
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
        try {
            Log.d(TAG, "Update available, downloading...");
            Snackbar.make(mContext.get().findViewById(R.id.content), mContext.get().getString(R.string.downloading_update), Snackbar.LENGTH_LONG).show();
            HttpURLConnection fileNameConn = (HttpURLConnection) new URL(MainActivity.FILENAME_URL).openConnection();

            String fileName = Util.readResponse(TAG, fileNameConn);
            if (!fileName.isEmpty()) {

                BroadcastReceiver onComplete = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context ctx, Intent intent) {
                        Snackbar.make(mContext.get().findViewById(R.id.content), mContext.get().getString(R.string.installing_new_update), Snackbar.LENGTH_LONG).show();
                        Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        installIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);

                        Log.d(TAG, "Update APK Location: " + mDownloadManager.getUriForDownloadedFile(downloadId).getPath());
                        installIntent.setDataAndType(mDownloadManager.getUriForDownloadedFile(downloadId), mDownloadManager.getMimeTypeForDownloadedFile(downloadId));
                        ctx.unregisterReceiver(this);

                        ctx.startActivity(installIntent);
                    }
                };
                mContext.get().registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(MainActivity.DOWNLOAD_URL + fileName + ".apk"))
                        .setTitle(mContext.get().getString(R.string.app_name))
                        .setDescription(mContext.get().getString(R.string.downloading_update))
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
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
