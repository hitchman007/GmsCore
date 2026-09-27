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

package com.google.android.gms.wearable;

import android.net.Uri;

import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.data.AbstractDataBuffer;
import com.google.android.gms.common.data.DataHolder;
import com.google.android.gms.wearable.internal.DataItemAssetParcelable;
import com.google.android.gms.wearable.internal.DataItemParcelable;

import org.microg.gms.common.PublicApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@PublicApi
public class DataItemBuffer extends AbstractDataBuffer<DataItem> implements Result {
    private final Status status;
    private final List<DataItem> items;

    @PublicApi(exclude = true)
    public DataItemBuffer(DataHolder dataHolder) {
        super(dataHolder);
        status = new Status(dataHolder.getStatusCode());
        items = buildItems(dataHolder);
    }

    @Override
    public DataItem get(int position) {
        return items.get(position);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Status getStatus() {
        return status;
    }

    private static List<DataItem> buildItems(DataHolder holder) {
        LinkedHashMap<String, ItemBuilder> grouped = new LinkedHashMap<String, ItemBuilder>();
        for (int row = 0; row < holder.getCount(); row++) {
            int window = holder.getWindowIndex(row);
            String host = holder.getString("host", row, window);
            String path = holder.getString("path", row, window);
            String key = String.valueOf(host) + "\u0000" + String.valueOf(path);
            ItemBuilder item = grouped.get(key);
            if (item == null) {
                Uri uri = Uri.parse("wear://" + host + (path != null && path.startsWith("/") ? path : "/" + path));
                item = new ItemBuilder(uri,
                        holder.hasNull("data", row, window) ? null : holder.getByteArray("data", row, window));
                grouped.put(key, item);
            }
            if (!holder.hasNull("asset_key", row, window)
                    && !holder.hasNull("asset_id", row, window)) {
                String assetKey = holder.getString("asset_key", row, window);
                String assetId = holder.getString("asset_id", row, window);
                if (assetKey != null && assetId != null) {
                    item.assets.put(assetKey, new DataItemAssetParcelable(assetId, assetKey));
                }
            }
        }

        List<DataItem> result = new ArrayList<DataItem>(grouped.size());
        for (ItemBuilder builder : grouped.values()) {
            DataItemParcelable item = new DataItemParcelable(builder.uri, builder.assets);
            item.data = builder.data;
            result.add(item);
        }
        return result;
    }

    private static final class ItemBuilder {
        final Uri uri;
        final byte[] data;
        final Map<String, DataItemAssetParcelable> assets =
                new LinkedHashMap<String, DataItemAssetParcelable>();

        ItemBuilder(Uri uri, byte[] data) {
            this.uri = uri;
            this.data = data;
        }
    }
}
