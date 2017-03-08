package net.nitroshare.android.discovery;

import android.net.nsd.NsdServiceInfo;

import java.io.Serializable;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Device discoverable through mDNS
 *
 * The device UUID is also used as the service name
 */
public class Device implements Serializable {

    public static final String SERVICE_TYPE = "_nitroshare._tcp.";

    public static final String UUID = "uuid";

    /**
     * Device description is invalid
     *
     * This is thrown when an invalid DNS-SD entry is used to initialize a
     * device. This includes missing UUID.
     */
    public class InvalidDeviceException extends Exception {}

    private String mUuid;
    private String mName;
    private InetAddress mHost;
    private int mPort;

    /**
     * Create a device from a service description
     * @param serviceInfo device information
     * @throws InvalidDeviceException
     */
    public Device(NsdServiceInfo serviceInfo) throws InvalidDeviceException {
        byte[] uuid = serviceInfo.getAttributes().get(UUID);
        if (uuid == null) {
            throw new InvalidDeviceException();
        }
        mUuid = new String(uuid, StandardCharsets.UTF_8);
        mName = serviceInfo.getServiceName();
        mHost = serviceInfo.getHost();
        mPort = serviceInfo.getPort();
    }

    /**
     * Create a device from the provided information
     * @param uuid unique identifier for the device
     * @param name device name
     * @param port port for the service
     */
    public Device(String uuid, String name, int port) {
        mUuid = uuid;
        mName = name;
        mPort = port;
    }

    public String getUuid() {
        return mUuid;
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
        serviceInfo.setAttribute(UUID, mUuid);
        serviceInfo.setPort(mPort);
        return serviceInfo;
    }
}
