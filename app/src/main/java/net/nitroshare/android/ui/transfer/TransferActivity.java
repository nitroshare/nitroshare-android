package net.nitroshare.android.ui.transfer;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import net.nitroshare.android.R;
import net.nitroshare.android.ui.AboutActivity;
import net.nitroshare.android.ui.IntroActivity;
import net.nitroshare.android.ui.SettingsActivity;
import net.nitroshare.android.ui.explorer.ExplorerActivity;
import net.nitroshare.android.transfer.TransferService;
import net.nitroshare.android.util.Settings;

public class TransferActivity extends AppCompatActivity {

    private static final String TAG = "TransferActivity";
    private static final int INTRO_REQUEST = 1;

    private Settings mSettings;

    /**
     * Finish initializing the activity
     */
    private void finishInit() {
        Log.i(TAG, "finishing initialization of activity");

        // Launch the transfer service if it isn't already running
        TransferService.startStopService(this, mSettings.getBoolean(Settings.Key.BEHAVIOR_RECEIVE));

        TransferFragment mainFragment = new TransferFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.list_container, mainFragment)
                .commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(new Settings(this).getTheme());
        setContentView(R.layout.activity_main);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(TransferActivity.this, ExplorerActivity.class));
            }
        });

        mSettings = new Settings(this);

        // Launch the intro if the user hasn't seen it yet
        if (!mSettings.getBoolean(Settings.Key.INTRO_SHOWN)) {
            Log.i(TAG, "intro has not been shown; launching activity");
            Intent introIntent = new Intent(this, IntroActivity.class);
            startActivityForResult(introIntent, INTRO_REQUEST);
        } else {
            finishInit();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Determine if the dark theme is currently applied
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(R.attr.themeName, typedValue, true);
        boolean currentThemeDark = "dark".equals(typedValue.string);

        // Recreate the activity if the theme has changed
        boolean shouldShowDark = mSettings.getBoolean(Settings.Key.UI_DARK);
        if (currentThemeDark != shouldShowDark) {
            setTheme(shouldShowDark ? R.style.DarkTheme : R.style.AppTheme);
            recreate();
        }
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
            case R.id.action_about:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == INTRO_REQUEST) {
            if (resultCode == RESULT_OK) {
                Log.i(TAG, "intro finished");
                mSettings.putBoolean(Settings.Key.INTRO_SHOWN, true);
                finishInit();
            } else {
                finish();
            }
        }
    }
}
