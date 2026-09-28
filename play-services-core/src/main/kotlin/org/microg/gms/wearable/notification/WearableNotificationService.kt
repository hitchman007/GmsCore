/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable.notification

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.microg.gms.wearable.MediaBridge
import org.microg.gms.wearable.NotificationBridge
import org.microg.gms.wearable.NotificationPayload
import org.microg.gms.wearable.WearableService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bridges phone notifications onto the already-established Wearable Message API.
 *
 * The wire contract is documented by the public compatibility work in
 * microg/GmsCore#3286. This service is independently implemented against that
 * contract and the current GmsCore APIs.
 */
class WearableNotificationService : NotificationListenerService(), NotificationBridge.DismissHandler {
    private val nextUid = AtomicInteger(1)
    private val keyToUid = ConcurrentHashMap<String, Int>()

    override fun onCreate() {
        super.onCreate()
        NotificationBridge.setDismissHandler(this)
    }

    override fun onDestroy() {
        NotificationBridge.clearDismissHandler(this)
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        WearableService.getInstance()?.let { MediaBridge.start(this, it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return

        val key = stableKey(sbn)
        val uid = keyToUid.getOrPut(key) { nextUid.getAndIncrement() }
        NotificationBridge.remember(uid, sbn)

        val notification = sbn.notification ?: return
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (
            extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras?.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty()
        val actions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            notification.actions?.map { it.title?.toString().orEmpty() }.orEmpty()
        } else {
            emptyList()
        }

        val payload = NotificationPayload.encodePosted(
            uid,
            sbn.packageName,
            key,
            title,
            text,
            sbn.postTime,
            actions
        )
        sendToWearable(payload)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val key = stableKey(sbn)
        val uid = keyToUid.remove(key) ?: return
        NotificationBridge.forget(uid)
        sendToWearable(NotificationPayload.encodeRemoved(uid, key))
    }

    override fun dismiss(notification: StatusBarNotification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cancelNotification(notification.key)
        } else {
            @Suppress("DEPRECATION")
            cancelNotification(notification.packageName, notification.tag, notification.id)
        }
    }

    private fun sendToWearable(payload: ByteArray) {
        val wearable = WearableService.getInstance() ?: return
        for (nodeId in wearable.connectedNodeIds) {
            wearable.sendMessage(packageName, nodeId, NotificationBridge.NOTIFICATION_PATH, payload)
        }
    }

    private fun stableKey(sbn: StatusBarNotification): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            sbn.key
        } else {
            listOf(sbn.packageName, sbn.id.toString(), sbn.tag.orEmpty()).joinToString("|")
        }
    }
}
