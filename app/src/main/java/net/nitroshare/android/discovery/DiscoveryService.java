package net.nitroshare.android.discovery;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.ArrayMap;
import android.util.Log;

import java.util.Map;
import java.util.UUID;

/**
 * Respond to mDNS queries with the information for this host
 */
public class DiscoveryService extends Service {

    private static final String TAG = "DiscoveryService";

    private final NsdManager.RegistrationListener mRegistrationListener = new NsdManager.RegistrationListener() {
        @Override
        public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            Log.d(TAG, "Service registered");
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            Log.d(TAG, "Service unregistered");
        }

        @Override
        public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Log.d(TAG, "onRegistrationFailed()");
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Log.d(TAG, "onUnregistrationFailed()");
        }
    };

    private NsdManager mNsdManager;
    private SharedPreferences mSharedPreferences;

    @Override
    public void onCreate() {
        mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    public static final String ACTION_START = "start";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        switch (intent.getAction()) {
            case ACTION_START:
                start();
                break;
        }
        return START_REDELIVER_INTENT;
    }

    public static final String PREF_DEVICE_UUID = "uuid";
    public static final String PREF_DEVICE_NAME = "name";

    public void start() {
        String uuid = mSharedPreferences.getString(PREF_DEVICE_UUID, "");
        if (uuid.length() == 0) {
            uuid = "{" + UUID.randomUUID().toString() + "}";
            mSharedPreferences.edit().putString(PREF_DEVICE_UUID, uuid).commit();
        }
        String name = mSharedPreferences.getString(PREF_DEVICE_NAME, "");
        if (name.length() == 0) {
            name = Build.MODEL;
            mSharedPreferences.edit().putString(PREF_DEVICE_NAME, name).commit();
        }
        Map<String, String> attributes = new ArrayMap<>();
        attributes.put(Device.NAME, name);
        mNsdManager.registerService(new Device(uuid, attributes, 1800).toServiceInfo(), NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
