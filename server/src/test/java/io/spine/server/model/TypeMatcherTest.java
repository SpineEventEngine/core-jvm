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

import com.google.common.reflect.TypeToken;
import com.google.common.testing.NullPointerTester;
import com.google.protobuf.Message;
import io.spine.base.CommandMessage;
import io.spine.base.EventMessage;
import io.spine.core.UserId;
import io.spine.server.event.NoReaction;
import io.spine.server.command.DoNothing;
import io.spine.server.tuple.Pair;
import io.spine.server.tuple.Triplet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.model.TypeMatcher.matches;

@DisplayName("`TypeMatcher` should")
@SuppressWarnings({
        "SerializableNonStaticInnerClassWithoutSerialVersionUID",
        "SerializableInnerClassWithNonSerializableOuterClass" /* using anonymous `TypeToken`s. */
})
class TypeMatcherTest {

    @Test
    @DisplayName("not accept `null`s in package-private static methods")
    void nullCheckStaticMethods() {
        var tester = new NullPointerTester();
        tester.setDefault(TypeToken.class, TypeToken.of(TypeMatcherTest.class));
        tester.testStaticMethods(TypeMatcher.class, NullPointerTester.Visibility.PACKAGE);
    }

    @Test
    @DisplayName("match the same type against itself")
    void matchSameTypes() {
        assertSameTypeMatches(TypeToken.of(UserId.class));
        assertSameTypeMatches(new TypeToken<List<EventMessage>>() {});
        assertSameTypeMatches(new TypeToken<Pair<UserId, EventMessage>>() {});
        assertSameTypeMatches(
                new TypeToken<Triplet<UserId, EventMessage, Optional<EventMessage>>>() {});
    }

    @Test
    @DisplayName("do not match the type against " +
            "another one being neither a subclass nor of the same type")
    void notMatchDifferentTypes() {
        assertThat(matches(TypeToken.of(UserId.class),
                           TypeToken.of(NoReaction.class))
        ).isFalse();

        assertThat(matches(new TypeToken<List<EventMessage>>() {},
                           new TypeToken<Set<EventMessage>>() {})
        ).isFalse();
    }

    @Test
    @DisplayName("match the same type against subtypes")
    void matchSubtypes() {
        assertThat(matches(TypeToken.of(Message.class),
                           TypeToken.of(UserId.class))
        ).isTrue();

        assertThat(matches(new TypeToken<Collection<EventMessage>>() {},
                           new TypeToken<Set<EventMessage>>() {})
        ).isTrue();
    }

    @Test
    @DisplayName("do not match a type against the same type or its descendant, " +
            "but with an incompatible generic parameter")
    void doNotMatchSubtypesWithDifferentGenerics() {

        assertThat(matches(new TypeToken<Collection<EventMessage>>() {},
                           new TypeToken<Collection<CommandMessage>>() {})
        ).isFalse();

        assertThat(matches(new TypeToken<Collection<EventMessage>>() {},
                           new TypeToken<Set<CommandMessage>>() {})
        ).isFalse();

        assertThat(
                matches(new TypeToken<Iterable<Message>>() {},
                        new TypeToken<Pair<UserId, Object>>() {})
        ).isFalse();
    }

    @Test
    @DisplayName(", if the expected type has more than one generic," +
            " match the generics of actual type position by position")
    void matchGenericByGeneric() {

        assertThat(matches(new TypeToken<Map<Object, EventMessage>>() {},
                           new TypeToken<Map<String, NoReaction>>() {})
        ).isTrue();

        assertThat(matches(new TypeToken<Map<Object, EventMessage>>() {},
                           new TypeToken<Map<String, CommandMessage>>() {})
        ).isFalse();

        assertThat(matches(new TypeToken<Triplet<EventMessage, EventMessage, EventMessage>>() {},
                           new TypeToken<Triplet<NoReaction, Object, Object>>() {})
        ).isFalse();
    }

    @DisplayName(", if expected and actual types have `Optional` generic parameters," +
            "unpack the generic type of `Optional`s and take them into account")
    @Test
    void unpackOptionalIfBothDefineGenericParams() {
        assertThat(
                matches(new TypeToken<Optional<EventMessage>>() {},
                        new TypeToken<Optional<NoReaction>>() {})
        ).isTrue();

        assertThat(
                matches(new TypeToken<Optional<EventMessage>>() {},
                        new TypeToken<Optional<DoNothing>>() {})
        ).isFalse();

        assertThat(
                matches(new TypeToken<Iterable<Message>>() {},
                        new TypeToken<Triplet<UserId, EventMessage, Optional<EventMessage>>>() {})
        ).isTrue();

        assertThat(
                matches(new TypeToken<Iterable<Message>>() {},
                        new TypeToken<Triplet<UserId, EventMessage, Optional<Object>>>() {})
        ).isFalse();

        assertThat(
                matches(new TypeToken<Triplet<EventMessage, EventMessage, EventMessage>>() {},
                        new TypeToken<Triplet<EventMessage, EventMessage, Optional<NoReaction>>>() {
                        })
        ).isTrue();
    }

    @DisplayName(", if the expected type has no generic parameters, " +
            "not take `Optional` generic parameter of actual type into account")
    @Test
    void notUnpackOptionalIfExpectedHasNoGenerics() {
        assertThat(
                matches(TypeToken.of(EventMessage.class),
                        new TypeToken<Optional<NoReaction>>() {})
        ).isFalse();
    }

    private static void assertSameTypeMatches(TypeToken<?> type) {
        assertThat(matches(type, type)).isTrue();
    }
}
