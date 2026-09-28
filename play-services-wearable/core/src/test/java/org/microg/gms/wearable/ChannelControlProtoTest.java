/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.wearable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.microg.wearable.proto.ChannelControlRequest;
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
}