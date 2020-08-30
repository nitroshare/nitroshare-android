package net.nitroshare.android.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.nitroshare.android.transfer.TransferService;

/**
 * Starts the transfer service
 */
public class StartReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)
                && startServiceOnBoot(context)
                && receiveTransferAllowed(context)) {
            TransferService.startStopService(context, true);
        }
    }

    private static boolean getBooleanFromSettings(Context context, Settings.Key key) {
        Settings s = new Settings(context);
        return s.getBoolean(key);
    }

    private static boolean receiveTransferAllowed(Context context) {
        return getBooleanFromSettings(context, Settings.Key.BEHAVIOR_RECEIVE);
    }

    private static boolean startServiceOnBoot(Context context) {
        return getBooleanFromSettings(context, Settings.Key.BEHAVIOR_AUTOSTART);
    }
}
