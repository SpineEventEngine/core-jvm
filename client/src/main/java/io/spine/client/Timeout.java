/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.client;

import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.Immutable;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A timout value with a unit.
 *
 * @implNote This class is created instead of reusing {@code okio.Timeout} to avoid
 *         dependency on the library, which is a transitive dependency of gRPC.
 *         Also, {@code okio.Timeout} does a lot more than we need in this package.
 */
@Immutable
public final class Timeout {

    private final long value;
    private final TimeUnit unit;

    static Timeout of(long value, TimeUnit unit) {
        checkNotNull(unit);
        return new Timeout(value, unit);
    }

    private Timeout(long value, TimeUnit unit) {
        this.value = value;
        this.unit = unit;
    }

    long value() {
        return value;
    }

    TimeUnit unit() {
        return unit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var timeout = (Timeout) o;
        return value == timeout.value &&
                unit == timeout.unit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, unit);
    }

    @Override
    @SuppressWarnings("DuplicateStringLiteralInspection") // local field name semantics
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("value", value)
                          .add("unit", unit)
                          .toString();
    }
}
