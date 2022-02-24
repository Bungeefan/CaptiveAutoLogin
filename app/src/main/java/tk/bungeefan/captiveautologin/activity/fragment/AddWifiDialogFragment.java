package tk.bungeefan.captiveautologin.activity.fragment;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.stream.Collectors;

import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.Util;
import tk.bungeefan.captiveautologin.data.entity.Login;

public class AddWifiDialogFragment extends DialogFragment {

    private static final String TAG = AddWifiDialogFragment.class.getSimpleName();
    public static final String REQUEST_KEY = "loginBundle";
    public static final String BUNDLE_KEY = "loginData";
    private ArrayAdapter<String> mWifiSpinnerAdapter;
    private WifiManager mWifiManager;
    private WifiManager.ScanResultsCallback scanResultCallback;

    public static AddWifiDialogFragment newInstance(@Nullable Login login) {
        AddWifiDialogFragment dialogFragment = new AddWifiDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(WifiDetailsFragment.DATA_KEY, login);
        dialogFragment.setArguments(args);
        return dialogFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mWifiSpinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item);
        mWifiManager = requireContext().getSystemService(WifiManager.class);

        loadNetworks();
    }

    private void loadNetworks() {
        List<String> configuredNetworks = null;

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            List<WifiConfiguration> wifiConfigurations = mWifiManager.getConfiguredNetworks();
            if (wifiConfigurations != null && !wifiConfigurations.isEmpty()) {
                configuredNetworks = wifiConfigurations.stream()
                        .map(wifiConfiguration -> wifiConfiguration.SSID)
                        .collect(Collectors.toList());
            }
        }

        if (configuredNetworks == null) {
            configuredNetworks = mWifiManager.getScanResults().stream()
                    .map(scanResult -> scanResult.SSID)
                    .collect(Collectors.toList());
        }

        if (!configuredNetworks.isEmpty()) {
            mWifiSpinnerAdapter.clear();
            mWifiSpinnerAdapter.addAll(configuredNetworks.stream()
                    .map(Util::replaceSSID)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList())
            );
        }
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

        Login login = getArguments() != null ? (Login) getArguments().getSerializable(WifiDetailsFragment.DATA_KEY) : null;
        if (login != null) {
            ssidField.setText(login.getSSID());
            usernameField.setText(login.getUsername());
            passwordField.setText(login.getPassword());
        } else if (!Util.isUnknownSSID(mWifiManager.getConnectionInfo().getSSID())) {
            String ssid = Util.replaceSSID(mWifiManager.getConnectionInfo().getSSID());
            ssidField.setText(ssid);
        }

        SwitchMaterial manualInputSwitch = view.findViewById(R.id.manualInputSwitch);
        manualInputSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && ssidField.getText().toString().isEmpty()) {
                String selectedItem = (String) wifiSpinner.getSelectedItem();
                if (selectedItem != null) {
                    ssidField.setText(selectedItem);
                }
            }
            wifiSpinner.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            input_layout_ssid.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        if (mWifiSpinnerAdapter.isEmpty()) {
            manualInputSwitch.setEnabled(false);
            manualInputSwitch.setChecked(true);
        } else if (login != null) {
            manualInputSwitch.setChecked(true);
        } else {
            int position = mWifiSpinnerAdapter.getPosition(ssidField.getText().toString());
            if (position != -1) {
                wifiSpinner.setSelection(position);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            scanResultCallback = new WifiManager.ScanResultsCallback() {

                @Override
                public void onScanResultsAvailable() {
                    String selectedItem = (String) wifiSpinner.getSelectedItem();
                    loadNetworks();

                    manualInputSwitch.setChecked(mWifiSpinnerAdapter.isEmpty());
                    manualInputSwitch.setEnabled(!mWifiSpinnerAdapter.isEmpty());

                    if (!mWifiSpinnerAdapter.isEmpty()) {
                        if (selectedItem != null) {
                            int position = mWifiSpinnerAdapter.getPosition(selectedItem);
                            if (position != -1) {
                                wifiSpinner.setSelection(position);
                                return;
                            }
                        }

                        String ssid = Util.replaceSSID(mWifiManager.getConnectionInfo().getSSID());
                        int position = mWifiSpinnerAdapter.getPosition(ssid);
                        if (position != -1) {
                            wifiSpinner.setSelection(position);
                        }
                    }
                }
            };
        }

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(login == null ? getString(R.string.add_login_title) : getString(R.string.edit_login_title))
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String username = ((EditText) view.findViewById(R.id.usernameField)).getText().toString().trim();
                    String password = ((EditText) view.findViewById(R.id.passwordField)).getText().toString().trim();
                    String wifiName = manualInputSwitch.isChecked() ? ((EditText) view.findViewById(R.id.ssidField)).getText().toString().trim() : (String) ((Spinner) view.findViewById(R.id.wifiSpinner)).getSelectedItem();

                    Login newLogin = login != null ? login : new Login();
                    newLogin.setSSID(wifiName);
                    newLogin.setUsername(!username.isEmpty() ? username : null);
                    newLogin.setPassword(!password.isEmpty() ? password : null);

                    Bundle result = new Bundle();
                    result.putSerializable(BUNDLE_KEY, newLogin);
                    getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (scanResultCallback != null) {
                    mWifiManager.registerScanResultsCallback(requireContext().getMainExecutor(), scanResultCallback);
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (scanResultCallback != null) {
                mWifiManager.unregisterScanResultsCallback(scanResultCallback);
            }
        }
    }
}
