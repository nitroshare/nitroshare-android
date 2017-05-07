package net.nitroshare.android.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.nitroshare.android.R;
import net.nitroshare.android.transfer.TransferService;

/**
 * Starts the transfer service
 */
public class StartReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        TransferService.startStopService(
                context,
                new Settings(context).getBoolean(Settings.Key.BEHAVIOR_RECEIVE)
        );
    }
}
