/*
 * Copyright (C) 2013-2017 microG Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.microg.gms.wearable;

import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.ChannelApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.internal.ChannelParcelable;
import com.google.android.gms.wearable.internal.CloseChannelResponse;
import com.google.android.gms.wearable.internal.GetChannelInputStreamResponse;
import com.google.android.gms.wearable.internal.GetChannelOutputStreamResponse;
import com.google.android.gms.wearable.internal.IChannelStreamCallbacks;

import org.microg.gms.common.GmsConnector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ChannelImpl extends ChannelParcelable implements Channel {
    private static final String TAG = "GmsWearChannelImpl";

    public ChannelImpl(String token, String nodeId, String path) {
        super(token, nodeId, path);
    }

    public ChannelImpl(ChannelParcelable wrapped) {
        this(wrapped.token, wrapped.nodeId, wrapped.path);
    }
    @Override
    public PendingResult<Status> addListener(GoogleApiClient client, ChannelApi.ChannelListener listener) {
        return Wearable.ChannelApi.addListener(client, listener);
    }

    @Override
    public PendingResult<Status> close(GoogleApiClient client, int errorCode) {
        return closeInternal(client, errorCode, true);
    }

    @Override
    public PendingResult<Status> close(GoogleApiClient client) {
        return closeInternal(client, 0, false);
    }

    private PendingResult<Status> closeInternal(GoogleApiClient client,
                                                final int errorCode,
                                                final boolean withError) {
        return GmsConnector.call(client, Wearable.API,
                new GmsConnector.Callback<WearableClientImpl, Status>() {
                    @Override
                    public void onClientAvailable(WearableClientImpl client,
                                                  final ResultProvider<Status> resultProvider)
                            throws RemoteException {
                        BaseWearableCallbacks callbacks = new BaseWearableCallbacks() {
                            @Override
                            public void onCloseChannelResponse(CloseChannelResponse response)
                                    throws RemoteException {
                                resultProvider.onResultAvailable(
                                        new Status(response != null ? response.statusCode : 8));
                            }
                        };
                        if (withError) {
                            client.getServiceInterface().closeChannelWithError(
                                    callbacks, token, errorCode);
                        } else {
                            client.getServiceInterface().closeChannel(callbacks, token);
                        }
                    }
                });
    }

    @Override
    public PendingResult<GetInputStreamResult> getInputStream(GoogleApiClient client) {
        return GmsConnector.call(client, Wearable.API,
                new GmsConnector.Callback<WearableClientImpl, GetInputStreamResult>() {
                    @Override
                    public void onClientAvailable(WearableClientImpl client,
                                                  final ResultProvider<GetInputStreamResult> resultProvider)
                            throws RemoteException {
                        client.getServiceInterface().getChannelInputStream(
                                new BaseWearableCallbacks() {
                                    @Override
                                    public void onGetChannelInputStreamResponse(
                                            GetChannelInputStreamResponse response)
                                            throws RemoteException {
                                        resultProvider.onResultAvailable(
                                                new InputStreamResultImpl(response));
                                    }
                                },
                                new IChannelStreamCallbacks.Stub() {
                                    @Override
                                    public void onChannelClosed(int closeReason,
                                                                int appSpecificErrorCode)
                                            throws RemoteException {
                                    }
                                },
                                token);
                    }
                });
    }

    @Override
    public PendingResult<GetOutputStreamResult> getOutputStream(GoogleApiClient client) {
        return GmsConnector.call(client, Wearable.API,
                new GmsConnector.Callback<WearableClientImpl, GetOutputStreamResult>() {
                    @Override
                    public void onClientAvailable(WearableClientImpl client,
                                                  final ResultProvider<GetOutputStreamResult> resultProvider)
                            throws RemoteException {
                        client.getServiceInterface().getChannelOutputStream(
                                new BaseWearableCallbacks() {
                                    @Override
                                    public void onGetChannelOutputStreamResponse(
                                            GetChannelOutputStreamResponse response)
                                            throws RemoteException {
                                        resultProvider.onResultAvailable(
                                                new OutputStreamResultImpl(response));
                                    }
                                },
                                new IChannelStreamCallbacks.Stub() {
                                    @Override
                                    public void onChannelClosed(int closeReason,
                                                                int appSpecificErrorCode)
                                            throws RemoteException {
                                    }
                                },
                                token);
                    }
                });
    }

    public String getNodeId() {
        return nodeId;
    }

    @Override
    public String getPath() {
        return path;
    }

    public String getToken() {
        return token;
    }
    @Override
    public PendingResult<Status> receiveFile(GoogleApiClient client, Uri uri, boolean append) {
        Log.d(TAG, "unimplemented Method: receiveFile");
        return null;
    }

    @Override
    public PendingResult<Status> removeListener(GoogleApiClient client,
                                                ChannelApi.ChannelListener listener) {
        return Wearable.ChannelApi.removeListener(client, listener);
    }

    @Override
    public PendingResult<Status> sendFile(GoogleApiClient client, Uri uri) {
        Log.d(TAG, "unimplemented Method: sendFile");
        return null;
    }

    @Override
    public PendingResult<Status> sendFile(GoogleApiClient client, Uri uri,
                                          long startOffset, long length) {
        Log.d(TAG, "unimplemented Method: sendFile");
        return null;
    }

    private static final class InputStreamResultImpl implements GetInputStreamResult {
        private final Status status;
        private final ParcelFileDescriptor descriptor;
        private final InputStream inputStream;

        InputStreamResultImpl(GetChannelInputStreamResponse response) {
            int statusCode = response != null ? response.statusCode : 8;
            this.status = new Status(statusCode);
            this.descriptor = response != null ? response.descriptor : null;
            this.inputStream = status.isSuccess() && descriptor != null
                    ? new ParcelFileDescriptor.AutoCloseInputStream(descriptor) : null;
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public Status getStatus() {
            return status;
        }

        @Override
        public void release() {
            closeQuietly(inputStream, descriptor);
        }
    }

    private static final class OutputStreamResultImpl implements GetOutputStreamResult {
        private final Status status;
        private final ParcelFileDescriptor descriptor;
        private final OutputStream outputStream;

        OutputStreamResultImpl(GetChannelOutputStreamResponse response) {
            int statusCode = response != null ? response.statusCode : 8;
            this.status = new Status(statusCode);
            this.descriptor = response != null ? response.descriptor : null;
            this.outputStream = status.isSuccess() && descriptor != null
                    ? new ParcelFileDescriptor.AutoCloseOutputStream(descriptor) : null;
        }

        @Override
        public OutputStream getOutputStream() {
            return outputStream;
        }

        @Override
        public Status getStatus() {
            return status;
        }

        @Override
        public void release() {
            closeQuietly(outputStream, descriptor);
        }
    }

    private static void closeQuietly(java.io.Closeable stream, ParcelFileDescriptor descriptor) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        } else if (descriptor != null) {
            try {
                descriptor.close();
            } catch (IOException ignored) {
            }
        }
    }
}