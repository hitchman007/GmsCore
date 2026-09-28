/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import junit.framework.TestCase;

/**
 * Repository-level contract checks for the bridge layer.
 *
 * This is deliberately not a physical-device test. Physical WearOS validation
 * remains a separate release/submission gate.
 */
public class WearableConnectionIntegrationTest extends TestCase {
    public void testBridgePathsUseWearableMessageNamespace() {
        assertEquals("/wearable/notification", NotificationBridge.NOTIFICATION_PATH);
        assertEquals("/wearable/media", MediaBridge.MEDIA_PATH);
        assertEquals("/wearable/media/command", MediaBridge.MEDIA_COMMAND_PATH);
    }

    public void testMediaCommandContractIsStable() {
        assertEquals(MediaControlCommand.PLAY, MediaControlCommand.decode(new byte[]{1}));
        assertEquals(MediaControlCommand.VOLUME_DOWN, MediaControlCommand.decode(new byte[]{6}));
        assertEquals(MediaControlCommand.INVALID, MediaControlCommand.decode(new byte[]{99}));
    }
}
