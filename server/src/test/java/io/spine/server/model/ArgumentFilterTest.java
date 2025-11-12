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

package io.spine.server.model;

import com.google.common.testing.NullPointerTester;
import io.spine.base.EventMessage;
import io.spine.base.FieldPath;
import io.spine.server.model.given.filter.BeanAdded;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.model.ArgumentFilter.createFilter;
import static io.spine.server.model.given.filter.Bucket.everythingElse;
import static io.spine.server.model.given.filter.Bucket.onlyPeas;
import static io.spine.server.model.given.filter.Legume.BEAN;
import static io.spine.server.model.given.filter.Legume.PEA;

@DisplayName("`ArgumentFilter` should")
class ArgumentFilterTest {

    @Test
    @DisplayName("pass `null`-tolerance check")
    void nullTolerance() {
        new NullPointerTester()
                .setDefault(FieldPath.class, FieldPath.getDefaultInstance())
                .testAllPublicStaticMethods(ArgumentFilter.class);
    }

    @Test
    @DisplayName("create instance for `@Where` annotation")
    void onWhere() {
        var filter = createFilter(onlyPeas());
        assertThat(filter.acceptsAll())
                .isFalse();
        assertThat(filter.pathLength())
                .isEqualTo(1);
        assertThat(filter.expectedValue())
                .isEqualTo(PEA);
        assertThat(filter.test(peasAdded()))
                .isTrue();
        assertThat(filter.test(beansAdded()))
                .isFalse();
    }

    @Test
    @DisplayName("create empty filter if not annotated")
    void emptyFilter() {
        var filter = createFilter(everythingElse());
        assertThat(filter.acceptsAll())
                .isTrue();
        assertThat(filter.pathLength())
                .isEqualTo(0);
    }

    private static EventMessage peasAdded() {
        return BeanAdded.newBuilder()
                .setNumber(1)
                .setKind(PEA)
                .build();
    }

    private static EventMessage beansAdded() {
        return BeanAdded.newBuilder()
                .setNumber(1)
                .setKind(BEAN)
                .build();
    }
}
