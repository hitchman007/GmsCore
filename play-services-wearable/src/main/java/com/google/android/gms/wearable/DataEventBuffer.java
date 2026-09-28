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

/**
 * Data structure holding references to a set of events.
 */
@PublicApi
public class DataEventBuffer extends AbstractDataBuffer<DataEvent> implements Result {
    private final Status status;
    private final List<DataEvent> events;

    @PublicApi(exclude = true)
    public DataEventBuffer(DataHolder dataHolder) {
        super(dataHolder);
        status = new Status(dataHolder.getStatusCode());
        events = buildEvents(dataHolder);
    }

    @Override
    public DataEvent get(int position) {
        return events.get(position);
    }

    @Override
    public int getCount() {
        return events.size();
    }

    @Override
    public Status getStatus() {
        return status;
    }

    private static List<DataEvent> buildEvents(DataHolder holder) {
        LinkedHashMap<String, EventBuilder> grouped = new LinkedHashMap<String, EventBuilder>();
        for (int row = 0; row < holder.getCount(); row++) {
            int window = holder.getWindowIndex(row);
            int type = holder.getInteger("event_type", row, window);
            String path = holder.getString("path", row, window);
            String key = type + "\u0000" + String.valueOf(path);
            EventBuilder event = grouped.get(key);
            if (event == null) {
                event = new EventBuilder(
                        type,
                        Uri.parse(path),
                        holder.hasNull("data", row, window)
                                ? null
                                : holder.getByteArray("data", row, window));
                grouped.put(key, event);
            }
            if (!holder.hasNull("asset_key", row, window)
                    && !holder.hasNull("asset_id", row, window)) {
                String assetKey = holder.getString("asset_key", row, window);
                String assetId = holder.getString("asset_id", row, window);
                if (assetKey != null && assetId != null) {
                    event.assets.put(assetKey, new DataItemAssetParcelable(assetId, assetKey));
                }
            }
        }

        List<DataEvent> result = new ArrayList<DataEvent>(grouped.size());
        for (EventBuilder builder : grouped.values()) {
            DataItemParcelable item = new DataItemParcelable(builder.uri, builder.assets);
            item.data = builder.data;
            result.add(new DataEventImpl(builder.type, item));
        }
        return result;
    }

    private static final class EventBuilder {
        final int type;
        final Uri uri;
        final byte[] data;
        final Map<String, DataItemAssetParcelable> assets =
                new LinkedHashMap<String, DataItemAssetParcelable>();

        EventBuilder(int type, Uri uri, byte[] data) {
            this.type = type;
            this.uri = uri;
            this.data = data;
        }
    }

    private static final class DataEventImpl implements DataEvent {
        private final int type;
        private final DataItem dataItem;

        DataEventImpl(int type, DataItem dataItem) {
            this.type = type;
            this.dataItem = dataItem;
        }

        @Override
        public DataItem getDataItem() {
            return dataItem;
        }

        @Override
        public int getType() {
            return type;
        }

        @Override
        public DataEvent freeze() {
            return this;
        }

        @Override
        public boolean isDataValid() {
            return true;
        }
    }
}
