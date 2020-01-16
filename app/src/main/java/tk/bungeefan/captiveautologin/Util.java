package tk.bungeefan.captiveautologin;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.CaptivePortal;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.List;

import tk.bungeefan.captiveautologin.activity.MainActivity;
import tk.bungeefan.captiveautologin.data.WifiData;
import tk.bungeefan.captiveautologin.task.LoginTask;

public class Util {
    private static final String TAG = Util.class.getSimpleName();

    public static String replaceSSID(String ssid) {
        return ssid.replace("\"", "");
    }

    public static void checkForWifi(MainActivity ctx, List<WifiData> list, WifiManager wifiManager, CaptivePortal captivePortal, Network network, boolean unnecessaryOutputDisabled) {
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            String ssid = replaceSSID(wifiManager.getConnectionInfo().getSSID());
            if (!isUnknownSSID(ssid)) {
                list.stream().filter(wifiData -> wifiData.getSSID().equals(ssid))
                        .forEach(wifiData -> loginWifi(ctx, wifiData, captivePortal, network, unnecessaryOutputDisabled));
            }
        }
    }

    public static boolean isUnknownSSID(String ssid) {
        return ssid.equals("<unknown ssid>");
    }

    public static void loginWifi(MainActivity ctx, WifiData wifiData, CaptivePortal captivePortal, Network network, boolean unnecessaryOutputDisabled) {
        if (!LoginTask.taskRunning) {
            new LoginTask(ctx, wifiData, captivePortal, network, unnecessaryOutputDisabled).execute();
        } else {
            Log.d(TAG, "LoginTask already running!");
            if (!unnecessaryOutputDisabled) {
                Toast.makeText(ctx, ctx.getString(R.string.login_already_in_progress), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static String readResponse(String TAG, HttpURLConnection conn) throws IOException {
        InputStream inputStream;
        try {
            inputStream = conn.getInputStream();
        } catch (FileNotFoundException e) {
            inputStream = conn.getErrorStream();
            Log.d(TAG, "Using ErrorStream due to the exception");
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
        return response;
    }
}
