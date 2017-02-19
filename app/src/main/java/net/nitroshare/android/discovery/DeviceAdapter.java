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
import java.net.UnknownHostException;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * ArrayAdapter for devices discovered via mDNS
 *
 * This class can easily be attached to a ListView to provide a list of devices discovered via
 * mDNS. Devices are added and removed asynchronously.
 */
public class DeviceAdapter extends ArrayAdapter<String> {

    private static final String TAG = "DeviceAdapter";
    private static final String SERVICE = "_nitroshare._tcp";
    private static final String UUID = "uuid";

    /**
     * Properties for a specific device
     */
    private class Device {
        private String mUuid;
        private Map<String, byte[]> mProperties;
        private InetAddress mAddress;
        private int mPort;
    }

    // Map of device UUIDs to Devices
    private final ArrayMap<String, Device> mDevices = new ArrayMap<>();

    /**
     * Attempt to obtain the UUID for a device
     * @param serviceInfo information about the service
     * @return unique device identifier or NULL
     */
    private String getUuid(NsdServiceInfo serviceInfo) {
        byte[] uuid = serviceInfo.getAttributes().get(UUID);
        return uuid == null ? null : new String(uuid, UTF_8);
    }

    // Listener for populating the map with data obtained through mDNS
    private final NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {
            String uuid = getUuid(serviceInfo);
            if (uuid != null) {
                Log.d(TAG, "Service found: " + uuid);
                Device device = new Device();
                device.mUuid = uuid;
                device.mProperties = serviceInfo.getAttributes();
                device.mAddress = serviceInfo.getHost();
                device.mPort = serviceInfo.getPort();
                mDevices.put(uuid, device);
                add(uuid);
            }
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            String uuid = getUuid(serviceInfo);
            if (uuid != null) {
                Log.d(TAG, "Service lost: " + uuid);
                mDevices.remove(uuid);
                remove(uuid);
            }
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

    public DeviceAdapter(Context context) {
        super(context, R.layout.view_simple_list_item, android.R.id.text1);
        mNsdManager = (NsdManager)context.getSystemService(Context.NSD_SERVICE);
        mNsdManager.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        convertView = super.getView(position, convertView, parent);
        Device device = mDevices.get(getItem(position));
        ((TextView)convertView.findViewById(android.R.id.text1)).setText(device.mUuid);
        ((TextView)convertView.findViewById(android.R.id.text2)).setText("TODO");
        return convertView;
    }
}
