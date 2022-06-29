package tk.bungeefan.captiveautologin;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.CaptivePortal;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import tk.bungeefan.captiveautologin.activity.MainActivity;
import tk.bungeefan.captiveautologin.data.entity.Login;
import tk.bungeefan.captiveautologin.work.RxLoginWorker;

public class Util {

    private static final String TAG = Util.class.getSimpleName();

    public static final String GITHUB = "https://github.com/Bungeefan/CaptiveAutoLogin";
    public static final String USER_AGENT = "Mozilla/5.0 (Android; CaptiveAutoLogin (" + GITHUB + "))";

    public static String replaceSSID(String ssid) {
        return ssid.replace("\"", "");
    }

    public static void checkForWifi(MainActivity ctx, List<Login> list, @Nullable WifiInfo wifiInfo, CaptivePortal captivePortal, Network network, boolean silent) {
        if (wifiInfo != null) {
            String ssid = replaceSSID(wifiInfo.getSSID());
            if (!isUnknownSSID(ssid)) {
                list.stream().filter(login -> login.getSSID().equals(ssid))
                        .forEach(login -> loginWifi(ctx, login, captivePortal, network, silent));
            }
        }
    }

    public static boolean isUnknownSSID(String ssid) {
        return ssid.equals(WifiManager.UNKNOWN_SSID);
    }

    public static void loginWifi(MainActivity ctx, Login login, CaptivePortal captivePortal, Network network, boolean silent) {
        var request = new OneTimeWorkRequest.Builder(RxLoginWorker.class)
                .setInputData(new Data.Builder()
                        .putLong(RxLoginWorker.LOGIN_DATA_ID, login.getId())
                        .putString(RxLoginWorker.LOGIN_DATA_SSID, login.getSSID())
                        .build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();
        WorkManager.getInstance(ctx).enqueueUniqueWork("loginCaptivePortal", ExistingWorkPolicy.KEEP, request);

        WorkManager.getInstance(ctx).getWorkInfoByIdLiveData(request.getId())
                .observe(ctx, workInfo -> {
                    if (workInfo != null) {
                        if (workInfo.getState().isFinished()) {
                            if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                                LoginUtil.reportCaptivePortal(captivePortal);
                            }

                            String response = workInfo.getOutputData().getString(RxLoginWorker.RESPONSE);

                            if (response != null && !response.isEmpty()) {
                                Snackbar.make(ctx.findViewById(R.id.content), response, Snackbar.LENGTH_SHORT).show();

//                                NotificationManagerCompat mNotificationManager = NotificationManagerCompat.from(ctx.getApplicationContext());
//                                NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx.getApplicationContext(), MainActivity.CHANNEL_ID)
//                                        .setSmallIcon(R.drawable.ic_stat_name)
//                                        .setContentTitle("(" + loginData.getSSID() + ") " + ctx.getString(workInfo.getState() == WorkInfo.State.SUCCEEDED ? R.string.login_successful : R.string.login_failed))
//                                        .setContentText(response)
//                                        .setAutoCancel(true)
//                                        .setStyle(new NotificationCompat.BigTextStyle().bigText(response));
//
//                                if (silent) {
//                                    builder.setPriority(NotificationCompat.PRIORITY_MIN);
//                                }
//
//                                mNotificationManager.notify(notificationId, builder.build());
                            }

                            if (workInfo.getState() == WorkInfo.State.FAILED && !silent) {
                                ctx.loginFailed(captivePortal, login, response, workInfo.getOutputData().getString(RxLoginWorker.LOGIN_URL));
                            }

                            if (workInfo.getState() == WorkInfo.State.CANCELLED) {
                                Snackbar.make(ctx.findViewById(R.id.content), ctx.getString(R.string.login_canceled, login.getSSID()), Snackbar.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    public static String readResponse(String TAG, HttpURLConnection conn) throws IOException {
        InputStream inputStream;
        try {
            inputStream = conn.getInputStream();
        } catch (FileNotFoundException e) {
            inputStream = conn.getErrorStream();
            Log.d(TAG, "Using ErrorStream due to exception", e);
        }
        String response = "";
        if (inputStream != null) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream))) {
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    stringBuilder.append(line);
                }
                response = stringBuilder.toString();
            }
        }
        Log.d(TAG, "Response of " + conn.getURL() + ": \"" + response + "\"");
        conn.disconnect();
        return response;
    }

    public static List<Login> readData(Context ctx, Uri uri) throws IOException {
        List<Login> dataList = new ArrayList<>();
        InputStream inputStream = uri == null ? ctx.openFileInput(MainActivity.FULL_FILE_NAME) : ctx.getContentResolver().openInputStream(uri);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream))) {
            while (in.ready()) {
                String[] split = in.readLine().split(";");
                Login login = new Login();
                if (split.length >= 1) {
                    login.setSSID(split[0]);
                    if (split.length >= 2) {
                        login.setUsername(split[1]);
                        if (split.length >= 3) {
                            if (uri != null) {
                                login.setPassword(split[2]);
                            }
                            if (split.length >= 4) {
                                login.setLastLogin(Long.parseLong(split[3]));
                            }
                        }
                    }
                }
                dataList.add(login);
            }
        }
        return dataList;
    }

    public static void writeData(Context ctx, List<Login> dataList, Uri uri) throws FileNotFoundException {
        OutputStream outputStream = uri == null ? ctx.openFileOutput(MainActivity.FULL_FILE_NAME, Context.MODE_PRIVATE) : ctx.getContentResolver().openOutputStream(uri);
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(outputStream))) {
            dataList.forEach(login -> out.println(login.toCSVString()));
        }
    }

    public static long getVersionCode(Context context) throws PackageManager.NameNotFoundException {
        return PackageInfoCompat.getLongVersionCode(context.getPackageManager().getPackageInfo(context.getPackageName(), 0));
    }

    public static Network getNetworkForCaptivePortal(ConnectivityManager cm) {
        Network[] info = cm.getAllNetworks();
        for (Network nw : info) {
            final NetworkCapabilities nc = cm.getNetworkCapabilities(nw);
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return nw;
            }
        }
        return null;
    }
}
