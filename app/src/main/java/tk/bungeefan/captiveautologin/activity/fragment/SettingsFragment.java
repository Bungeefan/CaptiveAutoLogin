package tk.bungeefan.captiveautologin.activity.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.activity.SettingsActivity;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final SharedPreferences.OnSharedPreferenceChangeListener CHANGE_LISTENER = (prefs, key) -> {
        if (key.equals(SettingsActivity.THEME_KEY)) {
            SettingsActivity.setTheme(prefs);
        }
    };

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.app_preferences, rootKey);

        ListPreference themePreference = findPreference("pref_theme");
        if (themePreference != null) {
            themePreference.setSummaryProvider((Preference.SummaryProvider<ListPreference>) pref -> pref.getEntry());
        }

        Preference notificationsLink = findPreference("notifications");
        if (notificationsLink != null) {
            notificationsLink.setOnPreferenceClickListener(pref -> {
                Intent intent = new Intent()
                        .setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(CHANGE_LISTENER);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(CHANGE_LISTENER);
    }

}
