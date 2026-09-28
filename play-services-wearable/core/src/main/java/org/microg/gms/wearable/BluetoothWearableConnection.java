/*
 * SPDX-FileCopyrightText: 2026 NEXORA contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import android.bluetooth.BluetoothSocket;

import com.squareup.wire.Wire;

import org.microg.wearable.WearableConnection;
import org.microg.wearable.proto.MessagePiece;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Wearable Data Layer framing over an already-connected Bluetooth RFCOMM socket.
 *
 * The framing matches the Apache-2.0 microg/Wearable connection contract:
 * a 32-bit piece length followed by one serialized MessagePiece.
 */
final class BluetoothWearableConnection extends WearableConnection {
    private static final int MAX_PIECE_SIZE = 20 * 1024 * 1024;

    private final BluetoothSocket socket;
    private final DataInputStream input;
    private final DataOutputStream output;

    BluetoothWearableConnection(BluetoothSocket socket, Listener listener) throws IOException {
        super(listener);
        this.socket = socket;
        this.input = new DataInputStream(socket.getInputStream());
        this.output = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    protected synchronized void writeMessagePiece(MessagePiece piece) throws IOException {
        byte[] bytes = piece.toByteArray();
        if (bytes.length > MAX_PIECE_SIZE) {
            throw new IOException("Wearable message piece exceeds " + MAX_PIECE_SIZE + " bytes");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
        output.flush();
    }

    @Override
    protected MessagePiece readMessagePiece() throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_PIECE_SIZE) {
            throw new IOException("Invalid wearable message piece length: " + length);
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new Wire().parseFrom(bytes, MessagePiece.class);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
