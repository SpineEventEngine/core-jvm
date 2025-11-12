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

package io.spine.server.event.model;

import io.spine.base.EventMessage;
import io.spine.core.UserId;
import io.spine.server.event.EventReactor;
import io.spine.server.event.model.given.reactor.RcIterableReturn;
import io.spine.server.event.model.given.reactor.RcOneParam;
import io.spine.server.event.model.given.reactor.RcReturnOptional;
import io.spine.server.event.model.given.reactor.RcReturnOptionalRejection;
import io.spine.server.event.model.given.reactor.RcReturnPair;
import io.spine.server.event.model.given.reactor.RcReturnRejection;
import io.spine.server.event.model.given.reactor.RcReturnRejectionIterable;
import io.spine.server.event.model.given.reactor.RcTwoParams;
import io.spine.server.event.model.given.reactor.RcWrongAnnotation;
import io.spine.server.event.model.given.reactor.RcWrongFirstParam;
import io.spine.server.event.model.given.reactor.RcWrongNoAnnotation;
import io.spine.server.event.model.given.reactor.RcWrongNoParam;
import io.spine.server.event.model.given.reactor.RcWrongSecondParam;
import io.spine.server.event.model.given.reactor.TestEventReactor;
import io.spine.server.model.SignatureMismatchException;
import io.spine.server.type.EventEnvelope;
import io.spine.test.reflect.ProjectId;
import io.spine.test.reflect.event.RefProjectAssigned;
import io.spine.test.reflect.event.RefProjectCreated;
import io.spine.test.reflect.event.RefProjectStarted;
import io.spine.testing.server.TestEventFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.testing.TestValues.randomString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`EventReactorMethod` should")
@SuppressWarnings("InnerClassMayBeStatic")
class EventReactorMethodTest {

    private static final EventReactorSignature signature = new EventReactorSignature();

    private static void assertValid(Method rawMethod) {
        assertTrue(signature.matches(rawMethod));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")  // It's fine as it throws an exception.
    private static void assertInvalid(Method method) {
        assertThrows(SignatureMismatchException.class, () -> signature.matches(method));
    }


    @Nested
    @DisplayName("consider reactor method valid with")
    class MethodArguments {

        @Test
        @DisplayName("one event message parameter")
        void oneParam() {
            var method = new RcOneParam().getMethod();
            assertValid(method);
        }

        @Test
        @DisplayName("event message and context")
        void twoParams() {
            var method = new RcTwoParams().getMethod();
            assertValid(method);
        }
    }

    @Nested
    @DisplayName("support `Message` return type")
    class MessageReturn {

        @Test
        @DisplayName("in predicate")
        void predicate() {
            var method = new RcOneParam().getMethod();
            assertValid(method);
        }
    }

    @Nested
    @DisplayName("support `Iterable` return type")
    class ReturnValues {

        @Test
        @DisplayName("in predicate")
        void predicate() {
            var method = new RcIterableReturn().getMethod();
            assertValid(method);
        }
    }

    @Nested
    @DisplayName("support `Optional` return type")
    class OptionalReturn {

        private EventReactor target;
        private Method rawMethod;
        private EventReactorMethod method;

        @BeforeEach
        void setUp() {
            target = new RcReturnOptional();
            rawMethod = ((TestEventReactor) target).getMethod();
            method = createMethod(rawMethod);
        }

        @Test
        @DisplayName("in predicate")
        void inPredicate() {
            assertValid(rawMethod);
        }

        @Test
        @DisplayName("in factory")
        void inFactory() {
            assertThat(method).isNotNull();
        }

        @Test
        @DisplayName("when returning value")
        void returnValue() {
            var event = projectCreatedEvent();

            var outcome = method.invoke(target, envelope(event));
            var events = outcome.getSuccess()
                                .getProducedEvents()
                                .getEventList();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).enclosedMessage())
                    .isEqualTo(RefProjectStarted.newBuilder()
                                       .setProjectId(event.getProjectId())
                                       .build());
        }

        @Test
        @DisplayName("when returning Optional.empty()")
        void returnEmpty() {
            // Passing event with this ID should return `Optional.empty()`.
            var event = RefProjectCreated.newBuilder()
                    .setProjectId(ProjectId.newBuilder().setId("xxx-xxx"))
                    .build();

            var outcome = method.invoke(target, envelope(event));

            assertThat(outcome.getSuccess().getProducedEvents().getEventList()).isEmpty();
        }
    }

    @Nested
    @DisplayName("support `Pair` return type")
    class PairReturn {

        private EventReactor target;
        private Method rawMethod;
        private EventReactorMethod method;

        @BeforeEach
        void setUp() {
            target = new RcReturnPair();
            rawMethod = ((TestEventReactor) target).getMethod();
            method = createMethod(rawMethod);
        }

        @Test
        @DisplayName("in predicate")
        void inPredicate() {
            assertValid(rawMethod);
        }

        @Test
        @DisplayName("in factory")
        void inFactory() {
            assertThat(method).isNotNull();
        }

        @Test
        @DisplayName("when returning `Pair` with two non-null values")
        void returningNonNull() {
            var event = projectCreatedWithAssignee();
            var outcome = method.invoke(target, envelope(event));
            var events = outcome.getSuccess()
                                .getProducedEvents()
                                .getEventList();
            assertThat(events).hasSize(2);
            assertThat(events.get(0)
                             .enclosedMessage())
                    .isEqualTo(RefProjectStarted.newBuilder()
                                       .setProjectId(event.getProjectId())
                                       .build());
            assertThat(events.get(1).enclosedMessage())
                    .isEqualTo(RefProjectAssigned.newBuilder()
                                       .setProjectId(event.getProjectId())
                                       .setAssignee(event.getAssignee())
                                       .build());
        }

        @Test
        @DisplayName("when returning `Pair` with null second value")
        void returningSecondNull() {
            var event = projectCreatedEvent();
            var outcome = method.invoke(target, envelope(event));

            var events = outcome.getSuccess()
                                .getProducedEvents()
                                .getEventList();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).enclosedMessage())
                    .isEqualTo(RefProjectStarted.newBuilder()
                                       .setProjectId(event.getProjectId())
                                       .build());
        }
    }

    @Nested
    @DisplayName("consider a method invalid if")
    class NotReactor {

        @Test
        @DisplayName("no annotation is provided")
        void noAnnotation() {
            var method = new RcWrongNoAnnotation().getMethod();
            assertFalse(signature.matches(method));
        }

        @Test
        @DisplayName("wrong annotations provided")
        void wrongAnnotations() {
            var method = new RcWrongAnnotation().getMethod();
            assertFalse(signature.matches(method));
        }

        @Test
        @DisplayName("it has no parameters")
        void noParameters() {
            var method = new RcWrongNoParam().getMethod();
            assertInvalid(method);
        }

        @Test
        @DisplayName("the first parameter is not `Message`")
        void notMessageParam() {
            var method = new RcWrongFirstParam().getMethod();
            assertInvalid(method);
        }

        @Test
        @DisplayName("the second parameter is not `EventContext`")
        void notContextParam() {
            var method = new RcWrongSecondParam().getMethod();
            assertInvalid(method);
        }

        @Test
        @DisplayName("it returns a `RejectionMessage`")
        void returnRejection() {
            var method = new RcReturnRejection().getMethod();
            assertInvalid(method);
        }

        @Test
        @DisplayName("it returns an Iterable of `RejectionMessage`s")
        void returnRejectionIterable() {
            var method = new RcReturnRejectionIterable().getMethod();
            assertInvalid(method);
        }

        @Test
        @DisplayName("it returns an Optional `RejectionMessage`")
        void returnOptionalRejection() {
            var method = new RcReturnOptionalRejection().getMethod();
            assertInvalid(method);
        }
    }

    private static RefProjectCreated projectCreatedEvent() {
        var projectId = ProjectId.newBuilder()
                .setId(randomString())
                .build();
        var result = RefProjectCreated.newBuilder()
                .setProjectId(projectId)
                .build();
        return result;
    }

    private static RefProjectCreated projectCreatedWithAssignee() {
        var projectId = ProjectId.newBuilder()
                .setId(randomString())
                .build();
        var userId = UserId.newBuilder()
                .setValue(randomString())
                .build();
        var result = RefProjectCreated.newBuilder()
                .setProjectId(projectId)
                .setAssignee(userId)
                .build();
        return result;
    }

    private static EventReactorMethod createMethod(Method method) {
        var found = signature.classify(method);
        assertTrue(found.isPresent());
        return found.get();
    }

    private static EventEnvelope envelope(EventMessage eventMessage) {
        var factory = TestEventFactory.newInstance(EventReactorMethodTest.class);
        var event = factory.createEvent(eventMessage);
        var envelope = EventEnvelope.of(event);
        return envelope;
    }
}
