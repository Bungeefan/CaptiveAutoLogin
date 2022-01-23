package tk.bungeefan.captiveautologin.activity.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import java.text.DateFormat;
import java.util.Date;

import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.data.WifiData;

public class WifiDetailsFragment extends DialogFragment {

    public static String WIFI_DATA_KEY = "wifiData";
    private SharedPreferences prefs;

    public static WifiDetailsFragment newInstance(WifiData wifiData) {
        WifiDetailsFragment frag = new WifiDetailsFragment();
        Bundle args = new Bundle();
        args.putSerializable(WIFI_DATA_KEY, wifiData);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        WifiData wifiData = getArguments() != null ? (WifiData) getArguments().getSerializable(WIFI_DATA_KEY) : null;
        String ssid = "";
        String wifiUsername = "";
        String wifiPassword = "";
        long lastLogin = 0;
        if (wifiData != null) {
            ssid = wifiData.getSSID();
            wifiUsername = wifiData.getUsername();
            wifiPassword = wifiData.getPassword(prefs);
            lastLogin = wifiData.getLastLogin();
        }
        return new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.details))
                .setMessage(
                        "SSID: " + ssid + "\n"
                                + getString(R.string.username) + ": " + (!wifiUsername.isEmpty() ? wifiUsername : "-") + "\n"
                                + (!wifiPassword.isEmpty() ? getString(R.string.password_saved) : getString(R.string.no_saved_password)) + "\n"
                                + getString(R.string.last_login) + ": " + (lastLogin != 0 ? DateFormat.getDateTimeInstance().format(new Date(lastLogin)) : "-")
                )
                .create();
    }
}
