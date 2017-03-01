package net.nitroshare.android;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

public class MainActivity extends Activity {

    public static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        // Launch the intro if the user hasn't seen it yet
        boolean introShown = sharedPreferences.getBoolean(
                getString(R.string.setting_first_start), false);

        if (!introShown) {
            Log.d(TAG, "intro has not been shown; launching activity");

            Intent introIntent = new Intent(this, MainIntroActivity.class);
            startActivity(introIntent);

            // Remember that the intro has been seen
            sharedPreferences.edit().putBoolean(
                    getString(R.string.setting_first_start), true).apply();
        }

        // Start the discovery service
        Intent startIntent = new Intent(this, DiscoveryService.class);
        startIntent.setAction(DiscoveryService.ACTION_START);
        startService(startIntent);
    }
}
