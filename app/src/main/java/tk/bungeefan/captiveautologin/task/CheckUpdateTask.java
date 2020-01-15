package tk.bungeefan.captiveautologin.task;

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;

import androidx.core.content.pm.PackageInfoCompat;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.Util;
import tk.bungeefan.captiveautologin.activity.MainActivity;

public class CheckUpdateTask extends AsyncTask<Void, Long, Long> {

    private static final String TAG = CheckUpdateTask.class.getSimpleName();
    public static boolean taskRunning = false;
    private WeakReference<MainActivity> mContext;
    private boolean unnecessaryOutputDisabled;

    public CheckUpdateTask(MainActivity context, boolean unnecessaryOutputDisabled) {
        if (taskRunning) {
            throw new IllegalThreadStateException("Another " + this.getClass().getSimpleName() + " is already running!");
        }
        this.mContext = new WeakReference<>(context);
        this.unnecessaryOutputDisabled = unnecessaryOutputDisabled;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        taskRunning = true;
    }

    @Override
    protected Long doInBackground(Void... voids) {
        long newestVersion = -1;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(MainActivity.VERSION_URL).openConnection();
            String response = Util.readResponse(TAG, conn);
            newestVersion = Long.parseLong(response);
        } catch (NumberFormatException ignored) {
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return newestVersion;
    }

    @Override
    protected void onPostExecute(Long newestVersion) {
        super.onPostExecute(newestVersion);
        taskRunning = false;
        try {
            long currentVersion = PackageInfoCompat.getLongVersionCode(mContext.get().getPackageManager().getPackageInfo(mContext.get().getPackageName(), 0));
            if (newestVersion > currentVersion) {
                Snackbar snackbar = Snackbar.make(mContext.get().findViewById(R.id.content), String.format(mContext.get().getString(R.string.new_version_available), newestVersion), Snackbar.LENGTH_INDEFINITE);
                TextView snackBarTextView = snackbar.getView().findViewById(R.id.snackbar_text);
                TextView snackBarActionTextView = snackbar.getView().findViewById(R.id.snackbar_action);
                snackBarActionTextView.setTextSize(19);
                snackBarActionTextView.setTypeface(snackBarActionTextView.getTypeface(), Typeface.BOLD);
                snackBarTextView.setTypeface(snackBarTextView.getTypeface(), Typeface.BOLD);
                snackBarTextView.setTextColor(Color.YELLOW);
                snackbar.setAction(R.string.install, v -> new InstallUpdateTask(mContext.get()).execute()).show();
            } else {
                if (newestVersion == -1) {
                    String output = mContext.get().getString(R.string.update_check_failed);
                    Log.i(TAG, output);
                    Snackbar snackbar = Snackbar.make(mContext.get().findViewById(R.id.content), output, Snackbar.LENGTH_SHORT);
                    TextView snackBarTextView = snackbar.getView().findViewById(R.id.snackbar_text);
                    snackBarTextView.setTypeface(snackBarTextView.getTypeface(), Typeface.BOLD);
                    snackBarTextView.setTextColor(mContext.get().getColor(R.color.colorAccent));
                    snackbar.show();
                } else if (!unnecessaryOutputDisabled) {
                    String output = mContext.get().getString(R.string.version_up_to_date);
                    Log.i(TAG, output);
                    Snackbar snackbar = Snackbar.make(mContext.get().findViewById(R.id.content), output, Snackbar.LENGTH_SHORT);
                    TextView snackBarTextView = snackbar.getView().findViewById(R.id.snackbar_text);
                    snackBarTextView.setTypeface(snackBarTextView.getTypeface(), Typeface.BOLD);
                    snackBarTextView.setTextColor(mContext.get().getColor(R.color.snackBarGreen));
                    snackbar.show();
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }
}
