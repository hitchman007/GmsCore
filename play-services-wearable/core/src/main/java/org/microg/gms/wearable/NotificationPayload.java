/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Stable encoding for notification events sent through the Wearable Message API.
 *
 * <p>The format is based on the public compatibility contract documented in
 * microg/GmsCore#3286. This implementation is independently written against
 * that documented contract and current GmsCore APIs.</p>
 */
public final class NotificationPayload {
    public static final byte TYPE_POSTED = 1;
    public static final byte TYPE_REMOVED = 2;
    private static final int MAX_ACTIONS = 8;
    private static final int MAX_UTF_CHARS = 8192;

    private NotificationPayload() {
    }

    public static byte[] encodePosted(
            int uid,
            String packageName,
            String key,
            String title,
            String text,
            long timestamp,
            List<String> actionTitles
    ) {
        List<String> actions = actionTitles != null ? actionTitles : Collections.<String>emptyList();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(TYPE_POSTED);
            out.writeInt(uid);
            out.writeUTF(safe(packageName));
            out.writeUTF(safe(key));
            out.writeUTF(safe(title));
            out.writeUTF(safe(text));
            out.writeLong(timestamp);
            int count = Math.min(actions.size(), MAX_ACTIONS);
            out.writeInt(count);
            for (int i = 0; i < count; i++) {
                out.writeUTF(safe(actions.get(i)));
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected in-memory notification encoding failure", e);
        }
    }

    public static byte[] encodeRemoved(int uid, String key) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(TYPE_REMOVED);
            out.writeInt(uid);
            out.writeUTF(safe(key));
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected in-memory notification removal encoding failure", e);
        }
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.length() <= MAX_UTF_CHARS ? value : value.substring(0, MAX_UTF_CHARS);
    }
}
