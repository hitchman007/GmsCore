/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.wearable;

import org.junit.Test;
import org.microg.wearable.proto.Connect;
import org.microg.wearable.proto.Request;
import org.microg.wearable.proto.RootMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import okio.ByteString;
import static org.junit.Assert.*;

public class WireProtocolTest {
    private static byte[] hex(String value) {
        return ByteString.decodeHex(value).toByteArray();
    }
    @Test public void legacyConnectWireFormatIsUnchanged() throws Exception {
        byte[] fixture = hex("3a080a066c6567616379");
        assertEquals("legacy", RootMessage.ADAPTER.decode(fixture).connect.id);
        RootMessage message = new RootMessage.Builder().connect(
                new Connect.Builder().id("legacy").build()).build();
        assertArrayEquals(fixture, RootMessage.ADAPTER.encode(message));
    }
    @Test public void modernRpcFieldsDecodeWithoutLosingUnknownFields() throws Exception {
        // Tags 1, 13, 14 and unknown tag 100. These are wire fixtures, not device captures.
        byte[] fixture = hex("082a68017007a00601");
        Request request = Request.ADAPTER.decode(fixture);
        assertEquals(Integer.valueOf(42), request.requestId);
        assertEquals(Boolean.TRUE, request.requiresResponse);
        assertEquals(Integer.valueOf(7), request.senderRequestId);
        assertArrayEquals(fixture, Request.ADAPTER.encode(request));
    }
    @Test public void framedLegacyMessageUsesModernWireRuntime() throws Exception {
        SocketWearableConnection connection = connection(hex(
                "000000100a0a3a080a066c656761637918012001"));
        assertEquals("legacy", connection.readMessage().connect.id);
    }
    @Test public void negativeFrameLengthIsRejected() throws Exception {
        assertThrows(IOException.class, () -> connection(hex("ffffffff")).readMessagePiece());
    }
    @Test public void oversizedFrameIsRejectedBeforeAllocation() throws Exception {
        assertThrows(IOException.class, () -> connection(hex("01400001")).readMessagePiece());
    }
    @Test public void truncatedFrameIsRejected() throws Exception {
        assertThrows(IOException.class, () -> connection(hex("000000100a")).readMessagePiece());
    }
    private static SocketWearableConnection connection(final byte[] input) throws IOException {
        Socket socket = new Socket() {
            @Override public InputStream getInputStream() {
                return new ByteArrayInputStream(input);
            }
            @Override public OutputStream getOutputStream() {
                return new ByteArrayOutputStream();
            }
        };
        return new SocketWearableConnection(socket, new WearableConnection.Listener() {
            @Override public void onConnected(WearableConnection connection) { }
            @Override public void onMessage(WearableConnection connection, RootMessage message) { }
            @Override public void onDisconnected() { }
        });
    }
}
