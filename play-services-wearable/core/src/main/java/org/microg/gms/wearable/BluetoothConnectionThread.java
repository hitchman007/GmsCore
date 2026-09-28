/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.google.android.gms.wearable.ConnectionConfiguration;

import org.microg.wearable.WearableConnection;

import java.io.Closeable;
import java.io.IOException;
import java.util.UUID;

/**
 * RFCOMM client for the Wear OS primary WearableBt channel.
 *
 * The peer Wear OS device is the RFCOMM server. Protocol handshakes and data
 * sync remain in MessageHandler through the existing WearableConnection API.
 */
public final class BluetoothConnectionThread extends Thread implements Closeable {
    private static final String TAG = "GmsWearBt";
    static final UUID WEARABLE_BT_UUID =
            UUID.fromString("5e8945b0-9525-11e3-a5e2-0800200c9a66");
    private static final long RETRY_DELAY_MS = 5_000L;

    private final ConnectionConfiguration configuration;
    private final WearableConnection.Listener listener;
    private final BluetoothAdapter adapter;

    private volatile boolean stopped;
    private volatile BluetoothSocket socket;
    private volatile BluetoothWearableConnection connection;

    public BluetoothConnectionThread(
            ConnectionConfiguration configuration,
            WearableConnection.Listener listener
    ) {
        super("WearableBtClientThread-" + configuration.name);
        this.configuration = configuration;
        this.listener = listener;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
    }

    public BluetoothWearableConnection getWearableConnection() {
        return connection;
    }

    @Override
    public void run() {
        if (adapter == null) {
            Log.w(TAG, "Bluetooth unavailable; cannot start WearableBt transport");
            return;
        }
        if (configuration.address == null ||
                !BluetoothAdapter.checkBluetoothAddress(configuration.address)) {
            Log.w(TAG, "Invalid WearableBt peer address for " + configuration.name);
            return;
        }

        final BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(configuration.address);
        } catch (IllegalArgumentException | SecurityException e) {
            Log.w(TAG, "Unable to resolve WearableBt peer", e);
            return;
        }

        while (!stopped && !isInterrupted()) {
            BluetoothSocket currentSocket = null;
            try {
                try {
                    adapter.cancelDiscovery();
                } catch (SecurityException e) {
                    Log.d(TAG, "Bluetooth discovery could not be cancelled", e);
                }

                Log.d(TAG, "Connecting WearableBt to " + configuration.address);
                currentSocket = device.createRfcommSocketToServiceRecord(WEARABLE_BT_UUID);
                socket = currentSocket;
                currentSocket.connect();

                BluetoothWearableConnection currentConnection =
                        new BluetoothWearableConnection(currentSocket, listener);
                connection = currentConnection;
                currentConnection.run();
            } catch (IOException | SecurityException e) {
                if (!stopped) Log.w(TAG, "WearableBt connection failed", e);
            } finally {
                connection = null;
                socket = null;
                if (currentSocket != null) {
                    try {
                        currentSocket.close();
                    } catch (IOException ignored) {
                    }
                }
            }

            if (!stopped && !isInterrupted()) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    interrupt();
                }
            }
        }
    }

    @Override
    public void close() {
        stopped = true;
        interrupt();
        BluetoothWearableConnection currentConnection = connection;
        if (currentConnection != null) {
            try {
                currentConnection.close();
            } catch (IOException ignored) {
            }
        }
        BluetoothSocket currentSocket = socket;
        if (currentSocket != null) {
            try {
                currentSocket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
