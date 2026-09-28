/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
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
 * WearableConnection backed by a Bluetooth RFCOMM socket.
 *
 * The wearable transport uses the same 32-bit length-prefixed MessagePiece
 * framing as the existing socket transport, so all higher-level wearable
 * protocol handling remains in WearableConnection/MessageHandler.
 */
public final class BluetoothWearableConnection extends WearableConnection {
    private static final int MAX_PIECE_SIZE = 20 * 1024 * 1024;

    private final BluetoothSocket socket;
    private final DataInputStream input;
    private final DataOutputStream output;

    public BluetoothWearableConnection(BluetoothSocket socket, Listener listener) throws IOException {
        super(listener);
        this.socket = socket;
        this.input = new DataInputStream(socket.getInputStream());
        this.output = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    protected synchronized void writeMessagePiece(MessagePiece piece) throws IOException {
        byte[] bytes = piece.toByteArray();
        output.writeInt(bytes.length);
        output.write(bytes);
        output.flush();
    }

    @Override
    protected MessagePiece readMessagePiece() throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > MAX_PIECE_SIZE) {
            throw new IOException("Invalid wearable message piece size: " + length);
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
