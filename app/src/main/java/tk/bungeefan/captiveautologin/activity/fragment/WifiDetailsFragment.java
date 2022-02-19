package tk.bungeefan.captiveautologin.activity.fragment;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.DateFormat;
import java.util.Date;

import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.data.entity.Login;

public class WifiDetailsFragment extends DialogFragment {

    public static String DATA_KEY = "loginData";

    public static WifiDetailsFragment newInstance(@Nullable Login login) {
        WifiDetailsFragment detailsFragment = new WifiDetailsFragment();
        Bundle bundle = new Bundle();
        bundle.putSerializable(DATA_KEY, login);
        detailsFragment.setArguments(bundle);
        return detailsFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Login login = getArguments() != null ? (Login) getArguments().getSerializable(DATA_KEY) : null;
        String ssid = null;
        String username = null;
        String password = null;
        Long lastLogin = null;
        if (login != null) {
            ssid = login.getSSID();
            username = login.getUsername();
            password = login.getPassword();
            lastLogin = login.getLastLogin();
        }
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.details))
                .setMessage(
                        "SSID: " + ssid + "\n"
                                + getString(R.string.username) + ": " + (username != null ? username : "-") + "\n"
                                + (password != null ? getString(R.string.password_saved) : getString(R.string.no_saved_password)) + "\n"
                                + getString(R.string.last_login) + ": " + (lastLogin != null ? DateFormat.getDateTimeInstance().format(new Date(lastLogin)) : "-")
                )
                .create();
    }
}
