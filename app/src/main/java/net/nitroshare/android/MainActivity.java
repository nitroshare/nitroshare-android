package net.nitroshare.android;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int INTRO_REQUEST = 1;

    private SharedPreferences mSharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        // Launch the intro if the user hasn't seen it yet
        boolean introShown = mSharedPreferences.getBoolean(
                getString(R.string.setting_intro_shown), false);

        if (!introShown) {
            Log.d(TAG, "intro has not been shown; launching activity");

            Intent introIntent = new Intent(this, MainIntroActivity.class);
            startActivityForResult(introIntent, INTRO_REQUEST);
        }

        WebView webView = (WebView) findViewById(R.id.webview);
        webView.loadUrl("file:///android_asset/readme.html");

        // Start the discovery service
        Intent startIntent = new Intent(this, DiscoveryService.class);
        startIntent.setAction(DiscoveryService.ACTION_START);
        startService(startIntent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == INTRO_REQUEST) {
            if (resultCode == RESULT_OK) {
                // Remember that the intro has been seen
                mSharedPreferences.edit().putBoolean(
                        getString(R.string.setting_intro_shown), true).apply();
            }
            else {
                // Intro wasn't finished, so close this activity
                finish();
            }
        }
    }
}
