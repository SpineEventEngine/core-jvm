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

package io.spine.testing.server;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Any;
import io.spine.base.SignalMessage;
import io.spine.core.Signal;
import io.spine.testing.SubjectTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static com.google.common.truth.ExpectFailure.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.testing.server.EmittedMessageSubject.FactKey.ACTUAL;
import static io.spine.testing.server.EmittedMessageSubject.FactKey.MESSAGE_COUNT;
import static io.spine.testing.server.EmittedMessageSubject.FactKey.REQUESTED_INDEX;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.generate;

abstract class EmittedMessageSubjectTest<S extends EmittedMessageSubject<S, W, M>,
                                         W extends Signal<?, ?, ?>,
                                         M extends SignalMessage>
        extends SubjectTest<S, Iterable<W>> {

    abstract W createMessage();

    abstract W createAnotherMessage();

    private Iterable<W> messages(int messageCount) {
        Supplier<W> supplier = this::createMessage;
        return messages(messageCount, supplier);
    }

    private Iterable<W> messages(int messageCount, Supplier<W> supplier) {
        return generate(supplier)
                .limit(messageCount)
                .collect(toList());
    }

    @Test
    @DisplayName("check message count")
    void checkSize() {
        var messageCount = 42;
        var messages = messages(messageCount);
        assertWithSubjectThat(messages).hasSize(messageCount);
        var error = expectFailure(
                whenTesting -> whenTesting.that(messages)
                                          .hasSize(0)
        );
        var assertError = assertThat(error);
        assertError.factValue(EXPECTED)
                   .isEqualTo(String.valueOf(0));
        assertError.factValue(BUT_WAS)
                   .isEqualTo(String.valueOf(messageCount));
    }

    @Test
    @DisplayName("check if there is no messages")
    void checkIfAbsent() {
        var messageCount = 0;
        var messages = messages(messageCount);
        assertWithSubjectThat(messages).isEmpty();
        expectSomeFailure(whenTesting -> whenTesting.that(messages)
                                                    .isNotEmpty());
    }

    @Test
    @DisplayName("check if there are some messages")
    void checkIfPresent() {
        var messageCount = 1;
        var messages = messages(messageCount);
        assertWithSubjectThat(messages).isNotEmpty();
        expectSomeFailure(whenTesting -> whenTesting.that(messages)
                                                    .isEmpty());
    }

    @Test
    @DisplayName("retrieve a subject of an emitted message by its index")
    void retrieveMessage() {
        var messageCount = 3;
        var messages = messages(messageCount);
        var protoSubject = assertWithSubjectThat(messages).message(2);
        assertThat(protoSubject).isNotNull();
        protoSubject.isNotEqualToDefaultInstance();
        protoSubject.isNotInstanceOf(Any.class);
        protoSubject.isNotInstanceOf(Signal.class);
    }

    @Test
    @DisplayName("check the message count when obtaining a `ProtoSubject`")
    void failOnIndexOutOfBounds() {
        var messageCount = 5;
        var messages = messages(messageCount);
        var index = 13;
        @SuppressWarnings("CheckReturnValue")
        var error = expectFailure(whenTesting -> whenTesting.that(messages)
                                                            .message(index));
        var assertError = assertThat(error);
        assertError.factValue(MESSAGE_COUNT.value())
                   .isEqualTo(String.valueOf(messageCount));
        assertError.factValue(REQUESTED_INDEX.value())
                   .isEqualTo(String.valueOf(index));
    }

    @Test
    @DisplayName("fail to get a message if the list is `null`")
    void failForNull() {
        var error = expectFailure(whenTesting -> whenTesting.that(null)
                                                            .message(42));
        assertThat(error).factKeys()
                         .contains(ACTUAL.value());
    }

    @Test
    @DisplayName("obtain derived subject with messages of type")
    void subSubjectByType() {
        var messageCount = 3;
        var otherMessageCount = 2;
        Iterable<W> outerObjects = ImmutableList
                .<W>builder()
                .addAll(messages(messageCount))
                .addAll(messages(otherMessageCount, this::createAnotherMessage))
                .build();

        var type = typeOf(createMessage());
        var anotherType = typeOf(createAnotherMessage());

        var subject = assertWithSubjectThat(outerObjects);

        var subSubject = subject.withType(type);
        subSubject.hasSize(messageCount);

        var anotherSubSubject = subject.withType(anotherType);
        anotherSubSubject.hasSize(otherMessageCount);
    }

    @Test
    @DisplayName("fail when trying to obtain filtered sub-subject over null actual")
    void failWithNull() {
        var type = typeOf(createMessage());
        @SuppressWarnings("CheckReturnValue") /* The call to `withType()` should fail,
            we don't need its result. */
        var error = expectFailure(whenTesting -> whenTesting.that(null)
                                                                    .withType(type));
        var assertError = assertThat(error);
        assertError.factValue(ACTUAL.value())
                   .isEqualTo("null");
    }

    private Class<M> typeOf(W outerObject) {
        @SuppressWarnings("unchecked") /* The cast is protected by matching outer type
            (such as `Event` or `Command`) to corresponding enclosed message type (such as
            `EventMessage` or `CommandMessage`) in the generic parameters of the derived classes. */
        var result = (Class<M>) outerObject.enclosedMessage().getClass();
        return result;
    }
}
