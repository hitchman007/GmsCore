/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import static org.junit.Assert.assertEquals;

import com.google.android.gms.wearable.ConnectionConfiguration;

import org.junit.Test;

import java.lang.reflect.Field;

public class WearableBinderCompatibilityTest {
    @Test
    public void modernWearableBinderTransactionsMatchObservedSurface() throws Exception {
        assertTransaction(IWearableService.Stub.class, "TRANSACTION_getNodeId", 66);
        assertTransaction(IWearableService.Stub.class, "TRANSACTION_updateConnectionStrategy", 71);
        assertTransaction(IWearableService.Stub.class, "TRANSACTION_getRelatedConfigs", 72);
        assertTransaction(IWearableService.Stub.class, "TRANSACTION_updateConfig", 73);
        assertTransaction(IWearableCallbacks.Stub.class, "TRANSACTION_onGetNodeIdResponse", 38);
    }

    @Test
    public void modernConnectionConfigurationFieldsRemainAvailable() {
        ConnectionConfiguration config =
                new ConnectionConfiguration("server", "00:11:22:33:44:55", 1, 2, true);
        config.packageName = "com.example.companion";
        config.connectionRetryStrategy = 3;

        assertEquals("com.example.companion", config.packageName);
        assertEquals(3, config.connectionRetryStrategy);
    }

    @Test
    public void getNodeIdResponseCarriesStatusAndNodeId() {
        GetNodeIdResponse response = new GetNodeIdResponse(0, "peer-node");

        assertEquals(0, response.statusCode);
        assertEquals("peer-node", response.nodeId);
    }

    private static void assertTransaction(Class<?> stubClass, String fieldName, int expected)
            throws Exception {
        Field field = stubClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        assertEquals(expected, field.getInt(null));
    }
}
