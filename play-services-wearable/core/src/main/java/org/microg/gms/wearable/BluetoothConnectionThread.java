/*
 * SPDX-FileCopyrightText: 2026 NEXORA contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.microg.wearable.WearableConnection;

import java.io.IOException;
import java.util.UUID;

/**
 * Owns one outbound RFCOMM connection to a paired Wear OS device.
 *
 * The service UUID is supplied by the caller so protocol identifiers remain
 * explicit, reviewable inputs rather than hidden transport assumptions.
 */
final class BluetoothConnectionThread extends Thread {
    private static final String TAG = "GmsWearBt";

    private final BluetoothDevice device;
    private final UUID serviceUuid;
    private final WearableConnection.Listener listener;

    private volatile BluetoothSocket socket;
    private volatile BluetoothWearableConnection wearableConnection;

    BluetoothConnectionThread(
            BluetoothDevice device,
            UUID serviceUuid,
            WearableConnection.Listener listener
    ) {
        super("GmsWear-Bluetooth-" + device.getAddress());
        this.device = device;
        this.serviceUuid = serviceUuid;
        this.listener = listener;
    }

    BluetoothWearableConnection getWearableConnection() {
        return wearableConnection;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void run() {
        try {
            socket = device.createRfcommSocketToServiceRecord(serviceUuid);
            socket.connect();
            BluetoothWearableConnection connection =
                    new BluetoothWearableConnection(socket, listener);
            wearableConnection = connection;
            connection.run();
        } catch (IOException | SecurityException e) {
            Log.w(TAG, "Wear OS Bluetooth connection failed for " + device.getAddress(), e);
        } finally {
            wearableConnection = null;
            BluetoothSocket current = socket;
            socket = null;
            if (current != null) {
                try {
                    current.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    void closeConnection() {
        BluetoothWearableConnection connection = wearableConnection;
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException ignored) {
            }
        }
        BluetoothSocket current = socket;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
            }
        }
        interrupt();
    }
}
