package tk.bungeefan.captiveautologin.activity;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import tk.bungeefan.captiveautologin.activity.fragment.SettingsFragment;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

        prefs.registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
            if (key.equals("pref_theme")) {
                int defaultValue = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                int themeSetting;
                try {
                    themeSetting = Integer.parseInt(sharedPreferences.getString(key, String.valueOf(defaultValue)));
                } catch (NumberFormatException e) {
                    themeSetting = defaultValue;
                }
                AppCompatDelegate.setDefaultNightMode(themeSetting);
            }
        });
    }
}