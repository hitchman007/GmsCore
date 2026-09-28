/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable.bridge;

import junit.framework.TestCase;

import org.microg.gms.wearable.NotificationPayload;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Arrays;

public class NotificationPayloadTest extends TestCase {
    public void testPostedNotificationEncoding() throws Exception {
        byte[] encoded = NotificationPayload.encodePosted(
                42,
                "com.example.mail",
                "mail|7",
                "Subject",
                "Message body",
                123456789L,
                Arrays.asList("Reply", "Archive")
        );

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
        assertEquals(NotificationPayload.TYPE_POSTED, in.readByte());
        assertEquals(42, in.readInt());
        assertEquals("com.example.mail", in.readUTF());
        assertEquals("mail|7", in.readUTF());
        assertEquals("Subject", in.readUTF());
        assertEquals("Message body", in.readUTF());
        assertEquals(123456789L, in.readLong());
        assertEquals(2, in.readInt());
        assertEquals("Reply", in.readUTF());
        assertEquals("Archive", in.readUTF());
        assertEquals(-1, in.read());
    }

    public void testRemovalEncoding() throws Exception {
        byte[] encoded = NotificationPayload.encodeRemoved(9, "key-9");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded));
        assertEquals(NotificationPayload.TYPE_REMOVED, in.readByte());
        assertEquals(9, in.readInt());
        assertEquals("key-9", in.readUTF());
        assertEquals(-1, in.read());
    }
}
