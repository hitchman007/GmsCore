/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.UUID;

public class BluetoothConnectionThreadTest {
    @Test
    public void wearableBtUuidMatchesKnownService() {
        assertEquals(
                UUID.fromString("5e8945b0-9525-11e3-a5e2-0800200c9a66"),
                BluetoothConnectionThread.WEARABLE_BT_UUID);
    }

    @Test
    public void flowUuidsMatchKnownServices() {
        assertEquals(
                UUID.fromString("fafbdd20-83f0-4389-addf-917ac9dae5b2"),
                BluetoothConnectionThread.FLOW_UUID);
        assertEquals(
                UUID.fromString("6a1eafb1-61c0-42a0-8bb0-a336fb1c3f00"),
                BluetoothConnectionThread.FLOW15_UUID);
        assertNotEquals(BluetoothConnectionThread.FLOW_UUID, BluetoothConnectionThread.FLOW15_UUID);
        assertNotEquals(BluetoothConnectionThread.WEARABLE_BT_UUID, BluetoothConnectionThread.FLOW_UUID);
    }
}
