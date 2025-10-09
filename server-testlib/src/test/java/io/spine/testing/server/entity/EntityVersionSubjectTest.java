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

import com.google.common.testing.NullPointerTester;
import com.google.common.truth.Subject;
import io.spine.core.Version;
import io.spine.testing.SubjectTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static io.spine.testing.server.entity.EntityVersionSubject.assertEntityVersion;
import static io.spine.testing.server.entity.EntityVersionSubject.entityVersion;
import static io.spine.testing.server.entity.given.GivenEntityVersion.newerVersion;
import static io.spine.testing.server.entity.given.GivenEntityVersion.olderVersion;
import static io.spine.testing.server.entity.given.GivenEntityVersion.version;

@DisplayName("EntityVersionSubject should")
public class EntityVersionSubjectTest extends SubjectTest<EntityVersionSubject, Version> {

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void passNullToleranceCheck() {
        new NullPointerTester()
                .testAllPublicInstanceMethods(assertEntityVersion(version()));
    }

    @Test
    @DisplayName("check if version is newer than the other version")
    void checkNewer() {
        assertWithSubjectThat(version())
                .isNewerThan(olderVersion());

        expectSomeFailure(whenTesting -> whenTesting.that(version())
                                                    .isNewerThan(newerVersion()));
    }

    @Test
    @DisplayName("check if version is newer or equal to the other version")
    void checkNewerOrEqual() {
        assertWithSubjectThat(version())
                .isNewerOrEqualTo(version());

        expectSomeFailure(whenTesting -> whenTesting.that(version())
                                                    .isNewerOrEqualTo(newerVersion()));
    }

    @Test
    @DisplayName("check if version is older than the other version")
    void checkOlder() {
        assertWithSubjectThat(version())
                .isOlderThan(newerVersion());

        expectSomeFailure(whenTesting -> whenTesting.that(version())
                                                    .isOlderThan(olderVersion()));
    }

    @Test
    @DisplayName("check if version is older or equal to the other version")
    void checkOlderOrEqual() {
        assertWithSubjectThat(version())
                .isOlderOrEqualTo(version());

        expectSomeFailure(whenTesting -> whenTesting.that(version())
                                                    .isOlderOrEqualTo(olderVersion()));
    }

    @Override
    protected Subject.Factory<EntityVersionSubject, Version> subjectFactory() {
        return entityVersion();
    }
}
