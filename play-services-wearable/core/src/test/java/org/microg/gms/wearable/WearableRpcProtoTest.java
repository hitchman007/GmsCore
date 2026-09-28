/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.wearable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.microg.wearable.proto.Request;

public class WearableRpcProtoTest {
    @Test
    public void modernRpcCorrelationFieldsAreGenerated() {
        Request request = new Request.Builder()
                .requestId(41)
                .path("/clockworkSetupWizard/frp_status_request")
                .requiresResponse(true)
                .senderRequestId(17)
                .build();

        assertEquals(Integer.valueOf(41), request.requestId);
        assertEquals(Integer.valueOf(17), request.senderRequestId);
        assertTrue(request.requiresResponse);
        assertFalse(request.path.isEmpty());
    }
}
