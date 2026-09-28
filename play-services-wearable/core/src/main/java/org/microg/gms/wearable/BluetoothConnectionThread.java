/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.microg.wearable.SocketWearableConnection;
import org.microg.wearable.WearableConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.UUID;

/**
 * RFCOMM transport adapter for the existing microG Wearable wire protocol.
 *
 * The UUIDs match the WearableBt / Flow / Flow15 services observed on modern
 * Wear OS devices. Framing and message dispatch remain handled by the existing
 * SocketWearableConnection and MessageHandler implementations.
 */
public abstract class BluetoothConnectionThread extends Thread {
    private static final String TAG = "GmsWearBt";

    static final int RETRY_POLICY_DEFAULT = 0;
    static final int RETRY_POLICY_AGGRESSIVE = 1;
    static final int RETRY_POLICY_LOW_POWER = 2;
    static final int RETRY_POLICY_OFF = 3;

    public static final UUID WEARABLE_BT_UUID =
            UUID.fromString("5e8945b0-9525-11e3-a5e2-0800200c9a66");
    public static final UUID FLOW_UUID =
            UUID.fromString("fafbdd20-83f0-4389-addf-917ac9dae5b2");
    public static final UUID FLOW15_UUID =
            UUID.fromString("6a1eafb1-61c0-42a0-8bb0-a336fb1c3f00");

    public static final String WEARABLE_BT_SERVICE_NAME = "WearableBt";
    public static final String FLOW_SERVICE_NAME = "Flow";
    public static final String FLOW15_SERVICE_NAME = "Flow15";

    private volatile SocketWearableConnection wearableConnection;

    protected void setWearableConnection(SocketWearableConnection connection) {
        wearableConnection = connection;
    }

    public SocketWearableConnection getWearableConnection() {
        return wearableConnection;
    }

    public abstract void close();

    static long retryDelayMs(int retryPolicy, int failedAttempts) {
        int attempt = Math.max(1, failedAttempts);
        switch (retryPolicy) {
            case RETRY_POLICY_OFF:
                return -1L;
            case RETRY_POLICY_AGGRESSIVE:
                return Math.min(30_000L, 500L << Math.min(attempt - 1, 6));
            case RETRY_POLICY_LOW_POWER:
                return Math.min(300_000L, 10_000L << Math.min(attempt - 1, 5));
            case RETRY_POLICY_DEFAULT:
            default:
                return Math.min(60_000L, 1_000L << Math.min(attempt - 1, 6));
        }
    }

    private static Socket proxySocket(final BluetoothSocket socket) {
        return new Socket() {
            @Override
            public InputStream getInputStream() throws IOException {
                return socket.getInputStream();
            }

            @Override
            public OutputStream getOutputStream() throws IOException {
                return socket.getOutputStream();
            }

            @Override
            public boolean isConnected() {
                return socket.isConnected();
            }

            @Override
            public synchronized void close() throws IOException {
                socket.close();
            }
        };
    }

    @SuppressLint("MissingPermission")
    public static BluetoothConnectionThread clientConnect(
            BluetoothDevice device,
            UUID uuid,
            WearableConnection.Listener listener,
            int retryPolicy) {
        return new BluetoothConnectionThread() {
            private volatile BluetoothSocket socket;
            private volatile boolean running = true;

            @Override
            public void close() {
                running = false;
                BluetoothSocket current = socket;
                socket = null;
                if (current != null) {
                    try {
                        current.close();
                    } catch (IOException e) {
                        Log.w(TAG, "Failed to close Bluetooth client socket", e);
                    }
                }
                interrupt();
            }

            @Override
            public void run() {
                int failedAttempts = 0;
                while (running && !isInterrupted()) {
                    BluetoothSocket current = null;
                    boolean connected = false;
                    try {
                        current = device.createRfcommSocketToServiceRecord(uuid);
                        socket = current;
                        current.connect();

                        connected = true;
                        failedAttempts = 0;

                        SocketWearableConnection connection =
                                new SocketWearableConnection(proxySocket(current), listener);
                        setWearableConnection(connection);
                        connection.run();
                    } catch (IOException | SecurityException e) {
                        if (running && !isInterrupted()) {
                            Log.w(TAG, "Wear OS Bluetooth client connection failed", e);
                        }
                    } finally {
                        try {
                            if (current != null) current.close();
                        } catch (IOException ignored) {
                        }
                        socket = null;
                        setWearableConnection(null);
                    }

                    if (!running || isInterrupted()) break;

                    failedAttempts = connected ? 1 : failedAttempts + 1;
                    long delayMs = retryDelayMs(retryPolicy, failedAttempts);
                    if (delayMs < 0) {
                        Log.d(TAG, "Automatic Wear OS Bluetooth retry disabled by policy");
                        break;
                    }

                    Log.d(TAG, "Retrying Wear OS Bluetooth in " + delayMs + " ms");
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        interrupt();
                        break;
                    }
                }
            }
        };
    }

    @SuppressLint("MissingPermission")
    public static BluetoothConnectionThread serverListen(
            BluetoothAdapter adapter,
            String serviceName,
            UUID uuid,
            WearableConnection.Listener listener) {
        return new BluetoothConnectionThread() {
            private volatile BluetoothServerSocket serverSocket;

            @Override
            public void close() {
                BluetoothServerSocket current = serverSocket;
                serverSocket = null;
                if (current != null) {
                    try {
                        current.close();
                    } catch (IOException e) {
                        Log.w(TAG, "Failed to close Bluetooth server socket", e);
                    }
                }
            }

            @Override
            public void run() {
                try {
                    serverSocket = adapter.listenUsingRfcommWithServiceRecord(serviceName, uuid);
                    while (!isInterrupted()) {
                        BluetoothSocket accepted;
                        try {
                            accepted = serverSocket.accept();
                        } catch (IOException e) {
                            if (!isInterrupted()) Log.d(TAG, serviceName + " listener stopped", e);
                            break;
                        }
                        if (accepted == null) continue;
                        try {
                            SocketWearableConnection connection =
                                    new SocketWearableConnection(proxySocket(accepted), listener);
                            setWearableConnection(connection);
                            connection.run();
                        } catch (IOException e) {
                            Log.w(TAG, serviceName + " connection failed", e);
                        } finally {
                            setWearableConnection(null);
                            try {
                                accepted.close();
                            } catch (IOException ignored) {
                            }
                        }
                    }
                } catch (IOException | SecurityException e) {
                    if (!isInterrupted()) Log.w(TAG, "Unable to listen on " + serviceName, e);
                } finally {
                    close();
                }
            }
        };
    }
}
