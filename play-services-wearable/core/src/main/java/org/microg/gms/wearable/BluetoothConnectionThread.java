/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.microg.wearable.WearableConnection;

import java.io.IOException;
import java.util.UUID;

/**
 * Client connector for the documented WearableBt RFCOMM service.
 *
 * Protocol reference:
 * https://github.com/teccheck/wearos-research/blob/main/docs/btcomm.md
 */
public final class BluetoothConnectionThread extends Thread {
    private static final String TAG = "GmsWearBt";
    public static final UUID WEARABLE_BT_UUID =
            UUID.fromString("5e8945b0-9525-11e3-a5e2-0800200c9a66");

    private final Context context;
    private final String address;
    private final WearableConnection.Listener listener;

    private volatile BluetoothSocket socket;
    private volatile BluetoothWearableConnection wearableConnection;

    private BluetoothConnectionThread(
            Context context,
            String address,
            WearableConnection.Listener listener
    ) {
        super("WearableBt-" + address);
        this.context = context.getApplicationContext();
        this.address = address;
        this.listener = listener;
    }

    public static BluetoothConnectionThread clientConnect(
            Context context,
            String address,
            WearableConnection.Listener listener
    ) {
        return new BluetoothConnectionThread(context, address, listener);
    }

    public static boolean isBluetoothAddress(String address) {
        return address != null && BluetoothAdapter.checkBluetoothAddress(address);
    }

    public BluetoothWearableConnection getWearableConnection() {
        return wearableConnection;
    }

    public void closeConnection() {
        BluetoothWearableConnection connection = wearableConnection;
        if (connection != null) {
            try {
                connection.close();
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

    private boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void run() {
        if (!hasConnectPermission()) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission is required for WearableBt");
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Log.w(TAG, "Bluetooth adapter unavailable");
            return;
        }

        try {
            BluetoothDevice device = adapter.getRemoteDevice(address);
            BluetoothSocket created =
                    device.createRfcommSocketToServiceRecord(WEARABLE_BT_UUID);
            socket = created;
            Log.d(TAG, "Connecting WearableBt RFCOMM to " + address);
            created.connect();

            BluetoothWearableConnection connection =
                    new BluetoothWearableConnection(created, listener);
            wearableConnection = connection;
            connection.run();
        } catch (SecurityException e) {
            Log.w(TAG, "WearableBt permission denied", e);
        } catch (IOException e) {
            if (!isInterrupted()) {
                Log.w(TAG, "WearableBt connection failed for " + address, e);
            }
        } finally {
            closeConnection();
            wearableConnection = null;
            socket = null;
        }
    }
}
