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

    public static final String NAME = "name";

    private String mUuid;
    private Map<String, String> mAttributes;
    private InetAddress mHost;
    private int mPort;

    /**
     * Create a device from a service description
     * @param serviceInfo device information
     */
    public Device(NsdServiceInfo serviceInfo) {
        mUuid = serviceInfo.getServiceName();
        mAttributes = new HashMap<>();
        for(Map.Entry<String, byte[]> entry : serviceInfo.getAttributes().entrySet()){
            mAttributes.put(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8));
        }
        mHost = serviceInfo.getHost();
        mPort = serviceInfo.getPort();
    }

    /**
     * Create a device from the provided information
     * @param uuid unique identifier for the device
     * @param attributes attributes, such as device name
     * @param port port for the service
     */
    public Device(String uuid, Map<String, String> attributes, int port) {
        mUuid = uuid;
        mAttributes = attributes;
        mPort = port;
    }

    public String getUuid() {
        return mUuid;
    }

    public String getAttribute(String key) {
        return mAttributes.get(key);
    }

    /**
     * Retrieve the device name
     * @return name if present, otherwise UUID
     */
    public String getName() {
        String name = getAttribute(NAME);
        return name == null ? getUuid() : name;
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
        serviceInfo.setServiceName(mUuid);
        for(Map.Entry<String, String> entry : mAttributes.entrySet()){
            serviceInfo.setAttribute(entry.getKey(), entry.getValue());
        }
        serviceInfo.setPort(mPort);
        return serviceInfo;
    }
}
