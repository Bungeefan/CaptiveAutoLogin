package tk.bungeefan.captiveautologin.activity.fragment;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import tk.bungeefan.captiveautologin.R;

public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        // Load the Preferences from the XML file
        setPreferencesFromResource(R.xml.app_preferences, rootKey);
    }
}
