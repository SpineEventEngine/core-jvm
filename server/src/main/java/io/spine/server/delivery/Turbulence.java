/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.delivery;

import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import io.spine.base.Time;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.protobuf.util.Timestamps.subtract;

/**
 * Defines the turbulence period in a catch-up delivery.
 *
 * <p>Turbulence is a period in time, when the stream of historical events is mixed with
 * the stream of live events at the very end of the catch-up process.
 *
 * <p>It starts when the catch-up comes close to the current time in the event history.
 * In this phase the task is to understand when to stop reading the history and switch back
 * to delivering the live events dispatched to the catching-up projections.
 */
final class Turbulence {

    private final Duration duration;

    private Turbulence(Duration duration) {
        this.duration = duration;
    }

    /**
     * Creates a new instance of {@code Turbulence} with the specified duration.
     */
    static Turbulence of(Duration duration) {
        checkNotNull(duration);
        return new Turbulence(duration);
    }

    /**
     * Returns the start time of the turbulence period, counting back from the
     * {@linkplain Time#currentTime() current time}.
     */
    Timestamp whenStarts() {
        return subtract(Time.currentTime(), duration);
    }
}
