package tk.bungeefan.captiveautologin.activity;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import tk.bungeefan.captiveautologin.activity.fragment.SettingsFragment;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    public static final String THEME_KEY = "pref_theme";

    public static void setTheme(SharedPreferences prefs) {
        int defaultValue = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        int themeSetting;
        try {
            themeSetting = Integer.parseInt(prefs.getString(THEME_KEY, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            themeSetting = defaultValue;
        }
        AppCompatDelegate.setDefaultNightMode(themeSetting);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

        prefs.registerOnSharedPreferenceChangeListener((prefs, key) -> {
            if (key.equals(THEME_KEY)) {
                setTheme(prefs);
            }
        });
    }
}