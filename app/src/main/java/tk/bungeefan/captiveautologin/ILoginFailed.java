package tk.bungeefan.captiveautologin;

import android.net.CaptivePortal;

import tk.bungeefan.captiveautologin.data.WifiData;

public interface ILoginFailed {
    void loginFailed(CaptivePortal captivePortal, WifiData wifiData, String response, String url);
}
