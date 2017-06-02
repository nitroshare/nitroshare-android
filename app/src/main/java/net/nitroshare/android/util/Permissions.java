package net.nitroshare.android.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

/**
 * Utility methods for checking permissions
 */
public class Permissions {

    private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    /**
     * Determine if the app has permission to access storage
     * @param context use this context for checking
     * @return true if permission exists
     */
    public static boolean haveStoragePermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request permission to access storage
     * @param activity use this activity for the request
     */
    public static void requestStoragePermission(Activity activity) {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE
        );
    }

    /**
     * Determine if storage access permission was granted
     * @return true if permission was granted
     */
    public static boolean obtainedStoragePermission(int requestCode, int[] grantResuts) {
        return requestCode == PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE &&
                grantResuts.length > 0 &&
                grantResuts[0] == PackageManager.PERMISSION_GRANTED;
    }
}
