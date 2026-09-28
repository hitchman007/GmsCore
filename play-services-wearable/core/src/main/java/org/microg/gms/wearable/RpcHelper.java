/*
 * Copyright (C) 2013-2017 microG Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.microg.gms.wearable;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RpcHelper {
    private final Map<String, RpcConnectionState> rpcStateMap =
            new HashMap<String, RpcConnectionState>();
    private final SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, PendingRpcListener> rpcListeners =
            new ConcurrentHashMap<String, PendingRpcListener>();

    public RpcHelper(Context context) {
        this.preferences = context.getSharedPreferences("wearable.rpc_service.settings", 0);
    }

    private String getRpcConnectionId(String packageName, String targetNodeId, String path) {
        String mode = "lo";
        if ("com.google.android.wearable.app".equals(packageName) && path != null
                && path.startsWith("/s3")) {
            mode = "hi";
        }
        return targetNodeId + ":" + mode;
    }

    public RpcConnectionState useConnectionState(String packageName, String targetNodeId,
                                                 String path) {
        String rpcConnectionId = getRpcConnectionId(packageName, targetNodeId, path);
        synchronized (rpcStateMap) {
            if (!rpcStateMap.containsKey(rpcConnectionId)) {
                int generation = preferences.getInt(rpcConnectionId, 1) + 1;
                preferences.edit().putInt(rpcConnectionId, generation).apply();
                rpcStateMap.put(rpcConnectionId, new RpcConnectionState(generation));
            }
            RpcConnectionState result = rpcStateMap.get(rpcConnectionId);
            result.lastRequestId++;
            return result.freeze();
        }
    }

    private static String rpcListenerKey(String peerNodeId, int generation, int requestId) {
        return peerNodeId + ":" + generation + ":" + requestId;
    }

    public void addResponseListener(String peerNodeId, int generation, int requestId,
                                    long timeoutMs, RpcResponseCallback onResponse,
                                    RpcTimeoutCallback onTimeout) {
        final String key = rpcListenerKey(peerNodeId, generation, requestId);
        final PendingRpcListener pending =
                new PendingRpcListener(generation, requestId, onResponse);
        rpcListeners.put(key, pending);
        mainHandler.postDelayed(() -> {
            boolean timedOut = false;
            synchronized (rpcListeners) {
                if (rpcListeners.get(key) == pending) {
                    rpcListeners.remove(key);
                    timedOut = true;
                }
            }
            if (timedOut) {
                onTimeout.onTimeout(requestId);
            }
        }, timeoutMs);
    }

    public void removeResponseListener(String peerNodeId, int generation, int requestId) {
        rpcListeners.remove(rpcListenerKey(peerNodeId, generation, requestId));
    }

    public boolean deliverRpcResponse(String peerNodeId, @Nullable Integer generation,
                                      int senderRequestId, @Nullable byte[] data) {
        PendingRpcListener listener = null;
        if (generation != null) {
            listener = rpcListeners.remove(
                    rpcListenerKey(peerNodeId, generation, senderRequestId));
        }

        if (listener == null) {
            String suffix = ":" + senderRequestId;
            String prefix = peerNodeId + ":";
            String onlyKey = null;
            for (String key : rpcListeners.keySet()) {
                if (key.startsWith(prefix) && key.endsWith(suffix)) {
                    if (onlyKey != null) {
                        return false;
                    }
                    onlyKey = key;
                }
            }
            if (onlyKey != null) {
                listener = rpcListeners.remove(onlyKey);
            }
        }

        if (listener == null) return false;
        listener.callback.onResponse(listener.requestId, data);
        return true;
    }

    public interface RpcResponseCallback {
        void onResponse(int requestId, @Nullable byte[] data);
    }

    public interface RpcTimeoutCallback {
        void onTimeout(int requestId);
    }

    private static final class PendingRpcListener {
        final int generation;
        final int requestId;
        final RpcResponseCallback callback;

        PendingRpcListener(int generation, int requestId, RpcResponseCallback callback) {
            this.generation = generation;
            this.requestId = requestId;
            this.callback = callback;
        }
    }

    public static class RpcConnectionState {
        public int generation;
        public int lastRequestId;

        public RpcConnectionState(int generation) {
            this.generation = generation;
        }

        public RpcConnectionState freeze() {
            RpcConnectionState result = new RpcConnectionState(generation);
            result.lastRequestId = lastRequestId;
            return result;
        }
    }
}