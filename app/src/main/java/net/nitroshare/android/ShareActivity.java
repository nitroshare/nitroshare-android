package net.nitroshare.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import net.nitroshare.android.discovery.Device;
import net.nitroshare.android.transfer.TransferService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Display a list of devices available for receiving a transfer
 *
 * mDNS (multicast DNS) is used to find other peers capable of receiving the transfer. Once a
 * device is selected, the transfer service is provided with the device information and the file.
 */
public class ShareActivity extends Activity {

    private static final String TAG = "ShareActivity";

    private NsdManager mNsdManager;

    /**
     * Adapter that discovers other devices on the network
     */
    class DeviceAdapter extends ArrayAdapter<String> {

        /**
         * Maintain a mapping of device IDs to discovered devices
         */
        private final Map<String, Device> mDevices = new HashMap<>();

        /**
         * Listener for discovery events
         */
        private NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onServiceFound(final NsdServiceInfo serviceInfo) {
                Log.d(TAG, String.format("found \"%s\"", serviceInfo.getServiceName()));
                if (serviceInfo.getHost() == null) {
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mDevices.put(serviceInfo.getServiceName(), new Device(serviceInfo));
                        add(serviceInfo.getServiceName());
                    }
                });
            }

            @Override
            public void onServiceLost(final NsdServiceInfo serviceInfo) {
                Log.d(TAG, String.format("lost \"%s\"", serviceInfo.getServiceName()));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        remove(serviceInfo.getServiceName());
                        mDevices.remove(serviceInfo.getServiceName());
                    }
                });
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

        DeviceAdapter() {
            super(ShareActivity.this, R.layout.view_simple_list_item, android.R.id.text1);
        }

        void start() {
            mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
            mNsdManager.discoverServices(Device.SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
        }

        void stop() {
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        }

        /**
         * Retrieve the specified device
         * @param position device index
         * @return device at the specified position
         */
        Device getDevice(int position) {
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

    private DeviceAdapter mDeviceAdapter;

    /**
     * Given a SEND intent, determine the absolute paths to the items to send
     * @param intent intent to resolve
     * @return list of absolute filenames
     */
    private String[] resolveIntent(Intent intent) {
        ArrayList<Uri> unresolvedUris = new ArrayList<>();
        switch (intent.getAction()) {
            case "android.intent.action.SEND":
                unresolvedUris.add((Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM));
                break;
            case "android.intent.action.SEND_MULTIPLE":
                unresolvedUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                break;
        }
        Log.i(TAG, String.format("received intent with %d URIs", unresolvedUris.size()));
        ArrayList<String> resolvedFilenames = new ArrayList<>();
        for (Uri uri : unresolvedUris) {
            switch (uri.getScheme()) {
                case "content":
                    Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                    if (cursor != null) {
                        cursor.moveToFirst();
                        resolvedFilenames.add(cursor.getString(
                                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)));
                        cursor.close();
                    }
                    break;
                case "file":
                    resolvedFilenames.add(uri.getPath());
                    break;
            }
        }
        Log.i(TAG, String.format("successfully resolved %d URIs", resolvedFilenames.size()));
        return resolvedFilenames.toArray(new String[resolvedFilenames.size()]);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);

        mDeviceAdapter = new DeviceAdapter();
        mDeviceAdapter.start();

        final String[] filenames = resolveIntent(getIntent());
        final ListView listView = (ListView) findViewById(R.id.selectList);
        listView.setAdapter(mDeviceAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Device device = mDeviceAdapter.getDevice(position);

                Intent startTransfer = new Intent(ShareActivity.this, TransferService.class);
                startTransfer.setAction(TransferService.ACTION_START_TRANSFER);
                startTransfer.putExtra(TransferService.EXTRA_DEVICE, device);
                startTransfer.putExtra(TransferService.EXTRA_FILENAMES, filenames);
                startService(startTransfer);

                // Close the activity
                ShareActivity.this.finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        mDeviceAdapter.stop();
        super.onDestroy();
    }
}
