package tk.bungeefan.captiveautologin;

import android.app.Application;
import android.os.Build;

import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;

import tk.bungeefan.captiveautologin.activity.SettingsActivity;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SettingsActivity.setTheme(prefs);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }

        super.onCreate();
    }

}