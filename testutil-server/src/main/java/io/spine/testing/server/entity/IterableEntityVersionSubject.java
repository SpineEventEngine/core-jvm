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

package io.spine.testing.server.entity;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.extensions.proto.IterableOfProtosSubject;
import io.spine.annotation.VisibleForTesting;
import io.spine.core.Version;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.size;
import static com.google.common.truth.Truth.assertAbout;
import static io.spine.testing.server.entity.EntityVersionSubject.entityVersion;

/**
 * A set of checks for {@link Iterable several} {@link io.spine.server.entity.Entity Entity}
 * versions.
 */
@VisibleForTesting
public final class IterableEntityVersionSubject extends IterableOfProtosSubject<Version> {

    private final @Nullable Iterable<Version> actual;

    private IterableEntityVersionSubject(FailureMetadata failureMetadata,
                                         @Nullable Iterable<Version> versions) {
        super(failureMetadata, versions);
        this.actual = versions;
    }

    public static IterableEntityVersionSubject
    assertEntityVersions(@Nullable Iterable<Version> versions) {
        return assertAbout(entityVersions()).that(versions);
    }

    /**
     * Verifies that all versions are newer than the specified {@code version}.
     */
    public void containsAllNewerThan(Version version) {
        checkNotNull(version);
        if (actual == null) {
            isNotNull();
        } else {
            actual.forEach(v -> assertVersion(v).isNewerThan(version));
        }
    }

    /**
     * Verifies that all versions are newer or at least equal to the specified {@code version}.
     */
    public void containsAllNewerOrEqualTo(Version version) {
        checkNotNull(version);
        if (actual == null) {
            isNotNull();
        } else {
            actual.forEach(v -> assertVersion(v).isNewerOrEqualTo(version));
        }
    }

    /**
     * Verifies that all versions are older than the specified {@code version}.
     */
    public void containsAllOlderThan(Version version) {
        checkNotNull(version);
        if (actual == null) {
            isNotNull();
        } else {
            actual.forEach(v -> assertVersion(v).isOlderThan(version));
        }
    }

    /**
     * Verifies that all versions are older or at least equal to the specified {@code version}.
     */
    public void containsAllOlderOrEqualTo(Version version) {
        checkNotNull(version);
        if (actual == null) {
            isNotNull();
        } else {
            actual.forEach(v -> assertVersion(v).isOlderOrEqualTo(version));
        }
    }

    /**
     * Verifies that the {@code Iterable} stores exactly one entity {@link Version} and returns a
     * {@code Subject} for it.
     */
    public EntityVersionSubject containsSingleEntityVersionThat() {
        if (actual == null) {
            isNotNull();
            return ignoreCheck().about(entityVersion())
                                .that(Version.getDefaultInstance());
        } else if (size(actual) != 1) {
            hasSize(1);
            return ignoreCheck().about(entityVersion())
                                .that(Version.getDefaultInstance());
        } else {
            var version = actual.iterator().next();
            return assertVersion(version);
        }
    }

    private EntityVersionSubject assertVersion(Version version) {
        return check("singleEntityVersion()").about(entityVersion())
                                             .that(version);
    }

    public static
    Subject.Factory<IterableEntityVersionSubject, Iterable<Version>> entityVersions() {
        return IterableEntityVersionSubject::new;
    }
}
