/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wearable.internal;

import org.microg.safeparcel.AutoSafeParcelable;
import org.microg.safeparcel.SafeParceled;

public class GetNodeIdResponse extends AutoSafeParcelable {
    @SafeParceled(1)
    private final int versionCode = 1;
    @SafeParceled(2)
    public int statusCode;
    @SafeParceled(3)
    public String nodeId;

    private GetNodeIdResponse() {
    }

    public GetNodeIdResponse(int statusCode, String nodeId) {
        this.statusCode = statusCode;
        this.nodeId = nodeId;
    }

    public static final Creator<GetNodeIdResponse> CREATOR =
            new AutoCreator<GetNodeIdResponse>(GetNodeIdResponse.class);
}
