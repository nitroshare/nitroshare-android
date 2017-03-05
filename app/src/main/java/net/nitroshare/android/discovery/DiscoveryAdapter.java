package net.nitroshare.android.discovery;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import net.nitroshare.android.R;

import java.net.InetAddress;

/**
 * ArrayAdapter for devices discovered via mDNS
 *
 * This class can easily be attached to a ListView to provide a list of
 * devices discovered via mDNS. Devices are added and removed asynchronously.
 *
 * Be sure to use start() and stop() when beginning and ending discovery.
 */
public class DiscoveryAdapter extends ArrayAdapter<String> {

    private static final String TAG = "DiscoveryAdapter";

    private final ArrayMap<String, Device> mDevices = new ArrayMap<>();

    private final NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {
            if (serviceInfo.getHost() != null) {
                Log.d(TAG, String.format("found %s", serviceInfo.getServiceName()));
                mDevices.put(serviceInfo.getServiceName(), new Device(serviceInfo));
                add(serviceInfo.getServiceName());
            }
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            remove(serviceInfo.getServiceName());
            mDevices.remove(serviceInfo.getServiceName());
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
            Log.d(TAG, "Service discovery started");
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Log.d(TAG, "Service discovery stopped");
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.e(TAG, "onStartDiscoveryFailed()");
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Log.e(TAG, "onStopDiscoveryFailed()");
        }
    };

    private NsdManager mNsdManager;

    public DiscoveryAdapter(Context context) {
        super(context, R.layout.view_simple_list_item, android.R.id.text1);
        mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
    }

    /**
     * Start service discovery
     */
    public void start() {
        mNsdManager.discoverServices(Device.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    /**
     * Stop service discovery
     */
    public void stop() {
        mNsdManager.stopServiceDiscovery(mDiscoveryListener);
    }

    /**
     * Retrieve the specified device
     * @param position device index
     * @return device at the specified position
     */
    public Device getDevice(int position) {
        return mDevices.get(getItem(position));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        convertView = super.getView(position, convertView, parent);
        Device device = mDevices.get(getItem(position));
        ((TextView) convertView.findViewById(android.R.id.text1)).setText(device.getName());
        ((TextView) convertView.findViewById(android.R.id.text2)).setText(device.getHost().getHostAddress());
        return convertView;
    }
}
