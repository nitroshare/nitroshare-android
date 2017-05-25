package net.nitroshare.android.ui;

import android.content.Context;
import android.content.Intent;
import android.database.DataSetObserver;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import net.nitroshare.android.R;
import net.nitroshare.android.discovery.Device;
import net.nitroshare.android.transfer.TransferService;
import net.nitroshare.android.util.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Display a list of devices available for receiving a transfer
 *
 * mDNS (multicast DNS) is used to find other peers capable of receiving the transfer. Once a
 * device is selected, the transfer service is provided with the device information and the file.
 */
public class ShareActivity extends AppCompatActivity {

    private static final String TAG = "ShareActivity";

    /**
     * Adapter that discovers other devices on the network
     */
    private class DeviceAdapter extends ArrayAdapter<String> {

        /**
         * Maintain a mapping of device IDs to discovered devices
         */
        private final Map<String, Device> mDevices = new HashMap<>();

        /**
         * Maintain a queue of devices to resolve
         */
        private final List<NsdServiceInfo> mQueue = new ArrayList<>();

        private NsdManager mNsdManager;
        private String mThisDeviceName;

        /**
         * Prepare to resolve the next service
         *
         * For some inexplicable reason, Android chokes miserably when
         * resolving more than one service at a time. The queue performs each
         * resolution sequentially.
         */
        private void prepareNextService() {
            synchronized (mQueue) {
                mQueue.remove(0);
                if (mQueue.size() == 0) {
                    return;
                }
            }
            resolveNextService();
        }

        /**
         * Resolve the next service in the queue
         */
        private void resolveNextService() {
            NsdServiceInfo serviceInfo;
            synchronized (mQueue) {
                serviceInfo = mQueue.get(0);
            }
            Log.d(TAG, String.format("resolving \"%s\"", serviceInfo.getServiceName()));
            mNsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                @Override
                public void onServiceResolved(final NsdServiceInfo serviceInfo) {
                    Log.d(TAG, String.format("resolved \"%s\"", serviceInfo.getServiceName()));
                    final Device device = new Device(
                            serviceInfo.getServiceName(),
                            "",
                            serviceInfo.getHost(),
                            serviceInfo.getPort()
                    );
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mDevices.put(serviceInfo.getServiceName(), device);
                            add(serviceInfo.getServiceName());
                        }
                    });
                    prepareNextService();
                }

                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.e(TAG, String.format("unable to resolve \"%s\": %d",
                            serviceInfo.getServiceName(), errorCode));
                    prepareNextService();
                }
            });
        }

        /**
         * Listener for discovery events
         */
        private NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (serviceInfo.getServiceName().equals(mThisDeviceName)) {
                    return;
                }
                Log.d(TAG, String.format("found \"%s\"; queued for resolving", serviceInfo.getServiceName()));
                boolean resolve;
                synchronized (mQueue) {
                    resolve = mQueue.size() == 0;
                    mQueue.add(serviceInfo);
                }
                if (resolve) {
                    resolveNextService();
                }
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
                Log.d(TAG, "service discovery started");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "service discovery stopped");
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "unable to start service discovery");
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "unable to stop service discovery");
            }
        };

        DeviceAdapter() {
            super(ShareActivity.this, R.layout.view_simple_list_item, android.R.id.text1);
        }

        void start() {
            mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
            mNsdManager.discoverServices(Device.SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);

            mThisDeviceName = new Settings(getContext()).getString(Settings.Key.DEVICE_NAME);
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

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            convertView = super.getView(position, convertView, parent);
            Device device = mDevices.get(getItem(position));
            ((TextView) convertView.findViewById(android.R.id.text1)).setText(device.getName());
            ((TextView) convertView.findViewById(android.R.id.text2)).setText(device.getHost().getHostAddress());
            ((ImageView) convertView.findViewById(android.R.id.icon)).setImageResource(R.drawable.ic_device);
            return convertView;
        }
    }

    private DeviceAdapter mDeviceAdapter;

    /**
     * Given a SEND intent, build a list of URIs
     * @param intent intent received
     * @return list of URIs
     */
    private ArrayList<Uri> buildUriList(Intent intent) {
        if (intent.getAction().equals("android.intent.action.SEND_MULTIPLE")) {
            return intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        } else {
            ArrayList<Uri> uriList = new ArrayList<>();
            uriList.add((Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM));
            return uriList;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(new Settings(this).getTheme());
        setContentView(R.layout.activity_share);

        mDeviceAdapter = new DeviceAdapter();
        mDeviceAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                findViewById(R.id.progressBar).setVisibility(View.GONE);
            }
        });
        mDeviceAdapter.start();

        final ArrayList<Uri> uriList = buildUriList(getIntent());
        final ListView listView = (ListView) findViewById(R.id.selectList);
        listView.setAdapter(mDeviceAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Device device = mDeviceAdapter.getDevice(position);

                Intent startTransfer = new Intent(ShareActivity.this, TransferService.class);
                startTransfer.setAction(TransferService.ACTION_START_TRANSFER);
                startTransfer.putExtra(TransferService.EXTRA_DEVICE, device);
                startTransfer.putParcelableArrayListExtra(TransferService.EXTRA_URIS, uriList);
                startService(startTransfer);

                // Close the activity
                ShareActivity.this.setResult(RESULT_OK);
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
