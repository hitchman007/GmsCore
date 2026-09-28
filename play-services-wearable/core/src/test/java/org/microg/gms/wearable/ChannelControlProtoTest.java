/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.wearable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.microg.wearable.proto.ChannelControlRequest;
import org.microg.wearable.proto.ChannelDataAckRequest;
import org.microg.wearable.proto.ChannelDataHeader;
import org.microg.wearable.proto.ChannelDataRequest;
import org.microg.wearable.proto.ChannelRequest;
import org.microg.wearable.proto.Request;
import org.microg.wearable.proto.RootMessage;

public class ChannelControlProtoTest {
    @Test
    public void openControlRoundTripsThroughWire() throws Exception {
        ChannelControlRequest control = new ChannelControlRequest.Builder()
                .type(1)
                .channelId(123456789L)
                .fromChannelOperator(true)
                .packageName("com.example.app")
                .signatureDigest("abcd")
                .path("/sync")
                .build();
        RootMessage root = new RootMessage.Builder().channelRequest(
                new Request.Builder().request(
                        new ChannelRequest.Builder().channelControlRequest(control).version(1).build()
                ).build()
        ).build();

        RootMessage decoded = RootMessage.ADAPTER.decode(RootMessage.ADAPTER.encode(root));
        ChannelControlRequest result = decoded.channelRequest.request.channelControlRequest;
        assertEquals(Integer.valueOf(1), result.type);
        assertEquals(Long.valueOf(123456789L), result.channelId);
        assertEquals("/sync", result.path);
        assertTrue(result.fromChannelOperator);
    }

    @Test
    public void channelDataAndAckRoundTripThroughWire() throws Exception {
        ChannelDataHeader header = new ChannelDataHeader.Builder()
                .channelId(77L)
                .fromChannelOperator(true)
                .requestId(9L)
                .build();
        ChannelDataRequest data = new ChannelDataRequest.Builder()
                .header(header)
                .payload(okio.ByteString.encodeUtf8("payload"))
                .finalMessage(false)
                .build();
        RootMessage dataRoot = new RootMessage.Builder().channelRequest(
                new Request.Builder().request(
                        new ChannelRequest.Builder().channelDataRequest(data).version(1).build()
                ).build()
        ).build();

        RootMessage decodedData = RootMessage.ADAPTER.decode(RootMessage.ADAPTER.encode(dataRoot));
        ChannelDataRequest resultData = decodedData.channelRequest.request.channelDataRequest;
        assertEquals(Long.valueOf(77L), resultData.header.channelId);
        assertEquals(Long.valueOf(9L), resultData.header.requestId);
        assertEquals("payload", resultData.payload.utf8());

        ChannelDataAckRequest ack = new ChannelDataAckRequest.Builder()
                .header(header)
                .finalMessage(true)
                .build();
        RootMessage ackRoot = new RootMessage.Builder().channelRequest(
                new Request.Builder().request(
                        new ChannelRequest.Builder().channelDataAckRequest(ack).version(1).build()
                ).build()
        ).build();

        RootMessage decodedAck = RootMessage.ADAPTER.decode(RootMessage.ADAPTER.encode(ackRoot));
        ChannelDataAckRequest resultAck = decodedAck.channelRequest.request.channelDataAckRequest;
        assertEquals(Long.valueOf(77L), resultAck.header.channelId);
        assertEquals(Long.valueOf(9L), resultAck.header.requestId);
        assertTrue(resultAck.finalMessage);
    }
}