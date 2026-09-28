/*
 * SPDX-FileCopyrightText: 2015, microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.wearable;

import org.microg.wearable.proto.MessagePiece;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class SocketWearableConnection extends WearableConnection {
    private static final int MAX_PIECE_SIZE = 20 * 1024 * 1024;
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;

    public SocketWearableConnection(Socket socket, Listener listener) throws IOException {
        super(listener);
        this.socket = socket;
        this.input = new DataInputStream(socket.getInputStream());
        this.output = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    protected synchronized void writeMessagePiece(MessagePiece piece) throws IOException {
        byte[] bytes = piece.encode();
        output.writeInt(bytes.length);
        output.write(bytes);
        output.flush();
    }

    @Override
    protected MessagePiece readMessagePiece() throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > MAX_PIECE_SIZE) {
            throw new IOException("Piece size " + length + " outside allowed range");
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return MessagePiece.ADAPTER.decode(bytes);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
