/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.squareup.wire.Wire;

import org.microg.wearable.WearableConnection;
import org.microg.wearable.proto.MessagePiece;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Bluetooth Classic transport for the WearableBt channel.
 *
 * The WearOS research linked from microG/GmsCore#2843 documents the watch as
 * the RFCOMM server for UUID 5e8945b0-9525-11e3-a5e2-0800200c9a66. Frames are
 * a 32-bit big-endian length followed by the same protobuf MessagePiece used
 * by the existing socket transport.
 */
public final class BluetoothConnectionThread extends Thread {
    private static final String TAG = "GmsWearBt";
    public static final UUID WEARABLE_BT_UUID =
            UUID.fromString("5e8945b0-9525-11e3-a5e2-0800200c9a66");

    private final String address;
    private final WearableConnection.Listener listener;
    private volatile BluetoothSocket socket;
    private volatile BluetoothWearableConnection wearableConnection;

    public BluetoothConnectionThread(String address, WearableConnection.Listener listener) {
        super("WearableBluetooth-" + address);
        this.address = address;
        this.listener = listener;
    }

    public WearableConnection getWearableConnection() {
        return wearableConnection;
    }

    @Override
    public void run() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Log.w(TAG, "Bluetooth adapter unavailable");
            return;
        }
        try {
            BluetoothDevice device = adapter.getRemoteDevice(address);
            BluetoothSocket nextSocket =
                    device.createRfcommSocketToServiceRecord(WEARABLE_BT_UUID);
            socket = nextSocket;
            nextSocket.connect();

            BluetoothWearableConnection connection =
                    new BluetoothWearableConnection(nextSocket, listener);
            wearableConnection = connection;
            connection.run();
        } catch (IllegalArgumentException | IOException | SecurityException e) {
            Log.w(TAG, "WearableBt connection failed for " + address, e);
        } finally {
            close();
        }
    }

    public void close() {
        BluetoothWearableConnection connection = wearableConnection;
        wearableConnection = null;
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException ignored) {
            }
        }
        BluetoothSocket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
            }
        }
        interrupt();
    }

    private static final class BluetoothWearableConnection extends WearableConnection {
        private static final int MAX_PIECE_SIZE = 20 * 1024 * 1024;

        private final BluetoothSocket socket;
        private final DataInputStream input;
        private final DataOutputStream output;
        private final Wire wire = new Wire();

        private BluetoothWearableConnection(
                BluetoothSocket socket,
                WearableConnection.Listener listener
        ) throws IOException {
            super(listener);
            this.socket = socket;
            this.input = new DataInputStream(socket.getInputStream());
            this.output = new DataOutputStream(socket.getOutputStream());
        }

        @Override
        protected void writeMessagePiece(MessagePiece piece) throws IOException {
            byte[] bytes = piece.toByteArray();
            output.writeInt(bytes.length);
            output.write(bytes);
            output.flush();
        }

        @Override
        protected MessagePiece readMessagePiece() throws IOException {
            int length = input.readInt();
            if (length <= 0 || length > MAX_PIECE_SIZE) {
                throw new IOException("Invalid WearableBt frame length: " + length);
            }
            byte[] bytes = new byte[length];
            input.readFully(bytes);
            return wire.parseFrom(bytes, MessagePiece.class);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
