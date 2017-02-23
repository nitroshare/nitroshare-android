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
        String size = (String) item.getProperties().get(Item.SIZE);
        if (size != null) {
            mTotalSize += Long.parseLong(size);
        }
    }

    /**
     * Retrieve the total size of the bundle content
     * @return
     */
    public long getTotalSize() {
        return mTotalSize;
    }
}
