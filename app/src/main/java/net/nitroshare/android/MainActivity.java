package net.nitroshare.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import net.nitroshare.android.discovery.DiscoveryService;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start the discovery service
        Intent startIntent = new Intent(this, DiscoveryService.class);
        startIntent.setAction(DiscoveryService.ACTION_START);
        startService(startIntent);
    }
}
