package net.nitroshare.android;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

/**
 * Show basic application information
 */
public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
