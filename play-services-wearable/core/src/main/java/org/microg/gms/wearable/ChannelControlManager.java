/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.wearable;

import android.util.Log;

import com.google.android.gms.wearable.ChannelApi;
import com.google.android.gms.wearable.internal.ChannelEventParcelable;
import com.google.android.gms.wearable.internal.ChannelParcelable;
import com.google.android.gms.wearable.internal.CloseChannelResponse;
import com.google.android.gms.wearable.internal.IWearableCallbacks;
import com.google.android.gms.wearable.internal.OpenChannelResponse;

import org.microg.gms.common.PackageUtils;
import org.microg.wearable.WearableConnection;
import org.microg.wearable.proto.ChannelControlRequest;
import org.microg.wearable.proto.ChannelRequest;
import org.microg.wearable.proto.Request;
import org.microg.wearable.proto.RootMessage;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
final class ChannelControlManager {
    private static final String TAG = "GmsWearChannel";

    static final int STATUS_SUCCESS = 0;
    static final int STATUS_INTERNAL_ERROR = 8;
    static final int STATUS_NOT_CONNECTED = 13;
    static final int STATUS_NOT_FOUND = 10004;

    private static final int CONTROL_OPEN = 1;
    private static final int CONTROL_OPEN_ACK = 2;
    private static final int CONTROL_CLOSE = 3;

    private static final int EVENT_OPENED = 1;
    private static final int EVENT_CLOSED = 2;
    private static final long OPEN_TIMEOUT_MS = 15_000L;

    private static final int STATE_OPEN_SENT = 1;
    private static final int STATE_ESTABLISHED = 2;
    private static final int STATE_CLOSED = 3;

    private final WearableImpl wearable;
    private final AtomicLong nextChannelId =
            new AtomicLong(Math.max(1L, System.currentTimeMillis()));
    private final Map<String, ChannelRecord> byToken = new ConcurrentHashMap<>();
    private final Map<String, ChannelRecord> byWireId = new ConcurrentHashMap<>();

    ChannelControlManager(WearableImpl wearable) {
        this.wearable = wearable;
    }
    void openChannel(String packageName, String nodeId, String path, IWearableCallbacks callbacks) {
        if (packageName == null || nodeId == null || path == null || path.isEmpty()) {
            sendOpenResult(callbacks, STATUS_INTERNAL_ERROR, null);
            return;
        }

        WearableConnection connection = wearable.getActiveConnection(nodeId);
        if (connection == null) {
            sendOpenResult(callbacks, STATUS_NOT_CONNECTED, null);
            return;
        }

        long channelId = nextPositiveChannelId();
        String token = "chl-" + UUID.randomUUID();
        String signature = safeSignatureDigest(packageName);
        ChannelRecord record = new ChannelRecord(
                token, nodeId, path, packageName, signature, channelId, true, callbacks);
        byToken.put(token, record);
        byWireId.put(wireKey(nodeId, channelId), record);

        try {
            connection.writeMessage(buildControlMessage(record, CONTROL_OPEN, 0));
        } catch (IOException e) {
            Log.w(TAG, "Unable to send channel OPEN", e);
            remove(record);
            sendOpenResult(callbacks, STATUS_NOT_CONNECTED, null);
            return;
        }

        if (wearable.networkHandler != null) {
            wearable.networkHandler.postDelayed(() -> {
                ChannelRecord current = byToken.get(token);
                if (current == record && current.state == STATE_OPEN_SENT) {
                    Log.w(TAG, "Channel OPEN timed out: " + token);
                    remove(current);
                    sendOpenResult(current.openCallbacks, STATUS_NOT_CONNECTED, null);
                }
            }, OPEN_TIMEOUT_MS);
        }
    }
    void closeChannel(String token, int errorCode, IWearableCallbacks callbacks) {
        ChannelRecord record = byToken.get(token);
        if (record == null) {
            sendCloseResult(callbacks, STATUS_NOT_FOUND);
            return;
        }

        WearableConnection connection = wearable.getActiveConnection(record.nodeId);
        int status = STATUS_SUCCESS;
        if (connection != null) {
            try {
                connection.writeMessage(buildControlMessage(record, CONTROL_CLOSE, errorCode));
            } catch (IOException e) {
                Log.w(TAG, "Unable to send channel CLOSE", e);
                status = STATUS_NOT_CONNECTED;
            }
        } else {
            status = STATUS_NOT_CONNECTED;
        }

        remove(record);
        record.state = STATE_CLOSED;
        wearable.sendChannelEvent(record.packageName,
                buildEvent(record, EVENT_CLOSED,
                        ChannelApi.ChannelListener.CLOSE_REASON_LOCAL_CLOSE, errorCode));
        sendCloseResult(callbacks, status);
    }

    void onChannelRequest(WearableConnection connection, String sourceNodeId, Request request) {
        if (connection == null || sourceNodeId == null || request == null || request.request == null) {
            return;
        }
        ChannelControlRequest control = request.request.channelControlRequest;
        if (control == null || control.type == null || control.channelId == null) return;

        switch (control.type) {
            case CONTROL_OPEN:
                handleRemoteOpen(connection, sourceNodeId, control);
                break;
            case CONTROL_OPEN_ACK:
                handleOpenAck(sourceNodeId, control);
                break;
            case CONTROL_CLOSE:
                handleRemoteClose(sourceNodeId, control);
                break;
            default:
                Log.w(TAG, "Unknown channel control type " + control.type);
        }
    }
    private void handleRemoteOpen(WearableConnection connection, String sourceNodeId,
                                  ChannelControlRequest control) {
        if (control.packageName == null || control.path == null || control.path.isEmpty()) {
            Log.w(TAG, "Ignoring malformed remote channel OPEN");
            return;
        }

        String key = wireKey(sourceNodeId, control.channelId);
        ChannelRecord existing = byWireId.get(key);
        if (existing != null) {
            try {
                connection.writeMessage(buildControlMessage(existing, CONTROL_OPEN_ACK, 0));
            } catch (IOException e) {
                Log.w(TAG, "Unable to re-ACK duplicate channel OPEN", e);
            }
            return;
        }

        ChannelRecord record = new ChannelRecord(
                "chl-" + UUID.randomUUID(),
                sourceNodeId,
                control.path,
                control.packageName,
                control.signatureDigest != null ? control.signatureDigest : "",
                control.channelId,
                false,
                null);
        record.state = STATE_ESTABLISHED;
        byToken.put(record.token, record);
        byWireId.put(key, record);

        try {
            connection.writeMessage(buildControlMessage(record, CONTROL_OPEN_ACK, 0));
        } catch (IOException e) {
            Log.w(TAG, "Unable to send channel OPEN_ACK", e);
            remove(record);
            return;
        }

        wearable.sendChannelEvent(record.packageName,
                buildEvent(record, EVENT_OPENED, 0, 0));
    }
    private void handleOpenAck(String sourceNodeId, ChannelControlRequest control) {
        ChannelRecord record = byWireId.get(wireKey(sourceNodeId, control.channelId));
        if (record == null || !record.localOpener) {
            Log.w(TAG, "OPEN_ACK for unknown channel " + control.channelId);
            return;
        }
        if (record.state == STATE_ESTABLISHED) return;
        if (record.state != STATE_OPEN_SENT) {
            Log.w(TAG, "OPEN_ACK in state " + record.state);
            return;
        }

        record.state = STATE_ESTABLISHED;
        IWearableCallbacks callbacks = record.openCallbacks;
        record.openCallbacks = null;
        sendOpenResult(callbacks, STATUS_SUCCESS, record.toParcelable());
    }

    private void handleRemoteClose(String sourceNodeId, ChannelControlRequest control) {
        ChannelRecord record = byWireId.get(wireKey(sourceNodeId, control.channelId));
        if (record == null) {
            Log.d(TAG, "CLOSE for unknown channel " + control.channelId);
            return;
        }

        int errorCode = control.closeErrorCode != null ? control.closeErrorCode : 0;
        remove(record);
        record.state = STATE_CLOSED;
        wearable.sendChannelEvent(record.packageName,
                buildEvent(record, EVENT_CLOSED,
                        ChannelApi.ChannelListener.CLOSE_REASON_REMOTE_CLOSE, errorCode));
        if (record.openCallbacks != null) {
            sendOpenResult(record.openCallbacks, STATUS_NOT_CONNECTED, null);
            record.openCallbacks = null;
        }
    }

    void onNodeDisconnected(String nodeId) {
        for (ChannelRecord record : byToken.values().toArray(new ChannelRecord[0])) {
            if (!nodeId.equals(record.nodeId)) continue;
            remove(record);
            record.state = STATE_CLOSED;
            wearable.sendChannelEvent(record.packageName,
                    buildEvent(record, EVENT_CLOSED,
                            ChannelApi.ChannelListener.CLOSE_REASON_DISCONNECTED, 0));
            if (record.openCallbacks != null) {
                sendOpenResult(record.openCallbacks, STATUS_NOT_CONNECTED, null);
                record.openCallbacks = null;
            }
        }
    }
    private RootMessage buildControlMessage(ChannelRecord record, int type, int errorCode) {
        ChannelControlRequest.Builder control = new ChannelControlRequest.Builder()
                .type(type)
                .channelId(record.channelId)
                .fromChannelOperator(record.localOpener)
                .packageName(record.packageName)
                .signatureDigest(record.signatureDigest);
        if (type == CONTROL_OPEN || type == CONTROL_OPEN_ACK) {
            control.path(record.path);
        }
        if (type == CONTROL_CLOSE) {
            control.closeErrorCode(errorCode);
        }

        ChannelRequest channelRequest = new ChannelRequest.Builder()
                .channelControlRequest(control.build())
                .version(1)
                .build();

        Request request = new Request.Builder()
                .targetNodeId(record.nodeId)
                .sourceNodeId(wearable.getLocalNodeId())
                .packageName(record.packageName)
                .signatureDigest(record.signatureDigest)
                .request(channelRequest)
                .requiresResponse(false)
                .build();

        return new RootMessage.Builder().channelRequest(request).build();
    }

    private static ChannelEventParcelable buildEvent(ChannelRecord record, int eventType,
                                                     int closeReason, int errorCode) {
        ChannelEventParcelable event = new ChannelEventParcelable();
        event.channel = record.toParcelable();
        event.eventType = eventType;
        event.closeReason = closeReason;
        event.appSpecificErrorCode = errorCode;
        return event;
    }
    private long nextPositiveChannelId() {
        long value = nextChannelId.incrementAndGet();
        if (value < 0) {
            nextChannelId.set(1L);
            return 1L;
        }
        return value;
    }

    private String safeSignatureDigest(String packageName) {
        try {
            String digest = PackageUtils.firstSignatureDigest(wearable.getContext(), packageName);
            return digest != null ? digest : "";
        } catch (Exception e) {
            Log.w(TAG, "Unable to resolve signature for " + packageName, e);
            return "";
        }
    }

    private static String wireKey(String nodeId, long channelId) {
        return nodeId + ":" + channelId;
    }

    private void remove(ChannelRecord record) {
        if (byToken.get(record.token) == record) {
            byToken.remove(record.token);
        }
        String key = wireKey(record.nodeId, record.channelId);
        if (byWireId.get(key) == record) {
            byWireId.remove(key);
        }
    }

    private static void sendOpenResult(IWearableCallbacks callbacks, int status,
                                       ChannelParcelable channel) {
        if (callbacks == null) return;
        try {
            callbacks.onOpenChannelResponse(new OpenChannelResponse(status, channel));
        } catch (Exception e) {
            Log.w(TAG, "Unable to deliver openChannel result", e);
        }
    }

    private static void sendCloseResult(IWearableCallbacks callbacks, int status) {
        if (callbacks == null) return;
        try {
            callbacks.onCloseChannelResponse(new CloseChannelResponse(status));
        } catch (Exception e) {
            Log.w(TAG, "Unable to deliver closeChannel result", e);
        }
    }
    private static final class ChannelRecord {
        final String token;
        final String nodeId;
        final String path;
        final String packageName;
        final String signatureDigest;
        final long channelId;
        final boolean localOpener;
        volatile int state = STATE_OPEN_SENT;
        volatile IWearableCallbacks openCallbacks;

        ChannelRecord(String token, String nodeId, String path, String packageName,
                      String signatureDigest, long channelId, boolean localOpener,
                      IWearableCallbacks openCallbacks) {
            this.token = token;
            this.nodeId = nodeId;
            this.path = path;
            this.packageName = packageName;
            this.signatureDigest = signatureDigest;
            this.channelId = channelId;
            this.localOpener = localOpener;
            this.openCallbacks = openCallbacks;
        }

        ChannelParcelable toParcelable() {
            return new ChannelParcelable(token, nodeId, path);
        }
    }
}