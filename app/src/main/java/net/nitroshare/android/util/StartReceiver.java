package net.nitroshare.android.misc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import net.nitroshare.android.R;
import net.nitroshare.android.transfer.TransferService;

/**
 * Starts the transfer service
 */
public class StartReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        TransferService.startStopService(
                context,
                sharedPreferences.getBoolean(
                        context.getString(R.string.setting_behavior_receive),
                        true
                )
        );
    }
}
