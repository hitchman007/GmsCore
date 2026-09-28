/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks notifications forwarded to a wearable peer so ANCS-style positive
 * and negative actions can be mapped back to the live Android notification.
 */
public final class NotificationBridge {
    private static final String TAG = "GmsWearNotifBridge";
    public static final String NOTIFICATION_PATH = "/wearable/notification";

    public interface DismissHandler {
        void dismiss(StatusBarNotification notification);
    }

    private static final Map<Integer, StatusBarNotification> ACTIVE = new ConcurrentHashMap<>();
    private static volatile DismissHandler dismissHandler;

    private NotificationBridge() {
    }

    public static void remember(int uid, StatusBarNotification notification) {
        if (notification != null) ACTIVE.put(uid, notification);
    }

    public static void forget(int uid) {
        ACTIVE.remove(uid);
    }

    public static void setDismissHandler(DismissHandler handler) {
        dismissHandler = handler;
    }

    public static void clearDismissHandler(DismissHandler handler) {
        if (dismissHandler == handler) dismissHandler = null;
    }

    public static boolean doPositiveAction(Context context, int uid) {
        StatusBarNotification sbn = ACTIVE.get(uid);
        if (sbn == null) return false;
        Notification notification = sbn.getNotification();
        if (notification == null) return false;

        PendingIntent pendingIntent = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && notification.actions != null
                && notification.actions.length > 0) {
            pendingIntent = notification.actions[0].actionIntent;
        }
        if (pendingIntent == null) pendingIntent = notification.contentIntent;
        if (pendingIntent == null) return false;

        try {
            pendingIntent.send();
            return true;
        } catch (PendingIntent.CanceledException e) {
            Log.w(TAG, "Notification positive action was cancelled for uid=" + uid, e);
            return false;
        }
    }

    public static boolean doNegativeAction(Context context, int uid) {
        StatusBarNotification sbn = ACTIVE.remove(uid);
        if (sbn == null) return false;

        DismissHandler handler = dismissHandler;
        if (handler != null) {
            handler.dismiss(sbn);
            return true;
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return false;
        manager.cancel(sbn.getTag(), sbn.getId());
        return true;
    }
}
