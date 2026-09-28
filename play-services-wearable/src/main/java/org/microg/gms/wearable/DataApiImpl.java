/*
 * Copyright (C) 2013-2017 microG Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.microg.gms.wearable;

import android.content.IntentFilter;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.data.DataHolder;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemAsset;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.internal.AddListenerRequest;
import com.google.android.gms.wearable.internal.DeleteDataItemsResponse;
import com.google.android.gms.wearable.internal.GetDataItemResponse;
import com.google.android.gms.wearable.internal.GetFdForAssetResponse;
import com.google.android.gms.wearable.internal.IWearableListener;
import com.google.android.gms.wearable.internal.PutDataRequest;
import com.google.android.gms.wearable.internal.PutDataResponse;
import com.google.android.gms.wearable.internal.RemoveListenerRequest;

import org.microg.gms.common.GmsConnector;

import java.io.InputStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public class DataApiImpl implements DataApi {
    private final Map<DataListener, IWearableListener> listeners =
            Collections.synchronizedMap(new IdentityHashMap<DataListener, IWearableListener>());

    @Override
    public PendingResult<Status> addListener(GoogleApiClient client, final DataListener listener) {
        return GmsConnector.call(client, Wearable.API,
                new GmsConnector.Callback<WearableClientImpl, Status>() {
                    @Override
                    public void onClientAvailable(WearableClientImpl client,
                                                  final ResultProvider<Status> resultProvider)
                            throws RemoteException {
                        IWearableListener adapter = listeners.get(listener);
                        if (adapter == null) {
                            adapter = WearableListenerAdapter.forData(listener);
                            listeners.put(listener, adapter);
                        }
                        final IWearableListener registered = adapter;
                        client.getServiceInterface().addListener(new BaseWearableCallbacks() {
                            @Override
                            public void onStatus(Status status) throws RemoteException {
                                if (!status.isSuccess()) listeners.remove(listener);
                                resultProvider.onResultAvailable(status);
                            }
                        }, new AddListenerRequest(registered, new IntentFilter[0], null));
                    }
                });
    }

    @Override
    public PendingResult<DeleteDataItemsResult> deleteDataItems(GoogleApiClient client,
                                                                 final Uri uri) {
        return GmsConnector.call(client, Wearable.API,
                new GmsConnector.Callback<WearableClientImpl, DeleteDataItemsResult>() {
                    @Override
                    public void onClientAvailable(WearableClientImpl client,
                                                  final ResultProvider<DeleteDataItemsResult> resultProvider)
                            throws RemoteException {
                        client.getServiceInterface().deleteDataItems(new BaseWearableCallbacks() {
                            @Override
                            public void onDeleteDataItemsResponse(DeleteDataItemsResponse response)
                                    throws RemoteException {
                                resultProvider.onResultAvailable(new DeleteDataItemsResultImpl(response));
                            }
                        }, uri);
                    }
                });
    }

    @Override
    public PendingResult<DataItemResult> getDataItem(GoogleApiClient client, final Uri uri) {
        return GmsConnector.call(client, Wearable.API,
                new GmsConnector.Callback<WearableClientImpl, DataItemResult>() {
                    @Override
                    public void onClientAvailable(WearableClientImpl client,
                                                  final ResultProvider<DataItemResult> resultProvider)
                            throws RemoteException {
                        client.getServiceInterface().getDataItem(new BaseWearableCallbacks() {
                            @Override
                            public void onGetDataItemResponse(GetDataItemResponse response)
                                    throws RemoteException {
                                resultProvider.onResultAvailable(new DataItemResultImpl(
                                        response.statusCode, response.dataItem));
                            }
                        }, uri);
                    }
                });
    }

    @Override
    public PendingResult<DataItemBuffer> getDataItems(GoogleApiClient client) {
        return GmsConnector.call(client, Wearable.API,
                new GmsConnector.Callback<WearableClientImpl, DataItemBuffer>() {
                    @Override
                    public void onClientAvailable(WearableClientImpl client,
                                                  final ResultProvider<DataItemBuffer> resultProvider)
                            throws RemoteException {
                        client.getServiceInterface().getDataItems(new BaseWearableCallbacks() {
                            @Override
                            public void onDataItemChanged(DataHolder dataHolder)
                                    throws RemoteException {
                                resultProvider.onResultAvailable(new DataItemBuffer(dataHolder));
                            }
                        });
                    }
                });
    }

    @Override
    public PendingResult<DataItemBuffer> getDataItems(GoogleApiClient client, final Uri uri) {
        return GmsConnector.call(client, Wearable.API,
                new GmsConnector.Callback<WearableClientImpl, DataItemBuffer>() {
                    @Override
                    public void onClientAvailable(WearableClientImpl client,
                                                  final ResultProvider<DataItemBuffer> resultProvider)
                            throws RemoteException {
                        client.getServiceInterface().getDataItemsByUri(new BaseWearableCallbacks() {
                            @Override
                            public void onDataItemChanged(DataHolder dataHolder)
                                    throws RemoteException {
                                resultProvider.onResultAvailable(new DataItemBuffer(dataHolder));
                            }
                        }, uri);
                    }
                });
    }

    @Override
    public PendingResult<GetFdForAssetResult> getFdForAsset(GoogleApiClient client,
                                                             DataItemAsset asset) {
        return getFdForAsset(client, Asset.createFromRef(asset.getId()));
    }

    @Override
    public PendingResult<GetFdForAssetResult> getFdForAsset(GoogleApiClient client,
                                                             final Asset asset) {
        return GmsConnector.call(client, Wearable.API,
                new GmsConnector.Callback<WearableClientImpl, GetFdForAssetResult>() {
                    @Override
                    public void onClientAvailable(WearableClientImpl client,
                                                  final ResultProvider<GetFdForAssetResult> resultProvider)
                            throws RemoteException {
                        client.getServiceInterface().getFdForAsset(new BaseWearableCallbacks() {
                            @Override
                            public void onGetFdForAssetResponse(GetFdForAssetResponse response)
                                    throws RemoteException {
                                resultProvider.onResultAvailable(new GetFdForAssetResultImpl(response));
                            }
                        }, asset);
                    }
                });
    }

    @Override
    public PendingResult<DataItemResult> putDataItem(GoogleApiClient client,
                                                      final PutDataRequest request) {
        return GmsConnector.call(client, Wearable.API,
                new GmsConnector.Callback<WearableClientImpl, DataItemResult>() {
                    @Override
                    public void onClientAvailable(WearableClientImpl client,
                                                  final ResultProvider<DataItemResult> resultProvider)
                            throws RemoteException {
                        client.getServiceInterface().putData(new BaseWearableCallbacks() {
                            @Override
                            public void onPutDataResponse(PutDataResponse response)
                                    throws RemoteException {
                                resultProvider.onResultAvailable(new DataItemResultImpl(
                                        response.statusCode, response.dataItem));
                            }
                        }, request);
                    }
                });
    }

    @Override
    public PendingResult<Status> removeListener(GoogleApiClient client,
                                                 final DataListener listener) {
        return GmsConnector.call(client, Wearable.API,
                new GmsConnector.Callback<WearableClientImpl, Status>() {
                    @Override
                    public void onClientAvailable(WearableClientImpl client,
                                                  final ResultProvider<Status> resultProvider)
                            throws RemoteException {
                        final IWearableListener adapter = listeners.get(listener);
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

    private static class DataItemResultImpl implements DataItemResult {
        private final Status status;
        private final DataItem dataItem;

        private DataItemResultImpl(int statusCode, DataItem dataItem) {
            this.status = new Status(statusCode);
            this.dataItem = dataItem;
        }

        @Override
        public DataItem getDataItem() {
            return dataItem;
        }

        @Override
        public Status getStatus() {
            return status;
        }
    }

    private static class DeleteDataItemsResultImpl implements DeleteDataItemsResult {
        private final DeleteDataItemsResponse response;

        private DeleteDataItemsResultImpl(DeleteDataItemsResponse response) {
            this.response = response;
        }

        @Override
        public int getNumDeleted() {
            return response.getCount();
        }

        @Override
        public Status getStatus() {
            return new Status(response.getStatusCode());
        }
    }

    private static class GetFdForAssetResultImpl implements GetFdForAssetResult {
        private final GetFdForAssetResponse response;
        private InputStream inputStream;

        private GetFdForAssetResultImpl(GetFdForAssetResponse response) {
            this.response = response;
        }

        @Override
        public ParcelFileDescriptor getFd() {
            return response.pfd;
        }

        @Override
        public synchronized InputStream getInputStream() {
            if (response.pfd == null) return null;
            if (inputStream == null) {
                inputStream = new ParcelFileDescriptor.AutoCloseInputStream(response.pfd);
            }
            return inputStream;
        }

        @Override
        public Status getStatus() {
            return new Status(response.statusCode);
        }
    }
}
