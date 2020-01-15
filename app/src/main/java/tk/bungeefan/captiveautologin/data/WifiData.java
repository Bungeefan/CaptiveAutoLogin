package tk.bungeefan.captiveautologin.data;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.io.Serializable;

public class WifiData implements Serializable {

    //    private static final String DATA_KEY = "WifiKeys";
    private String wifiName = "";
    private String username = "";
    private long lastLogin;

    public WifiData() {
    }

    public void setPassword(SharedPreferences prefs, String password) {
        if (!password.isEmpty()) {
            prefs.edit().putString(getDataKey(), password).apply();
        }
    }

    public String getPassword(SharedPreferences prefs) {
        return prefs.getString(getDataKey(), "");
//        Set<String> prefsStringSet = prefs.getStringSet(DATA_KEY, null);
//        String password;
//        if (prefsStringSet != null) {
//            password = prefsStringSet.stream().filter(s -> s.equals(getDataKey())).findAny().orElse("");
//        } else {
//            password = "";
//        }
//        return password;
    }

    public String getDataKey() {
        return this.getSSID() + ";" + this.getUsername();
    }

    public String getSSID() {
        return wifiName;
    }

    public void setWifiName(String wifiName) {
        this.wifiName = wifiName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public long getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(long lastLogin) {
        this.lastLogin = lastLogin;
    }

    @NonNull
    @Override
    public String toString() {
        return wifiName + " " + username;
    }

    public String toCSVString() {
        return toCSVString(null);
    }

    public String toCSVString(SharedPreferences prefs) {
        return wifiName + ";" + username + ";" + (prefs != null ? getPassword(prefs) : "") + ";" + lastLogin;
    }
}
