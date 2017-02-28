package net.nitroshare.android.bundle;

import java.util.ArrayList;

/**
 * List of items to be transferred
 */
public class Bundle extends ArrayList<Item> {

    private long mTotalSize = 0;

    /**
     * Add the specified item to the bundle for transfer
     * @param item
     */
    public void addItem(Item item) {
        add(item);
        mTotalSize += item.getSize();
    }

    /**
     * Retrieve the total size of the bundle content
     * @return
     */
    public long getTotalSize() {
        return mTotalSize;
    }
}
