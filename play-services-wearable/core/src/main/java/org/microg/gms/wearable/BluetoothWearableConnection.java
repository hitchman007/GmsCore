/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import android.bluetooth.BluetoothSocket;

import org.microg.wearable.WearableConnection;
import org.microg.wearable.proto.MessagePiece;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * WearableConnection backed by a Bluetooth Classic RFCOMM socket.
 *
 * WearableBt uses the same framing as microG's SocketWearableConnection:
 * a 32-bit big-endian length followed by a protobuf MessagePiece.
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
            throw new IOException("Invalid WearableBt piece size: " + length);
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
