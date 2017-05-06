package net.nitroshare.android.ui.transfer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import net.nitroshare.android.R;
import net.nitroshare.android.ui.AboutActivity;
import net.nitroshare.android.ui.MainIntroActivity;
import net.nitroshare.android.ui.SettingsActivity;
import net.nitroshare.android.ui.explorer.ExplorerActivity;
import net.nitroshare.android.transfer.TransferService;
import net.nitroshare.android.util.Settings;

import java.lang.reflect.Method;

public class TransferActivity extends AppCompatActivity {

    private static final String TAG = "TransferActivity";
    private static final int INTRO_REQUEST = 1;

    private SharedPreferences mSharedPreferences;

    /**
     * Finish initializing the activity
     */
    private void finishInit() {
        Log.i(TAG, "finishing initialization of activity");

        // Launch the transfer service if it isn't already running
        TransferService.startStopService(this, mSharedPreferences.getBoolean(
                getString(R.string.setting_behavior_receive), true));

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

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        // Launch the intro if the user hasn't seen it yet
        boolean introShown = mSharedPreferences.getBoolean(
                getString(R.string.setting_intro_shown), false);

        if (!introShown) {
            Log.i(TAG, "intro has not been shown; launching activity");
            Intent introIntent = new Intent(this, MainIntroActivity.class);
            startActivityForResult(introIntent, INTRO_REQUEST);
        } else {
            finishInit();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(TransferActivity.this);

        boolean darkTheme = mSharedPreferences.getBoolean("dark-theme", false);

        int themeResId = 0;
        try {
            Class<?> willThisWork = ContextThemeWrapper.class;
            Method method = willThisWork.getMethod("getThemeResId");
            method.setAccessible(true);
            themeResId = (Integer) method.invoke(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(darkTheme) {
            if(themeResId != R.style.DarkTheme) {
                setTheme(R.style.DarkTheme);
                this.recreate();
            }
        } else {
            if(themeResId != R.style.AppTheme) {
                setTheme(R.style.AppTheme);
                this.recreate();
            }
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
                mSharedPreferences.edit().putBoolean(
                        getString(R.string.setting_intro_shown), true).apply();
                finishInit();
            } else {
                finish();
            }
        }
    }
}
