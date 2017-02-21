package net.nitroshare.android.discovery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Starts discovery upon boot
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent startIntent = new Intent(context, DiscoveryService.class);
        startIntent.setAction(DiscoveryService.ACTION_START);
        context.startService(startIntent);
    }
}
