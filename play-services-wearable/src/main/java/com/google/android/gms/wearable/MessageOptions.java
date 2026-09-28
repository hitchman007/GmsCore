/*
 * Copyright 2013-2026 microG Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.google.android.gms.wearable;

import org.microg.safeparcel.AutoSafeParcelable;
import org.microg.safeparcel.SafeParceled;

public class MessageOptions extends AutoSafeParcelable {
    @SafeParceled(1)
    public final int version = 1;
    @SafeParceled(2)
    public int priority;

    private MessageOptions() {
    }

    public MessageOptions(int priority) {
        this.priority = priority;
    }

    public static final Creator<MessageOptions> CREATOR =
            new AutoCreator<MessageOptions>(MessageOptions.class);
}