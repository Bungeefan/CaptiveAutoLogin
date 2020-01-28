package tk.bungeefan.captiveautologin.activity.fragment;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import tk.bungeefan.captiveautologin.R;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        // Load the Preferences from the XML file
        setPreferencesFromResource(R.xml.app_preferences, rootKey);

        Preference button = findPreference("notificationBtn");
        button.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
            } else {
                intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                intent.putExtra("app_package", getContext().getPackageName());
                intent.putExtra("app_uid", getContext().getApplicationInfo().uid);
            }
            startActivity(intent);
            return true;
        });
    }
}
