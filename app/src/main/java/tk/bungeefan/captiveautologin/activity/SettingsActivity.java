package tk.bungeefan.captiveautologin.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import tk.bungeefan.captiveautologin.activity.fragment.SettingsFragment;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new SettingsFragment())
                    .commit();
        }
    }
}