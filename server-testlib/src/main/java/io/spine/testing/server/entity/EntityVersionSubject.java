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

package io.spine.testing.server.entity;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.extensions.proto.ProtoSubject;
import io.spine.annotation.VisibleForTesting;
import io.spine.core.Version;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertAbout;

/**
 * A set of checks for the {@link io.spine.server.entity.Entity Entity} version.
 */
@VisibleForTesting
public final class EntityVersionSubject extends ProtoSubject {

    private final @Nullable Version actual;

    private EntityVersionSubject(FailureMetadata failureMetadata, @Nullable Version message) {
        super(failureMetadata, message);
        this.actual = message;
    }

    public static EntityVersionSubject assertEntityVersion(Version version) {
        return assertAbout(entityVersion()).that(version);
    }

    /**
     * Verifies the version is an increment of the {@code other}.
     */
    public void isNewerThan(Version other) {
        checkNotNull(other);
        if (actual() == null) {
            isNotNull();
        } else {
            check("isIncrement()").that(nonNullActual().isIncrement(other))
                                  .isTrue();
        }
    }

    /**
     * Verifies the version is an increment or at least equal to the {@code other}.
     */
    public void isNewerOrEqualTo(Version other) {
        checkNotNull(other);
        if (actual() == null) {
            isNotNull();
        } else {
            check("isIncrementOrEqual()").that(nonNullActual().isIncrementOrEqual(other))
                                         .isTrue();
        }
    }

    /**
     * Verifies the version is a decrement of the {@code other}.
     */
    public void isOlderThan(Version other) {
        checkNotNull(other);
        if (actual() == null) {
            isNotNull();
        } else {
            check("isDecrement()").that(nonNullActual().isDecrement(other))
                                  .isTrue();
        }
    }

    /**
     * Verifies the version is a decrement or at least equal to the {@code other}.
     */
    public void isOlderOrEqualTo(Version other) {
        checkNotNull(other);
        if (actual() == null) {
            isNotNull();
        } else {
            check("isDecrementOrEqual()").that(nonNullActual().isDecrementOrEqual(other))
                                         .isTrue();
        }
    }

    /**
     * Obtains the entity version under verification.
     */
    public @Nullable Version actual() {
        return actual;
    }

    private Version nonNullActual() {
        assertExists();
        return checkNotNull(actual);
    }

    private void assertExists() {
        isNotNull();
    }

    static
    Subject.Factory<EntityVersionSubject, Version> entityVersion() {
        return EntityVersionSubject::new;
    }
}
