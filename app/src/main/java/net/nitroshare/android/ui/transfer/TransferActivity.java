package net.nitroshare.android.ui.transfer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import net.nitroshare.android.R;
import net.nitroshare.android.ui.AboutActivity;
import net.nitroshare.android.ui.IntroActivity;
import net.nitroshare.android.ui.settings.SettingsActivity;
import net.nitroshare.android.transfer.TransferService;
import net.nitroshare.android.ui.explorer.ExplorerActivity;
import net.nitroshare.android.util.Permissions;
import net.nitroshare.android.util.Settings;

public class TransferActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "TransferActivity";
    private static final int INTRO_REQUEST = 1;

    private Settings mSettings;

    /**
     * Finish initializing the activity
     */
    private void finishInit() {
        Log.i(TAG, "finishing initialization of activity");

        // Setup the action bar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Connect the action bar and navigation drawer
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar,
                R.string.activity_transfer_navigation_drawer_open,
                R.string.activity_transfer_navigation_drawer_close
        );
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        // Process items in the navigation drawer
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // TODO: update this every time the activity is shown

        // Set the label for the subtitle in the navigation drawer
        String deviceName = mSettings.getString(Settings.Key.DEVICE_NAME);
        ((TextView) navigationView.getHeaderView(0).findViewById(R.id.transfer_subtitle)).setText(
                getString(R.string.menu_transfer_subtitle, deviceName));

        // Setup the floating action button
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(TransferActivity.this, ExplorerActivity.class));
            }
        });

        // Launch the transfer service if it isn't already running
        TransferService.startStopService(this, mSettings.getBoolean(Settings.Key.BEHAVIOR_RECEIVE));

        // Add the transfer fragment
        TransferFragment mainFragment = new TransferFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.list_container, mainFragment)
                .commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSettings = new Settings(this);
        setTheme(mSettings.getTheme(
                R.style.LightTheme_NoActionBar,
                R.style.DarkTheme_NoActionBar
        ));
        setContentView(R.layout.activity_transfer);

        boolean introShown = mSettings.getBoolean(Settings.Key.INTRO_SHOWN);
        Log.i(TAG, introShown ? "intro has been shown" : "intro has not been shown");

        if (!introShown) {
            Log.i(TAG, "launching intro activity");
            Intent introIntent = new Intent(this, IntroActivity.class);
            startActivityForResult(introIntent, INTRO_REQUEST);
        } else if (!Permissions.haveStoragePermission(this)) {
            Permissions.requestStoragePermission(this);
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
            setTheme(shouldShowDark ? R.style.DarkTheme : R.style.LightTheme);
            recreate();
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_send:
                startActivity(new Intent(this, ExplorerActivity.class));
                break;
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.action_intro:
                startActivity(new Intent(this, IntroActivity.class));
                break;
            case R.id.action_report:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://github.com/nitroshare/nitroshare-android/issues/new")));
                break;
            case R.id.action_about:
                startActivity(new Intent(this, AboutActivity.class));
                break;
        }
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (Permissions.obtainedStoragePermission(requestCode, grantResults)) {
            finishInit();
        } else {
            finish();
        }
    }
}
