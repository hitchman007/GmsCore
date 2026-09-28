/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable.bridge;

import junit.framework.TestCase;

import org.microg.gms.wearable.MediaControlCommand;

public class MediaControlCommandTest extends TestCase {
    public void testKnownCommands() {
        assertEquals(MediaControlCommand.PLAY, MediaControlCommand.decode(new byte[]{1}));
        assertEquals(MediaControlCommand.PAUSE, MediaControlCommand.decode(new byte[]{2}));
        assertEquals(MediaControlCommand.NEXT, MediaControlCommand.decode(new byte[]{3}));
        assertEquals(MediaControlCommand.PREVIOUS, MediaControlCommand.decode(new byte[]{4}));
        assertEquals(MediaControlCommand.VOLUME_UP, MediaControlCommand.decode(new byte[]{5}));
        assertEquals(MediaControlCommand.VOLUME_DOWN, MediaControlCommand.decode(new byte[]{6}));
    }

    public void testInvalidCommandsFailClosed() {
        assertEquals(MediaControlCommand.INVALID, MediaControlCommand.decode(null));
        assertEquals(MediaControlCommand.INVALID, MediaControlCommand.decode(new byte[0]));
        assertEquals(MediaControlCommand.INVALID, MediaControlCommand.decode(new byte[]{0}));
        assertEquals(MediaControlCommand.INVALID, MediaControlCommand.decode(new byte[]{7}));
    }
}
