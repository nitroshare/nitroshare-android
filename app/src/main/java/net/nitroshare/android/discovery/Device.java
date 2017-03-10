package net.nitroshare.android.discovery;

import android.net.nsd.NsdServiceInfo;

import java.io.Serializable;
import java.net.InetAddress;

/**
 * Device discoverable through mDNS
 *
 * The device UUID is also used as the service name
 */
public class Device implements Serializable {

    public static final String SERVICE_TYPE = "_nitroshare._tcp.";

    public static final String UUID = "uuid";

    private String mUuid;
    private String mName;
    private InetAddress mHost;
    private int mPort;

    /**
     * Create a device from the provided information
     * @param uuid unique identifier for the device
     * @param name device name
     * @param host device host
     * @param port port for the service
     */
    public Device(String uuid, String name, InetAddress host, int port) {
        mUuid = uuid;
        mName = name;
        mHost = host;
        mPort = port;
    }

    public String getName() {
        return mName;
    }

    public InetAddress getHost() {
        return mHost;
    }

    public int getPort() {
        return mPort;
    }

    public NsdServiceInfo toServiceInfo() {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setServiceName(mName);
        //serviceInfo.setAttribute(UUID, mUuid);
        serviceInfo.setPort(mPort);
        return serviceInfo;
    }
}
