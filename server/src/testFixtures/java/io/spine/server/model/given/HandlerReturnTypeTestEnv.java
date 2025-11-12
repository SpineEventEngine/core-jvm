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

package io.spine.server.model.given;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import io.spine.base.EventMessage;
import io.spine.base.RejectionMessage;
import io.spine.server.event.NoReaction;
import io.spine.server.tuple.EitherOf2;
import io.spine.server.tuple.EitherOf3;
import io.spine.server.tuple.Pair;
import io.spine.test.model.ModCreateProject;
import io.spine.test.model.ModProjectCreated;
import io.spine.test.model.ModProjectOwnerAssigned;
import io.spine.test.model.ModProjectStarted;
import io.spine.test.model.ModStartProject;
import io.spine.test.model.Rejections.ModCannotAssignOwnerToProject;
import io.spine.test.model.Rejections.ModProjectAlreadyExists;

import java.util.List;
import java.util.Optional;

import static io.spine.base.Identifier.newUuid;

public final class HandlerReturnTypeTestEnv {

    /** Prevents instantiation of this test environment class. */
    private HandlerReturnTypeTestEnv() {
    }

    @SuppressWarnings("unused") // Reflective access only.
    public static class MessageProducer {

        /** Prevents instantiation of this reflective-access-only class. */
        private MessageProducer() {
        }

        public ModCreateProject emitCommand() {
            return ModCreateProject.getDefaultInstance();
        }

        public ModProjectCreated emitEvent() {
            return ModProjectCreated.getDefaultInstance();
        }

        public Optional<ModProjectStarted> emitOptionalEvent() {
            return Optional.of(ModProjectStarted.getDefaultInstance());
        }

        public List<ModStartProject> emitListOfCommands() {
            return ImmutableList.of();
        }

        public EitherOf2<ModProjectCreated, ModProjectAlreadyExists> emitEither() {
            return EitherOf2.withA(ModProjectCreated.getDefaultInstance());
        }

        public Pair<ModProjectCreated,
                EitherOf2<ModProjectOwnerAssigned, ModCannotAssignOwnerToProject>>
        emitPair() {
            var projectCreated = ModProjectCreated.newBuilder()
                    .setId(newUuid())
                    .build();
            EitherOf2<ModProjectOwnerAssigned, ModCannotAssignOwnerToProject> either =
                    EitherOf2.withA(ModProjectOwnerAssigned.getDefaultInstance());
            return Pair.withEither(projectCreated, either);
        }

        public EitherOf3<ModProjectOwnerAssigned, ModCannotAssignOwnerToProject, RejectionMessage>
        emitEitherWithTooBroad() {
            return EitherOf3.withA(ModProjectOwnerAssigned.getDefaultInstance());
        }

        public void returnVoid() {
        }

        public NoReaction returnNothing() {
            return NoReaction.getDefaultInstance();
        }

        public Empty returnEmpty() {
            return Empty.getDefaultInstance();
        }

        public EventMessage returnTooBroadEvent() {
            return ModProjectCreated.getDefaultInstance();
        }

        public Iterable<Message> returnTooBroadIterable() {
            return ImmutableList.of();
        }

        public int returnRandomType() {
            return 42;
        }
    }
}
