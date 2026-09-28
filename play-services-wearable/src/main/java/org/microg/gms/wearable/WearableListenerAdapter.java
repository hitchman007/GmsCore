/*
 * Copyright (C) 2013-2026 microG Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.microg.gms.wearable;

import android.os.RemoteException;

import com.google.android.gms.common.data.DataHolder;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.internal.AmsEntityUpdateParcelable;
import com.google.android.gms.wearable.internal.AncsNotificationParcelable;
import com.google.android.gms.wearable.internal.CapabilityInfoParcelable;
import com.google.android.gms.wearable.internal.ChannelEventParcelable;
import com.google.android.gms.wearable.internal.IWearableListener;
import com.google.android.gms.wearable.internal.MessageEventParcelable;
import com.google.android.gms.wearable.internal.NodeParcelable;

import java.util.List;

/**
 * Binder listener used by the legacy Wearable APIs.
 *
 * Each API implementation keeps the same adapter instance until removeListener
 * so the service can unregister the exact binder that was registered.
 */
final class WearableListenerAdapter extends IWearableListener.Stub {
    private final NodeApi.NodeListener nodeListener;
    private final DataApi.DataListener dataListener;
    private final MessageApi.MessageListener messageListener;

    private WearableListenerAdapter(NodeApi.NodeListener nodeListener,
                                    DataApi.DataListener dataListener,
                                    MessageApi.MessageListener messageListener) {
        this.nodeListener = nodeListener;
        this.dataListener = dataListener;
        this.messageListener = messageListener;
    }

    static WearableListenerAdapter forNode(NodeApi.NodeListener listener) {
        return new WearableListenerAdapter(listener, null, null);
    }

    static WearableListenerAdapter forData(DataApi.DataListener listener) {
        return new WearableListenerAdapter(null, listener, null);
    }

    static WearableListenerAdapter forMessage(MessageApi.MessageListener listener) {
        return new WearableListenerAdapter(null, null, listener);
    }

    @Override
    public void onDataChanged(DataHolder data) throws RemoteException {
        if (dataListener != null) {
            dataListener.onDataChanged(new DataEventBuffer(data));
        }
    }

    @Override
    public void onMessageReceived(MessageEventParcelable messageEvent) throws RemoteException {
        if (messageListener != null) {
            messageListener.onMessageReceived(messageEvent);
        }
    }

    @Override
    public void onPeerConnected(NodeParcelable node) throws RemoteException {
        if (nodeListener != null) {
            nodeListener.onPeerConnected(node);
        }
    }

    @Override
    public void onPeerDisconnected(NodeParcelable node) throws RemoteException {
        if (nodeListener != null) {
            nodeListener.onPeerDisconnected(node);
        }
    }

    @Override
    public void onConnectedNodes(List<NodeParcelable> nodes) throws RemoteException {
    }

    @Override
    public void onNotificationReceived(AncsNotificationParcelable notification) throws RemoteException {
    }

    @Override
    public void onChannelEvent(ChannelEventParcelable channelEvent) throws RemoteException {
    }

    @Override
    public void onConnectedCapabilityChanged(CapabilityInfoParcelable capabilityInfo) throws RemoteException {
    }

    @Override
    public void onEntityUpdate(AmsEntityUpdateParcelable update) throws RemoteException {
    }
}
