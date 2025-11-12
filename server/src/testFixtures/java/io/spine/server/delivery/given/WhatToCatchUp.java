/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.server.delivery.given;

import com.google.protobuf.Timestamp;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A description of what target to catch-up in tests and since when.
 */
public final class WhatToCatchUp {

    private final @Nullable String id;
    private final Timestamp sinceWhen;

    private WhatToCatchUp(String id, Timestamp sinceWhen) {
        this.id = id;
        this.sinceWhen = sinceWhen;
    }

    public static WhatToCatchUp catchUpOf(String id, Timestamp sinceWhen) {
        checkNotNull(id);
        return new WhatToCatchUp(id, sinceWhen);
    }

    public static WhatToCatchUp catchUpAll(Timestamp sinceWhen) {
        return new WhatToCatchUp(null, sinceWhen);
    }

    public @Nullable String id() {
        return id;
    }

    public boolean shouldCatchUpAll() {
        return null == id;
    }

    public Timestamp sinceWhen() {
        return sinceWhen;
    }
}
