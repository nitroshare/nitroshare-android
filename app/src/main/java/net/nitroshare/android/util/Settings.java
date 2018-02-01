package net.nitroshare.android.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;

import net.nitroshare.android.R;

import java.io.File;
import java.util.UUID;

/**
 * Provide a convenient location for accessing common settings and their defaults.
 */
public class Settings {

    public enum Key {
        BEHAVIOR_RECEIVE,      // Listen for incoming transfers
        BEHAVIOR_OVERWRITE,    // Overwrite files with identical names
        DEVICE_NAME,           // Device name broadcast via mDNS
        DEVICE_UUID,           // Unique identifier for the device
        INTRO_SHOWN,           // Intro has been shown to user?
        TRANSFER_DIRECTORY,    // Directory for storing received files
        TRANSFER_NOTIFICATION, // Default sounds, vibrate, etc. for transfers
        UI_DARK,               // Use a dark theme
    }

    private final SharedPreferences mSharedPreferences;

    /**
     * Create a new Settings instance
     * @param context use this context for retrieving settings
     */
    public Settings(Context context) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Obtain a default value for the specified key
     * @param key retrieve default for this key
     * @return default value or null if key is invalid
     */
    @Nullable
    public Object getDefault(Key key) {
        switch (key) {
            case BEHAVIOR_RECEIVE:
                return true;
            case BEHAVIOR_OVERWRITE:
                return false;
            case DEVICE_NAME:
                return Build.MODEL;
            case DEVICE_UUID:
                String uuid = String.format("{%s}", UUID.randomUUID().toString());
                mSharedPreferences.edit().putString(Key.DEVICE_UUID.name(), uuid).apply();
                return uuid;
            case INTRO_SHOWN:
                return false;
            case TRANSFER_DIRECTORY:
                File storage = Environment.getExternalStorageDirectory();
                File downloads = new File(storage, "Download");
                return new File(downloads, "NitroShare").getAbsolutePath();
            case TRANSFER_NOTIFICATION:
                return true;
            case UI_DARK:
                return false;
            default:
                return null;
        }
    }

    /**
     * Retrieve the boolean value or its default for the specified key
     * @param key retrieve value for this key
     * @return value of the key
     * @throws ClassCastException if the key is not a boolean
     */
    public boolean getBoolean(Key key) throws ClassCastException {
        return mSharedPreferences.getBoolean(key.name(), (boolean) getDefault(key));
    }

    /**
     * Store a boolean value for the specified key
     * @param key store a value for this key
     * @param value store this value
     */
    public void putBoolean(Key key, boolean value) {
        mSharedPreferences.edit().putBoolean(key.name(), value).apply();
    }

    /**
     * Retrieve the string value or its default for the specified key
     * @param key retrieve value for this key
     * @return value of the key
     * @throws ClassCastException if the key is not a string
     */
    @Nullable
    public String getString(Key key) throws ClassCastException {
        return mSharedPreferences.getString(key.name(), (String) getDefault(key));
    }

    /**
     * Convenience method for determining the current app theme to use
     * @param lightTheme theme to use when dark is disabled
     * @param darkTheme theme to use when dark is enabled
     * @return integer ID of the correct theme to use
     */
    @StyleRes
    public int getTheme(@StyleRes int lightTheme, @StyleRes int darkTheme) {
        return getBoolean(Key.UI_DARK) ? darkTheme : lightTheme;
    }

    /**
     * Convenience method for determining the current app theme to use
     * @return integer ID of the correct theme to use
     *
     * This method uses the default themes for light and dark.
     */
    @StyleRes
    public int getTheme() {
        return getTheme(R.style.LightTheme, R.style.DarkTheme);
    }
}
