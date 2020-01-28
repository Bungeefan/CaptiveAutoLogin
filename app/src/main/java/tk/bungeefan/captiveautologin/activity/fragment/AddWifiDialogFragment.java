package tk.bungeefan.captiveautologin.activity.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.stream.Collectors;

import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.Util;
import tk.bungeefan.captiveautologin.activity.MainActivity;
import tk.bungeefan.captiveautologin.data.WifiData;

public class AddWifiDialogFragment extends DialogFragment {

    private static final String TAG = AddWifiDialogFragment.class.getSimpleName();
    private ArrayAdapter<String> mWifiSpinnerAdapter;
    private WifiManager mWifiManager;
    private SharedPreferences prefs;

    public static AddWifiDialogFragment newInstance(WifiData wifiData) {
        AddWifiDialogFragment frag = new AddWifiDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(WifiDetailsFragment.WIFI_DATA_KEY, wifiData);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        mWifiManager = getActivity().getSystemService(WifiManager.class);

        List<WifiConfiguration> wifiConfigurations = mWifiManager.getConfiguredNetworks();
        List<String> configuredNetworks = (wifiConfigurations != null && !wifiConfigurations.isEmpty() ?
                wifiConfigurations.stream()
                        .map(wifiConfiguration -> Util.replaceSSID(wifiConfiguration.SSID)) :
                mWifiManager.getScanResults().stream()
                        .map(scanResult -> Util.replaceSSID(scanResult.SSID))
                        .filter(s -> !s.isEmpty())
                        .distinct())
                .sorted()
                .collect(Collectors.toList());
        mWifiSpinnerAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_dropdown_item, configuredNetworks);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = requireActivity().getLayoutInflater().inflate(R.layout.add_wifi_dialog, null);

        EditText usernameField = view.findViewById(R.id.usernameField);
        EditText passwordField = view.findViewById(R.id.passwordField);
        TextInputLayout input_layout_ssid = view.findViewById(R.id.input_layout_ssid);
        EditText ssidField = view.findViewById(R.id.ssidField);
        Spinner wifiSpinner = view.findViewById(R.id.wifiSpinner);

        wifiSpinner.setAdapter(mWifiSpinnerAdapter);

        Switch manualInputSwitch = view.findViewById(R.id.manualInputSwitch);
        manualInputSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            wifiSpinner.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            input_layout_ssid.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        if (mWifiSpinnerAdapter.isEmpty() && Util.isUnknownSSID(mWifiManager.getConnectionInfo().getSSID())) {
            manualInputSwitch.setChecked(true);
            manualInputSwitch.setEnabled(false);
        }

        WifiData oldWifiData = getArguments() != null ? (WifiData) getArguments().getSerializable(WifiDetailsFragment.WIFI_DATA_KEY) : null;
        if (oldWifiData != null) {
            usernameField.setText(oldWifiData.getUsername());
            passwordField.setText(oldWifiData.getPassword(prefs));

            int position = mWifiSpinnerAdapter.getPosition(oldWifiData.getSSID());
            if (position == -1) {
                manualInputSwitch.setChecked(true);
            }

            if (!manualInputSwitch.isChecked()) {
                wifiSpinner.setSelection(position);
            } else {
                ssidField.setText(oldWifiData.getSSID());
            }
        } else if (!Util.isUnknownSSID(mWifiManager.getConnectionInfo().getSSID())) {
            String ssid = Util.replaceSSID(mWifiManager.getConnectionInfo().getSSID());

            int position = mWifiSpinnerAdapter.getPosition(ssid);
            if (position == -1) {
                manualInputSwitch.setChecked(true);
            }

            if (!manualInputSwitch.isChecked()) {
                wifiSpinner.setSelection(position);
            } else {
                ssidField.setText(ssid);
            }
        }
        MainActivity activity = (MainActivity) getActivity();
        return new AlertDialog.Builder(getActivity())
                .setTitle(oldWifiData == null ? getString(R.string.add_wifi_login) : getString(R.string.edit_wifi_login))
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String username = ((EditText) view.findViewById(R.id.usernameField)).getText().toString().trim();
                    String password = ((EditText) view.findViewById(R.id.passwordField)).getText().toString().trim();
                    String wifiName = manualInputSwitch.isChecked() ? ((EditText) view.findViewById(R.id.ssidField)).getText().toString().trim() : (String) ((Spinner) view.findViewById(R.id.wifiSpinner)).getSelectedItem();
                    WifiData wifiData;
                    if (oldWifiData == null) {
                        if (activity.wifiDataList.stream().noneMatch(wifiData1 -> wifiData1.getSSID().equals(wifiName))) {
                            wifiData = new WifiData();
                            activity.mListViewAdapter.add(wifiData);
                        } else {
                            new AlertDialog.Builder(getActivity())
                                    .setTitle(getString(R.string.error))
                                    .setMessage(String.format(getString(R.string.entry_already_exists), wifiName))
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                            return;
                        }
                    } else {
                        wifiData = oldWifiData;
                    }
                    wifiData.setWifiName(wifiName);
                    wifiData.setUsername(username);
                    wifiData.setPassword(prefs, password);
                    activity.mListViewAdapter.notifyDataSetChanged();

                    MainActivity mainActivity = (MainActivity) getActivity();
                    Util.writeData(getContext(), TAG, mainActivity.wifiDataList, null);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }
}
