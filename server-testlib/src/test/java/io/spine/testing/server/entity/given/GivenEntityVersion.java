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

package io.spine.testing.server.entity.given;

import com.google.protobuf.Duration;
import com.google.protobuf.util.Durations;
import io.spine.core.Version;

import static com.google.protobuf.util.Timestamps.add;
import static com.google.protobuf.util.Timestamps.subtract;
import static io.spine.base.Time.currentTime;

public final class GivenEntityVersion {

    private static final Duration TEN_DAYS = Durations.fromDays(10);

    /** Prevents instantiation of this test env class. */
    private GivenEntityVersion() {
    }

    public static Version version() {
        var version = Version.newBuilder()
                .setNumber(42)
                .setTimestamp(currentTime())
                .build();
        return version;
    }

    public static Version olderVersion() {
        var version = Version.newBuilder()
                .setNumber(15)
                .setTimestamp(subtract(currentTime(), TEN_DAYS))
                .build();
        return version;
    }

    public static Version newerVersion() {
        var version = Version.newBuilder()
                .setNumber(125)
                .setTimestamp(add(currentTime(), TEN_DAYS))
                .build();
        return version;
    }
}
