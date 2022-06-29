package tk.bungeefan.captiveautologin.activity.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import tk.bungeefan.captiveautologin.MainApplication;
import tk.bungeefan.captiveautologin.R;
import tk.bungeefan.captiveautologin.activity.MainActivity;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final SharedPreferences.OnSharedPreferenceChangeListener CHANGE_LISTENER = (prefs, key) -> {
        if (key.equals(MainApplication.THEME_KEY)) {
            MainApplication.setTheme(prefs);
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

        Preference resetDismissedContent = findPreference("reset_dismissed_content");
        if (resetDismissedContent != null) {
            resetDismissedContent.setOnPreferenceClickListener(pref -> {
                getPreferenceManager().getSharedPreferences().edit().remove(MainActivity.PREF_PERMISSION_KEY).apply();
                Snackbar.make(requireView(), R.string.dismissed_content_reset, BaseTransientBottomBar.LENGTH_SHORT).show();
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
