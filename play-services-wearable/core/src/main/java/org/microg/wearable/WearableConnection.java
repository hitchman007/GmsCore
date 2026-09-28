/*
 * SPDX-FileCopyrightText: 2015, microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.wearable;

import org.microg.wearable.proto.MessagePiece;
import org.microg.wearable.proto.RootMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okio.ByteString;

public abstract class WearableConnection implements Runnable {
    private static final String B64ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private final HashMap<Integer, List<MessagePiece>> piecesQueues = new HashMap<>();
    private final Listener listener;

    public WearableConnection(Listener listener) {
        this.listener = listener;
    }

    public static String base64encode(byte[] bytes) {
        int paddingCount = (3 - (bytes.length % 3)) % 3;
        byte[] padded = new byte[bytes.length + paddingCount];
        System.arraycopy(bytes, 0, padded, 0, bytes.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i += 3) {
            int j = ((padded[i] & 0xff) << 16) + ((padded[i + 1] & 0xff) << 8) + (padded[i + 2] & 0xff);
            sb.append(B64ALPHABET.charAt((j >> 18) & 0x3f))
                    .append(B64ALPHABET.charAt((j >> 12) & 0x3f))
                    .append(B64ALPHABET.charAt((j >> 6) & 0x3f))
                    .append(B64ALPHABET.charAt(j & 0x3f));
        }
        return sb.substring(0, sb.length() - paddingCount);
    }

    public static String calculateDigest(byte[] bytes) {
        try {
            return base64encode(MessageDigest.getInstance("SHA1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA1 not supported => platform not supported");
        }
    }

    public void writeMessage(RootMessage message) throws IOException {
        byte[] bytes = message.encode();
        writeMessagePiece(new MessagePiece.Builder()
                .data(ByteString.of(bytes))
                .digest(calculateDigest(bytes))
                .thisPiece(1)
                .totalPieces(1)
                .build());
    }

    protected abstract void writeMessagePiece(MessagePiece piece) throws IOException;

    protected RootMessage readMessage() throws IOException {
        while (true) {
            MessagePiece piece = readMessagePiece();
            int totalPieces = piece.totalPieces != null ? piece.totalPieces : 1;
            int thisPiece = piece.thisPiece != null ? piece.thisPiece : 1;
            if (piece.data == null) throw new IOException("Wearable message piece has no data");

            if (totalPieces == 1) {
                return RootMessage.ADAPTER.decode(piece.data);
            }

            if (thisPiece == 1) {
                List<MessagePiece> oldQueue = piecesQueues.get(piece.queueId);
                String oldDigest = oldQueue != null && !oldQueue.isEmpty() ? oldQueue.get(0).digest : null;
                List<MessagePiece> queue = new ArrayList<>(totalPieces);
                queue.add(piece);
                piecesQueues.put(piece.queueId, queue);
                if (oldDigest != null) {
                    throw new IOException("Could not finish message of digest " + oldDigest + "; queue reused");
                }
                continue;
            }

            List<MessagePiece> queue = piecesQueues.get(piece.queueId);
            if (queue == null || queue.isEmpty() || !safeEquals(queue.get(0).digest, piece.digest)) {
                throw new IOException("Received piece " + thisPiece + " before a matching first piece");
            }
            if (queue.size() + 1 != thisPiece) {
                throw new IOException("Received piece " + thisPiece + " but expected " + (queue.size() + 1));
            }
            queue.add(piece);
            if (thisPiece == totalPieces) {
                piecesQueues.remove(piece.queueId);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                for (MessagePiece messagePiece : queue) {
                    if (messagePiece.data == null) throw new IOException("Wearable message piece has no data");
                    bos.write(messagePiece.data.toByteArray());
                }
                byte[] bytes = bos.toByteArray();
                String digest = calculateDigest(bytes);
                if (!safeEquals(digest, piece.digest)) {
                    throw new IOException("Merged pieces have digest " + digest + ", expected " + piece.digest);
                }
                return RootMessage.ADAPTER.decode(bytes);
            }
        }
    }

    private static boolean safeEquals(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    protected abstract MessagePiece readMessagePiece() throws IOException;

    public abstract void close() throws IOException;

    @Override
    public void run() {
        try {
            listener.onConnected(this);
            RootMessage message;
            while ((message = readMessage()) != null) {
                listener.onMessage(this, message);
            }
        } catch (IOException ignored) {
        } finally {
            listener.onDisconnected();
        }
    }

    public interface Listener {
        void onConnected(WearableConnection connection);
        void onMessage(WearableConnection connection, RootMessage message);
        void onDisconnected();
    }
}
