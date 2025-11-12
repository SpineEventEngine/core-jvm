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

package io.spine.server.command.model;

import io.spine.base.EventMessage;
import io.spine.core.Command;
import io.spine.server.command.model.given.reaction.ReEitherWithNothing;
import io.spine.server.command.model.given.reaction.ReOneParam;
import io.spine.server.command.model.given.reaction.ReOptionalResult;
import io.spine.server.command.model.given.reaction.ReTwoParams;
import io.spine.server.command.model.given.reaction.TestCommandReactor;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.event.EventReceiver;
import io.spine.server.type.EventEnvelope;
import io.spine.test.command.CmdAddTask;
import io.spine.test.command.ProjectId;
import io.spine.test.command.event.CmdProjectCreated;
import io.spine.testing.server.TestEventFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`CommandReactionMethod` should")
@SuppressWarnings("InnerClassMayBeStatic")
class CommandingReactionTest {

    private static final
    CommandReactionSignature signature = new CommandReactionSignature();

    private static void assertValid(Method rawMethod, boolean isValid) {
        assertThat(signature.matches(rawMethod)).isEqualTo(isValid);
    }

    @Nested
    @DisplayName("consider command reaction valid with")
    class MethodArguments {

        @Test
        @DisplayName("one event message parameter")
        void oneParam() {
            var method = new ReOneParam().getMethod();
            assertValid(method, true);
        }

        @Test
        @DisplayName("event message and context")
        void twoParams() {
            var method = new ReTwoParams().getMethod();
            assertValid(method, true);
        }
    }

    @Nested
    @DisplayName("support `Message` return type")
    class MessageReturn {

        private EventReceiver target;
        private Method rawMethod;
        private CommandingReaction method;
        private ProjectId id;

        @BeforeEach
        void setUp() {
            target = new ReOneParam();
            rawMethod = ((TestCommandReactor) target).getMethod();
            var result = signature.classify(rawMethod);
            assertTrue(result.isPresent());
            this.method = result.get();
            id = ProjectId.newBuilder()
                    .setId(newUuid())
                    .build();
        }

        @Test
        @DisplayName("in predicate")
        void predicate() {
            assertValid(rawMethod, true);
        }

        @Test
        @DisplayName("in factory")
        void factory() {
            assertThat(rawMethod).isNotNull();
        }

        @Test
        @DisplayName("when returning value")
        void returnValue() {
            var message = createInitEvent(id);
            var outcome = method.invoke(target, envelope(message));
            assertResult(outcome, this.id);
        }
    }

    /**
     * Abstract base for tests checking empty method result.
     */
    abstract static class EmptyReturn {

        private final Supplier<TestCommandReactor> supplier;

        private TestCommandReactor target;
        private Method rawMethod;
        private CommandingReaction method;

        private ProjectId id;

        EmptyReturn(Supplier<TestCommandReactor> supplier) {
            this.supplier = supplier;
        }

        @SuppressWarnings("OptionalGetWithoutIsPresent") // OK as we surely get the result
        @BeforeEach
        void setUp() {
            target = supplier.get();
            rawMethod = target.getMethod();
            method = signature.classify(rawMethod).get();
            id = ProjectId.newBuilder()
                          .setId(newUuid())
                          .build();
        }

        @Test
        @DisplayName("in predicate")
        void inPredicate() {
            var result = signature.matches(rawMethod);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("in factory")
        void inFactory() {
            assertThat(method).isNotNull();
        }

        @Test
        @DisplayName("when returning value")
        void returnValue() {
            var givenId = this.id;
            var message = createInitEvent(givenId);

            var outcome = method.invoke(target, envelope(message));

            assertResult(outcome, givenId);
        }

        @Test
        void returnEmpty() {
            var message = createVoidEvent(id);

            var outcome = method.invoke(target, envelope(message));

            assertThat(outcome.getSuccess().hasCommands())
                    .isFalse();
        }

        private static CmdProjectCreated createVoidEvent(ProjectId givenId) {
            return CmdProjectCreated.newBuilder()
                    .setProjectId(givenId)
                    .setInitialize(false) // This will make the method return `Optional.empty()`.
                    .build();
        }
    }

    @Nested
    @DisplayName("support `Optional` return value")
    class OptionalReturn extends EmptyReturn {

        OptionalReturn() {
            super(ReOptionalResult::new);
        }

        @Test
        @Override
        @DisplayName("when returning `Optional.empty()`")
        void returnEmpty() {
            super.returnEmpty();
        }
    }

    @Nested
    @DisplayName("support `Either` with `Nothing`")
    class EitherReturn extends EmptyReturn {

        EitherReturn() {
            super(ReEitherWithNothing::new);
        }

        @Test
        @Override
        @DisplayName("when returning `Either` with `Nothing`")
        void returnEmpty() {
            super.returnEmpty();
        }
    }

    /**
     * Creates the event message which will cause the handling method return non-empty result.
     */
    private static CmdProjectCreated createInitEvent(ProjectId givenId) {
        return CmdProjectCreated.newBuilder()
                .setProjectId(givenId)
                .setInitialize(true) // This will make the method return result with a value.
                .build();
    }

    /**
     * Asserts that the result has a message with correct type and passed field value.
     */
    private static void assertResult(DispatchOutcome outcome, ProjectId id) {
        var expected = CmdAddTask.newBuilder()
                .setProjectId(id)
                .build();
        assertTrue(outcome.hasSuccess());
        var success = outcome.getSuccess();
        assertTrue(success.hasCommands());
        var commands = success.getProducedCommands()
                              .getCommandList();
        var commandMessages = commands.stream()
                .map(Command::enclosedMessage)
                .collect(toList());
        var assertThat = assertThat(commandMessages);
        assertThat.hasSize(1);
        assertThat.containsExactly(expected);
    }

    private static EventEnvelope envelope(EventMessage eventMessage) {
        var factory = TestEventFactory.newInstance(CommandingReactionTest.class);
        var event = factory.createEvent(eventMessage);
        var envelope = EventEnvelope.of(event);
        return envelope;
    }
}
