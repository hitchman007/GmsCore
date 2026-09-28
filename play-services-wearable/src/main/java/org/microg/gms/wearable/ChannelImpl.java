/*
 * Copyright (C) 2013-2017 microG Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.microg.gms.wearable;

import android.net.Uri;
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

import org.microg.gms.common.GmsConnector;

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
        Log.d(TAG, "unimplemented Method: getInputStream");
        return null;
    }

    @Override
    public PendingResult<GetOutputStreamResult> getOutputStream(GoogleApiClient client) {
        Log.d(TAG, "unimplemented Method: getOutputStream");
        return null;
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
}