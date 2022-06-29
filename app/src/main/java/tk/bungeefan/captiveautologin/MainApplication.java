package tk.bungeefan.captiveautologin;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.StrictMode;

import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.config.NotificationConfigurationBuilder;
import org.acra.data.StringFormat;

import tk.bungeefan.captiveautologin.activity.SettingsActivity;

public class MainApplication extends Application {

    private static final String REPORT_MAIL = "severin.hamader@yahoo.de";

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
        SettingsActivity.setTheme(prefs);

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
