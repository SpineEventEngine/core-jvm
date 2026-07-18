/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.event.model;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import io.spine.base.CommandMessage;
import io.spine.base.EventMessage;
import io.spine.base.RejectionMessage;
import io.spine.core.CommandContext;
import io.spine.core.EventContext;
import io.spine.server.model.AllowedParams;
import io.spine.server.model.ExtractedArguments;
import io.spine.server.model.MethodParams;
import io.spine.server.model.ParameterSpec;
import io.spine.server.model.TypeMatcher;
import io.spine.server.type.EventEnvelope;

import static io.spine.server.model.ExtractedArguments.ofOne;
import static io.spine.server.model.ExtractedArguments.ofTree;
import static io.spine.server.model.ExtractedArguments.ofTwo;
import static io.spine.server.model.TypeMatcher.classImplementing;
import static io.spine.server.model.TypeMatcher.exactly;

/**
 * Allowed combinations of parameters for the methods that accept {@code Event}s.
 */
@Immutable
enum EventAcceptingMethodParams implements ParameterSpec<EventEnvelope> {

    MESSAGE(
            classImplementing(EventMessage.class)
    ) {
        @Override
        public ExtractedArguments extractArguments(EventEnvelope event) {
            return ofOne(event.message());
        }
    },

    MESSAGE_EVENT_CTX(
            classImplementing(EventMessage.class),
            exactly(EventContext.class)
    ) {
        @Override
        public ExtractedArguments extractArguments(EventEnvelope event) {
            return ofTwo(event.message(), event.context());
        }
    },

    MESSAGE_COMMAND_CTX(
            classImplementing(RejectionMessage.class),
            exactly(CommandContext.class)
    ) {
        @Override
        public ExtractedArguments extractArguments(EventEnvelope event) {
            var re = new RejectionEnvelope(event);
            return ofTwo(re.rejectionMessage(), re.commandContext());
        }
    },

    MESSAGE_COMMAND_MSG(
            classImplementing(RejectionMessage.class),
            classImplementing(CommandMessage.class)
    ) {
        @Override
        public boolean acceptsCommand() {
            return true;
        }

        @Override
        public ExtractedArguments extractArguments(EventEnvelope event) {
            var re = new RejectionEnvelope(event);
            return ofTwo(re.rejectionMessage(), re.commandMessage());
        }
    },

    MESSAGE_COMMAND_MSG_COMMAND_CTX(
            classImplementing(RejectionMessage.class),
            classImplementing(CommandMessage.class),
            exactly(CommandContext.class)
    ) {
        @Override
        public boolean acceptsCommand() {
            return true;
        }

        @Override
        public ExtractedArguments extractArguments(EventEnvelope event) {
            var re = new RejectionEnvelope(event);
            return ofTree(re.rejectionMessage(), re.commandMessage(), re.commandContext());
        }
    };

    private static final AllowedParams<EventEnvelope> PARAMS = new AllowedParams<>(values());

    private final ImmutableList<TypeMatcher> criteria;

    EventAcceptingMethodParams(TypeMatcher... criteria) {
        this.criteria = ImmutableList.copyOf(criteria);
    }

    /**
     * Obtains specification of parameters allowed for event-handling methods.
     */
    static AllowedParams<EventEnvelope> allowed() {
        return PARAMS;
    }

    @Override
    public boolean matches(MethodParams params) {
        return params.match(criteria);
    }

    /**
     * Verifies if command message is one of the expected parameters.
     */
    public boolean acceptsCommand() {
        return false;
    }
}
