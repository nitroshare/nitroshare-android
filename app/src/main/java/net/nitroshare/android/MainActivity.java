package net.nitroshare.android;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import net.nitroshare.android.discovery.DiscoveryService;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Ensure we have write access to external storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int permission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                String[] permissions = {
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                };
                requestPermissions(permissions, 0);
            }
        }

        // TODO: error handling!

        // Start the discovery service
        Intent startIntent = new Intent(this, DiscoveryService.class);
        startIntent.setAction(DiscoveryService.ACTION_START);
        startService(startIntent);
    }
}
