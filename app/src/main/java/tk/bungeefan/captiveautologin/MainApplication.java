package tk.bungeefan.captiveautologin;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.StrictMode;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.config.NotificationConfigurationBuilder;
import org.acra.data.StringFormat;

public class MainApplication extends Application {

    public static final String THEME_KEY = "pref_theme";
    private static final String REPORT_MAIL = "severin.hamader@yahoo.de";

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
    public void onCreate() {
        if (ACRA.isACRASenderServiceProcess()) {
            super.onCreate();
            return;
        }

        if (BuildConfig.DEBUG) {
            StrictMode.enableDefaults();
        }

        var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        setTheme(prefs);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }

        super.onCreate();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        ACRA.init(this, new CoreConfigurationBuilder()
                .withBuildConfigClass(BuildConfig.class)
                .withReportFormat(StringFormat.JSON)
                .withPluginConfigurations(
                        new MailSenderConfigurationBuilder()
                                .withMailTo(REPORT_MAIL)
                                .withReportAsFile(true)
                                .build(),
                        new NotificationConfigurationBuilder()
                                .withTitle(getString(R.string.notification_title_crash))
                                .withText(getString(R.string.notification_text_crash))
                                .withChannelName(getString(R.string.notification_channel_crash))
                                .withTickerText(getString(R.string.notification_text_crash))
                                .withResIcon(R.drawable.ic_stat_name)
                                .withSendButtonText(getString(android.R.string.ok))
                                .withDiscardButtonText(getString(android.R.string.cancel))
                                .withSendOnClick(true)
                                .build()
                )
        );
    }

}
