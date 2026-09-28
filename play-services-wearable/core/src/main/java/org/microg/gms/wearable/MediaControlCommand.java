/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

/**
 * Parser for the compact media-control command payload used on
 * {@link MediaBridge#MEDIA_COMMAND_PATH}.
 */
public final class MediaControlCommand {
    public static final int INVALID = 0;
    public static final int PLAY = 1;
    public static final int PAUSE = 2;
    public static final int NEXT = 3;
    public static final int PREVIOUS = 4;
    public static final int VOLUME_UP = 5;
    public static final int VOLUME_DOWN = 6;

    private MediaControlCommand() {
    }

    public static int decode(byte[] payload) {
        if (payload == null || payload.length == 0) return INVALID;
        int command = payload[0] & 0xff;
        return command >= PLAY && command <= VOLUME_DOWN ? command : INVALID;
    }
}
