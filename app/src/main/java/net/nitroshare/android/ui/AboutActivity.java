package net.nitroshare.android.ui;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import net.nitroshare.android.R;
import net.nitroshare.android.util.Settings;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Show basic application information
 */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(new Settings(this).getTheme());
        setContentView(R.layout.activity_about);

        PackageInfo packageInfo = null;
        try {
            packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        if (packageInfo != null) {
            Date lastUpdateDate = new Date(packageInfo.lastUpdateTime);

            ((TextView) findViewById(R.id.version)).setText(
                    String.format(
                            Locale.getDefault(),
                            "%s (%d)",
                            packageInfo.versionName,
                            packageInfo.versionCode
                    )
            );

            ((TextView) findViewById(R.id.lastUpdated)).setText(
                    DateFormat.getDateInstance().format(lastUpdateDate)
            );
        }
    }
}
