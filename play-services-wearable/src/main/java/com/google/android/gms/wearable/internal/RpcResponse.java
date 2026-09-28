/*
 * Copyright 2013-2026 microG Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;
import org.microg.safeparcel.SafeParceled;

public class RpcResponse extends AutoSafeParcelable {
    @SafeParceled(1)
    public int statusCode;
    @SafeParceled(2)
    public int requestId;
    @SafeParceled(3)
    public byte[] data;

    private RpcResponse() {
    }

    public RpcResponse(int statusCode, int requestId, byte[] data) {
        this.statusCode = statusCode;
        this.requestId = requestId;
        this.data = data;
    }

    public static final Creator<RpcResponse> CREATOR =
            new AutoCreator<RpcResponse>(RpcResponse.class);
}