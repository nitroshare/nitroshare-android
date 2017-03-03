package net.nitroshare.android.transfer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import net.nitroshare.android.bundle.Bundle;
import net.nitroshare.android.bundle.FileItem;
import net.nitroshare.android.discovery.Device;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Receive incoming transfers and initiate outgoing transfers
 *
 * This service listens for new connections and instantiates Transfer instances
 * to process them. It will also initiate a transfer when the appropriate
 * intent is supplied.
 */
public class TransferService extends Service {

    private static final String TAG = "TransferService";

    public static final String ACTION_START_LISTENING = "start_listening";
    public static final String ACTION_STOP_LISTENING = "stop_listening";

    public static final String ACTION_START_TRANSFER = "start_transfer";
    public static final String EXTRA_DEVICE = "device";
    public static final String EXTRA_URLS = "urls";
    public static final String EXTRA_FILENAMES = "filenames";

    public static final String ACTION_STOP_TRANSFER = "stop_transfer";
    public static final String EXTRA_TRANSFER = "transfer";

    private static int STATE_STOPPED = 0;
    private static int STATE_STARTED = 1;
    private static int STATE_STOP = 2;
    private static int STATE_STOPPING = 3;

    private ServerSocketChannel mServerSocketChannel;
    private AtomicInteger mSocketState = new AtomicInteger(STATE_STOPPED);

    /**
     * Start listening for new incoming connections
     *
     * If the socket is already listening for new connections, then this method
     * does nothing. It will continue to listen until stopped.
     */
    private void startListening() {
        if (mSocketState.compareAndSet(STATE_STOPPED, STATE_STARTED)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        mServerSocketChannel = ServerSocketChannel.open();
                        mServerSocketChannel.socket().bind(new InetSocketAddress(40818));

                        Log.i(TAG, String.format("server bound to %d",
                                mServerSocketChannel.socket().getLocalPort()));

                        // Create the selector that will
                        Selector selector = Selector.open();
                        SelectionKey selectionKey = mServerSocketChannel.register(
                                selector, SelectionKey.OP_ACCEPT);

                        // Create new transfers for new connections
                        while (true) {
                            selector.select();
                            if (mSocketState.compareAndSet(STATE_STOP, STATE_STOPPING)) {
                                Log.i(TAG, "server shutting down");
                                break;
                            }
                            if (selectionKey.isAcceptable()) {
                                SocketChannel socketChannel = mServerSocketChannel.accept();
                                new TransferWrapper(TransferService.this, new Transfer(
                                        socketChannel, "Unknown")).run();
                            }
                        }

                        // Shut down the server and indicate this is complete
                        mServerSocketChannel.close();
                        mSocketState.set(STATE_STOPPED);

                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
            }).start();
        }
    }

    /**
     * Shut down the server if it is running
     */
    private void stopListening() {
        mSocketState.compareAndSet(STATE_STARTED, STATE_STOP);
    }

    /**
     * Traverse a directory tree and add all files to the bundle
     * @param root the directory to which all filenames will be relative
     * @param bundle target for all files that are found
     */
    private void traverseDirectory(File root, Bundle bundle) {
        Stack<File> stack = new Stack<>();
        stack.push(root);
        while (stack.empty()) {
            File topOfStack = stack.pop();
            for (File f : topOfStack.listFiles()) {
                if (f.isDirectory()) {
                    stack.push(f);
                } else {
                    String relativeFilename = f.getAbsolutePath().substring(
                            root.getAbsolutePath().length() + 1);
                    bundle.addItem(new FileItem(f, relativeFilename));
                }
            }
        }
    }

    /**
     * Create a bundle from the provided list of URLs and files
     * @param urls list of URLs
     * @param filenames list of filenames
     * @return newly created bundle
     */
    private Bundle createBundle(String[] urls, String[] filenames) {
        Bundle bundle = new Bundle();
        if (urls != null) {
            for (String url : urls) {
                // TODO: add URL
            }
        }
        if (filenames != null) {
            for (String filename : filenames) {
                File file = new File(filename);
                if (file.isDirectory()) {
                    traverseDirectory(file, bundle);
                } else {
                    bundle.addItem(new FileItem(file));
                }
            }
        }
        return bundle;
    }

    /**
     * Start a transfer using the provided intent
     */
    private void startTransfer(Intent intent) {

        // Retrieve the parameters from the intent
        final Device device = (Device) intent.getSerializableExtra(EXTRA_DEVICE);
        String[] urls = intent.getStringArrayExtra(EXTRA_URLS);
        String[] filenames = intent.getStringArrayExtra(EXTRA_FILENAMES);

        // Create the bundle
        Bundle bundle = createBundle(urls, filenames);

        // Create the transfer and transfer wrapper
        try {
            new TransferWrapper(this, new Transfer(device, "Android", bundle)).run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stop a transfer in progress
     */
    private void stopTransfer(Intent intent) {
        TransferWrapper.stopTransfer(intent.getIntExtra(EXTRA_TRANSFER, -1));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        switch (intent.getAction()) {
            case ACTION_START_LISTENING:
                startListening();
                break;
            case ACTION_STOP_LISTENING:
                stopListening();
                break;
            case ACTION_START_TRANSFER:
                startTransfer(intent);
                break;
            case ACTION_STOP_TRANSFER:
                stopTransfer(intent);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
