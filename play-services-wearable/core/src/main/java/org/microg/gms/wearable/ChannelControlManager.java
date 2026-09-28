/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.wearable;

import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import com.google.android.gms.wearable.ChannelApi;
import com.google.android.gms.wearable.internal.ChannelEventParcelable;
import com.google.android.gms.wearable.internal.ChannelParcelable;
import com.google.android.gms.wearable.internal.CloseChannelResponse;
import com.google.android.gms.wearable.internal.GetChannelInputStreamResponse;
import com.google.android.gms.wearable.internal.GetChannelOutputStreamResponse;
import com.google.android.gms.wearable.internal.IChannelStreamCallbacks;
import com.google.android.gms.wearable.internal.IWearableCallbacks;
import com.google.android.gms.wearable.internal.OpenChannelResponse;

import org.microg.gms.common.PackageUtils;
import org.microg.wearable.WearableConnection;
import org.microg.wearable.proto.ChannelControlRequest;
import org.microg.wearable.proto.ChannelDataAckRequest;
import org.microg.wearable.proto.ChannelDataHeader;
import org.microg.wearable.proto.ChannelDataRequest;
import org.microg.wearable.proto.ChannelRequest;
import org.microg.wearable.proto.Request;
import org.microg.wearable.proto.RootMessage;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okio.ByteString;
final class ChannelControlManager {
    private static final String TAG = "GmsWearChannel";

    static final int STATUS_SUCCESS = 0;
    static final int STATUS_INTERNAL_ERROR = 8;
    static final int STATUS_NOT_CONNECTED = 13;
    static final int STATUS_NOT_FOUND = 10004;
    static final int STATUS_ALREADY_IN_PROGRESS = 10005;

    private static final int CONTROL_OPEN = 1;
    private static final int CONTROL_OPEN_ACK = 2;
    private static final int CONTROL_CLOSE = 3;

    private static final int EVENT_OPENED = 1;
    private static final int EVENT_CLOSED = 2;
    private static final int EVENT_INPUT_CLOSED = 3;
    private static final int EVENT_OUTPUT_CLOSED = 4;

    private static final long OPEN_TIMEOUT_MS = 15_000L;
    private static final long DATA_ACK_TIMEOUT_MS = 5_000L;
    private static final int DATA_CHUNK_SIZE = 8 * 1024;
    private static final int MAX_INCOMING_CHUNKS = 64;
    private static final int MAX_INCOMING_CHUNK_SIZE = 64 * 1024;

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

        closeStreams(record, ChannelApi.ChannelListener.CLOSE_REASON_LOCAL_CLOSE, errorCode);
        remove(record);
        record.state = STATE_CLOSED;
        wearable.sendChannelEvent(record.packageName,
                buildEvent(record, EVENT_CLOSED,
                        ChannelApi.ChannelListener.CLOSE_REASON_LOCAL_CLOSE, errorCode));
        sendCloseResult(callbacks, status);
    }

    void getInputStream(String token, IWearableCallbacks callbacks,
                        IChannelStreamCallbacks streamCallbacks) {
        ChannelRecord record = byToken.get(token);
        if (record == null) {
            sendInputStreamResult(callbacks, STATUS_NOT_FOUND, null);
            return;
        }
        if (record.state != STATE_ESTABLISHED) {
            sendInputStreamResult(callbacks, STATUS_NOT_CONNECTED, null);
            return;
        }

        ParcelFileDescriptor appEnd;
        synchronized (record.streamLock) {
            if (record.inputWriter != null) {
                sendInputStreamResult(callbacks, STATUS_INTERNAL_ERROR, null);
                return;
            }
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                appEnd = pipe[0];
                record.inputWriter = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
                record.inputCallbacks = streamCallbacks;
                record.inputOpened = true;
                startInputWriter(record);
            } catch (IOException e) {
                Log.w(TAG, "Unable to create input channel pipe", e);
                sendInputStreamResult(callbacks, STATUS_INTERNAL_ERROR, null);
                return;
            }
        }

        sendInputStreamResult(callbacks, STATUS_SUCCESS, appEnd);
        closeQuietly(appEnd);
    }

    void getOutputStream(String token, IWearableCallbacks callbacks,
                         IChannelStreamCallbacks streamCallbacks) {
        ChannelRecord record = byToken.get(token);
        if (record == null) {
            sendOutputStreamResult(callbacks, STATUS_NOT_FOUND, null);
            return;
        }
        if (record.state != STATE_ESTABLISHED) {
            sendOutputStreamResult(callbacks, STATUS_NOT_CONNECTED, null);
            return;
        }

        ParcelFileDescriptor appEnd;
        synchronized (record.streamLock) {
            if (record.outputReader != null) {
                sendOutputStreamResult(callbacks, STATUS_ALREADY_IN_PROGRESS, null);
                return;
            }
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                record.outputReader = new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]);
                appEnd = pipe[1];
                record.outputCallbacks = streamCallbacks;
                record.outputOpened = true;
                startOutputReader(record, 0L, -1L);
            } catch (IOException e) {
                Log.w(TAG, "Unable to create output channel pipe", e);
                sendOutputStreamResult(callbacks, STATUS_INTERNAL_ERROR, null);
                return;
            }
        }

        sendOutputStreamResult(callbacks, STATUS_SUCCESS, appEnd);
        closeQuietly(appEnd);
    }

    int attachInputFile(String token, ParcelFileDescriptor descriptor,
                        IChannelStreamCallbacks streamCallbacks) {
        ChannelRecord record = byToken.get(token);
        if (record == null) {
            closeQuietly(descriptor);
            return STATUS_NOT_FOUND;
        }
        if (record.state != STATE_ESTABLISHED) {
            closeQuietly(descriptor);
            return STATUS_NOT_CONNECTED;
        }
        if (descriptor == null) return STATUS_INTERNAL_ERROR;

        synchronized (record.streamLock) {
            if (record.inputWriter != null) {
                closeQuietly(descriptor);
                return STATUS_ALREADY_IN_PROGRESS;
            }
            record.inputWriter = new ParcelFileDescriptor.AutoCloseOutputStream(descriptor);
            record.inputCallbacks = streamCallbacks;
            record.inputOpened = true;
            startInputWriter(record);
        }
        return STATUS_SUCCESS;
    }

    int attachOutputFile(String token, ParcelFileDescriptor descriptor, long startOffset,
                         long length, IChannelStreamCallbacks streamCallbacks) {
        ChannelRecord record = byToken.get(token);
        if (record == null) {
            closeQuietly(descriptor);
            return STATUS_NOT_FOUND;
        }
        if (record.state != STATE_ESTABLISHED) {
            closeQuietly(descriptor);
            return STATUS_NOT_CONNECTED;
        }
        if (descriptor == null || startOffset < 0 || length < -1) {
            closeQuietly(descriptor);
            return STATUS_INTERNAL_ERROR;
        }

        synchronized (record.streamLock) {
            if (record.outputReader != null) {
                closeQuietly(descriptor);
                return STATUS_ALREADY_IN_PROGRESS;
            }
            record.outputReader = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
            record.outputCallbacks = streamCallbacks;
            record.outputOpened = true;
            startOutputReader(record, startOffset, length);
        }
        return STATUS_SUCCESS;
    }
    void onChannelRequest(WearableConnection connection, String sourceNodeId, Request request) {
        if (connection == null || sourceNodeId == null || request == null || request.request == null) {
            return;
        }

        ChannelRequest channelRequest = request.request;
        ChannelControlRequest control = channelRequest.channelControlRequest;
        if (control != null && control.type != null && control.channelId != null) {
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
            return;
        }

        if (channelRequest.channelDataRequest != null) {
            handleData(sourceNodeId, channelRequest.channelDataRequest);
            return;
        }

        if (channelRequest.channelDataAckRequest != null) {
            handleDataAck(sourceNodeId, channelRequest.channelDataAckRequest);
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
        closeStreams(record, ChannelApi.ChannelListener.CLOSE_REASON_REMOTE_CLOSE, errorCode);
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
            closeStreams(record, ChannelApi.ChannelListener.CLOSE_REASON_DISCONNECTED, 0);
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
    private void handleData(String sourceNodeId, ChannelDataRequest dataRequest) {
        if (dataRequest.header == null || dataRequest.header.channelId == null
                || dataRequest.header.requestId == null) {
            Log.w(TAG, "Ignoring channel data without a complete header");
            return;
        }

        ChannelRecord record = byWireId.get(wireKey(sourceNodeId, dataRequest.header.channelId));
        if (record == null || record.state != STATE_ESTABLISHED) {
            Log.w(TAG, "Data for unknown or closed channel " + dataRequest.header.channelId);
            return;
        }

        boolean expectedRemoteOperator = !record.localOpener;
        if (!Boolean.valueOf(expectedRemoteOperator).equals(dataRequest.header.fromChannelOperator)) {
            Log.w(TAG, "Ignoring channel data with mismatched operator direction");
            return;
        }

        byte[] payload = dataRequest.payload != null
                ? dataRequest.payload.toByteArray() : new byte[0];
        if (payload.length > MAX_INCOMING_CHUNK_SIZE) {
            Log.w(TAG, "Ignoring oversized channel payload: " + payload.length);
            return;
        }

        IncomingChunk chunk = new IncomingChunk(
                payload,
                dataRequest.header.requestId,
                Boolean.TRUE.equals(dataRequest.finalMessage));
        if (!record.incoming.offer(chunk)) {
            Log.w(TAG, "Incoming channel queue full; withholding ACK for backpressure");
        }
    }

    private void handleDataAck(String sourceNodeId, ChannelDataAckRequest ackRequest) {
        if (ackRequest.header == null || ackRequest.header.channelId == null
                || ackRequest.header.requestId == null) {
            return;
        }

        ChannelRecord record = byWireId.get(wireKey(sourceNodeId, ackRequest.header.channelId));
        if (record == null) return;

        CountDownLatch latch = record.pendingAcks.remove(ackRequest.header.requestId);
        if (latch != null) {
            latch.countDown();
        }
    }

    private void startInputWriter(ChannelRecord record) {
        record.inputThread = new Thread(() -> {
            try {
                while (record.state != STATE_CLOSED && !Thread.currentThread().isInterrupted()) {
                    IncomingChunk chunk = record.incoming.take();
                    if (record.inputWriter == null) break;

                    if (chunk.payload.length > 0) {
                        record.inputWriter.write(chunk.payload);
                        record.inputWriter.flush();
                    }

                    sendDataAck(record, chunk.requestId, chunk.isFinal);
                    if (chunk.isFinal) {
                        notifyInputClosed(record, ChannelApi.ChannelListener.CLOSE_REASON_NORMAL, 0);
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                Log.w(TAG, "Channel input pipe failed for " + record.token, e);
                notifyInputClosed(record, ChannelApi.ChannelListener.CLOSE_REASON_DISCONNECTED, 0);
            } finally {
                closeQuietly(record.inputWriter);
                record.inputWriter = null;
            }
        }, "WearChannelIn-" + record.channelId);
        record.inputThread.setDaemon(true);
        record.inputThread.start();
    }

    private void startOutputReader(ChannelRecord record, long startOffset, long length) {
        record.outputThread = new Thread(() -> {
            byte[] buffer = new byte[DATA_CHUNK_SIZE];
            try {
                long remainingSkip = startOffset;
                while (remainingSkip > 0) {
                    long skipped = record.outputReader.skip(remainingSkip);
                    if (skipped > 0) {
                        remainingSkip -= skipped;
                    } else if (record.outputReader.read() >= 0) {
                        remainingSkip--;
                    } else {
                        sendChunkAndAwaitAck(record, new byte[0], true);
                        notifyOutputClosed(record, ChannelApi.ChannelListener.CLOSE_REASON_NORMAL, 0);
                        return;
                    }
                }

                long remaining = length;
                while (record.state != STATE_CLOSED && !Thread.currentThread().isInterrupted()) {
                    if (remaining == 0) {
                        sendChunkAndAwaitAck(record, new byte[0], true);
                        notifyOutputClosed(record, ChannelApi.ChannelListener.CLOSE_REASON_NORMAL, 0);
                        break;
                    }
                    int maxRead = remaining > 0
                            ? (int) Math.min((long) buffer.length, remaining)
                            : buffer.length;
                    int count = record.outputReader.read(buffer, 0, maxRead);
                    if (count < 0) {
                        sendChunkAndAwaitAck(record, new byte[0], true);
                        notifyOutputClosed(record, ChannelApi.ChannelListener.CLOSE_REASON_NORMAL, 0);
                        break;
                    }
                    if (count == 0) continue;

                    byte[] payload = new byte[count];
                    System.arraycopy(buffer, 0, payload, 0, count);
                    if (!sendChunkAndAwaitAck(record, payload, false)) {
                        notifyOutputClosed(record, ChannelApi.ChannelListener.CLOSE_REASON_DISCONNECTED, 0);
                        break;
                    }
                    if (remaining > 0) remaining -= count;
                }
            } catch (IOException e) {
                Log.w(TAG, "Channel output pipe failed for " + record.token, e);
                notifyOutputClosed(record, ChannelApi.ChannelListener.CLOSE_REASON_DISCONNECTED, 0);
            } finally {
                closeQuietly(record.outputReader);
                record.outputReader = null;
            }
        }, "WearChannelOut-" + record.channelId);
        record.outputThread.setDaemon(true);
        record.outputThread.start();
    }

    private boolean sendChunkAndAwaitAck(ChannelRecord record, byte[] payload, boolean isFinal) {
        long requestId = record.nextDataRequestId.getAndIncrement();
        CountDownLatch latch = new CountDownLatch(1);
        record.pendingAcks.put(requestId, latch);

        if (!sendData(record, payload, isFinal, requestId)) {
            record.pendingAcks.remove(requestId);
            return false;
        }

        try {
            boolean acked = latch.await(DATA_ACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!acked) {
                Log.d(TAG, "No channel DATA_ACK within timeout; continuing requestId=" + requestId);
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            record.pendingAcks.remove(requestId);
        }
    }

    private boolean sendData(ChannelRecord record, byte[] payload, boolean isFinal, long requestId) {
        WearableConnection connection = wearable.getActiveConnection(record.nodeId);
        if (connection == null) return false;

        ChannelDataHeader header = new ChannelDataHeader.Builder()
                .channelId(record.channelId)
                .fromChannelOperator(record.localOpener)
                .requestId(requestId)
                .build();
        ChannelDataRequest data = new ChannelDataRequest.Builder()
                .header(header)
                .payload(ByteString.of(payload))
                .finalMessage(isFinal)
                .build();
        ChannelRequest request = new ChannelRequest.Builder()
                .channelDataRequest(data)
                .version(1)
                .build();
        try {
            connection.writeMessage(buildChannelMessage(record, request));
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Unable to send channel data", e);
            return false;
        }
    }

    private void sendDataAck(ChannelRecord record, long requestId, boolean isFinal) {
        WearableConnection connection = wearable.getActiveConnection(record.nodeId);
        if (connection == null) return;

        ChannelDataHeader header = new ChannelDataHeader.Builder()
                .channelId(record.channelId)
                .fromChannelOperator(record.localOpener)
                .requestId(requestId)
                .build();
        ChannelDataAckRequest ack = new ChannelDataAckRequest.Builder()
                .header(header)
                .finalMessage(isFinal)
                .build();
        ChannelRequest request = new ChannelRequest.Builder()
                .channelDataAckRequest(ack)
                .version(1)
                .build();
        try {
            connection.writeMessage(buildChannelMessage(record, request));
        } catch (IOException e) {
            Log.w(TAG, "Unable to send channel data ACK", e);
        }
    }

    private RootMessage buildChannelMessage(ChannelRecord record, ChannelRequest channelRequest) {
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

    private void closeStreams(ChannelRecord record, int closeReason, int errorCode) {
        Thread inputThread = record.inputThread;
        Thread outputThread = record.outputThread;
        if (inputThread != null) inputThread.interrupt();
        if (outputThread != null) outputThread.interrupt();

        closeQuietly(record.inputWriter);
        closeQuietly(record.outputReader);
        record.inputWriter = null;
        record.outputReader = null;

        notifyInputClosed(record, closeReason, errorCode);
        notifyOutputClosed(record, closeReason, errorCode);
        for (CountDownLatch latch : record.pendingAcks.values()) {
            latch.countDown();
        }
        record.pendingAcks.clear();
        record.incoming.clear();
    }

    private void notifyInputClosed(ChannelRecord record, int reason, int errorCode) {
        if (!record.inputOpened || record.inputClosedNotified) return;
        record.inputClosedNotified = true;
        notifyStreamCallback(record.inputCallbacks, reason, errorCode);
        wearable.sendChannelEvent(record.packageName,
                buildEvent(record, EVENT_INPUT_CLOSED, reason, errorCode));
    }

    private void notifyOutputClosed(ChannelRecord record, int reason, int errorCode) {
        if (!record.outputOpened || record.outputClosedNotified) return;
        record.outputClosedNotified = true;
        notifyStreamCallback(record.outputCallbacks, reason, errorCode);
        wearable.sendChannelEvent(record.packageName,
                buildEvent(record, EVENT_OUTPUT_CLOSED, reason, errorCode));
    }

    private static void notifyStreamCallback(IChannelStreamCallbacks callbacks,
                                             int reason, int errorCode) {
        if (callbacks == null) return;
        try {
            callbacks.onChannelClosed(reason, errorCode);
        } catch (Exception e) {
            Log.w(TAG, "Unable to notify channel stream close", e);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) return;
        try {
            descriptor.close();
        } catch (IOException ignored) {
        }
    }

    private static void sendInputStreamResult(IWearableCallbacks callbacks, int status,
                                              ParcelFileDescriptor descriptor) {
        if (callbacks == null) return;
        try {
            callbacks.onGetChannelInputStreamResponse(
                    new GetChannelInputStreamResponse(status, descriptor));
        } catch (Exception e) {
            Log.w(TAG, "Unable to deliver channel input stream", e);
        }
    }

    private static void sendOutputStreamResult(IWearableCallbacks callbacks, int status,
                                               ParcelFileDescriptor descriptor) {
        if (callbacks == null) return;
        try {
            callbacks.onGetChannelOutputStreamResponse(
                    new GetChannelOutputStreamResponse(status, descriptor));
        } catch (Exception e) {
            Log.w(TAG, "Unable to deliver channel output stream", e);
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
    private static final class IncomingChunk {
        final byte[] payload;
        final long requestId;
        final boolean isFinal;

        IncomingChunk(byte[] payload, long requestId, boolean isFinal) {
            this.payload = payload;
            this.requestId = requestId;
            this.isFinal = isFinal;
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
        final Object streamLock = new Object();
        final BlockingQueue<IncomingChunk> incoming =
                new LinkedBlockingQueue<IncomingChunk>(MAX_INCOMING_CHUNKS);
        final Map<Long, CountDownLatch> pendingAcks =
                new ConcurrentHashMap<Long, CountDownLatch>();
        final AtomicLong nextDataRequestId = new AtomicLong(1L);
        volatile ParcelFileDescriptor.AutoCloseOutputStream inputWriter;
        volatile ParcelFileDescriptor.AutoCloseInputStream outputReader;
        volatile IChannelStreamCallbacks inputCallbacks;
        volatile IChannelStreamCallbacks outputCallbacks;
        volatile Thread inputThread;
        volatile Thread outputThread;
        volatile boolean inputOpened;
        volatile boolean outputOpened;
        volatile boolean inputClosedNotified;
        volatile boolean outputClosedNotified;

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