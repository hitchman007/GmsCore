/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package org.microg.gms.wearable;

import android.content.IntentFilter;
import android.os.RemoteException;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.ChannelApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.internal.AddListenerRequest;
import com.google.android.gms.wearable.internal.IWearableListener;
import com.google.android.gms.wearable.internal.OpenChannelResponse;
import com.google.android.gms.wearable.internal.RemoveListenerRequest;

import org.microg.gms.common.GmsConnector;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
public class ChannelApiImpl implements ChannelApi {
    private final Map<ChannelListener, IWearableListener> listeners =
            Collections.synchronizedMap(new IdentityHashMap<ChannelListener, IWearableListener>());

    @Override
    public PendingResult<Status> addListener(GoogleApiClient client, final ChannelListener listener) {
        return GmsConnector.call(client, Wearable.API,
                new GmsConnector.Callback<WearableClientImpl, Status>() {
                    @Override
                    public void onClientAvailable(WearableClientImpl client,
                                                  final ResultProvider<Status> resultProvider)
                            throws RemoteException {
                        IWearableListener adapter = listeners.get(listener);
                        if (adapter == null) {
                            adapter = WearableListenerAdapter.forChannel(listener);
                            listeners.put(listener, adapter);
                        }
                        client.getServiceInterface().addListener(new BaseWearableCallbacks() {
                            @Override
                            public void onStatus(Status status) throws RemoteException {
                                if (!status.isSuccess()) listeners.remove(listener);
                                resultProvider.onResultAvailable(status);
                            }
                        }, new AddListenerRequest(adapter, new IntentFilter[0], null));
                    }
                });
    }
    @Override
    public PendingResult<OpenChannelResult> openChannel(GoogleApiClient client,
                                                        final String nodeId,
                                                        final String path) {
        return GmsConnector.call(client, Wearable.API,
                new GmsConnector.Callback<WearableClientImpl, OpenChannelResult>() {
                    @Override
                    public void onClientAvailable(WearableClientImpl client,
                                                  final ResultProvider<OpenChannelResult> resultProvider)
                            throws RemoteException {
                        client.getServiceInterface().openChannel(new BaseWearableCallbacks() {
                            @Override
                            public void onOpenChannelResponse(OpenChannelResponse response)
                                    throws RemoteException {
                                resultProvider.onResultAvailable(new OpenChannelResultImpl(response));
                            }
                        }, nodeId, path);
                    }
                });
    }

    @Override
    public PendingResult<Status> removeListener(GoogleApiClient client, final ChannelListener listener) {
        return GmsConnector.call(client, Wearable.API,
                new GmsConnector.Callback<WearableClientImpl, Status>() {
                    @Override
                    public void onClientAvailable(WearableClientImpl client,
                                                  final ResultProvider<Status> resultProvider)
                            throws RemoteException {
                        final IWearableListener adapter = listeners.get(listener);
                        if (adapter == null) {
                            resultProvider.onResultAvailable(Status.SUCCESS);
                            return;
                        }
                        client.getServiceInterface().removeListener(new BaseWearableCallbacks() {
                            @Override
                            public void onStatus(Status status) throws RemoteException {
                                if (status.isSuccess()) listeners.remove(listener);
                                resultProvider.onResultAvailable(status);
                            }
                        }, new RemoveListenerRequest(adapter));
                    }
                });
    }

    static final class OpenChannelResultImpl implements OpenChannelResult {
        private final OpenChannelResponse response;

        OpenChannelResultImpl(OpenChannelResponse response) {
            this.response = response;
        }

        @Override
        public Channel getChannel() {
            return response != null && response.channel != null
                    ? new ChannelImpl(response.channel) : null;
        }

        @Override
        public Status getStatus() {
            return new Status(response != null ? response.statusCode : 8);
        }
    }
}