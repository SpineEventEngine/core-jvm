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

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Traverser;
import com.google.common.reflect.TypeToken;
import io.spine.base.CommandMessage;
import io.spine.base.EventMessage;
import io.spine.reflect.Types;
import io.spine.server.event.NoReaction;
import io.spine.server.type.CommandClass;
import io.spine.server.type.EventClass;
import io.spine.type.MessageClass;

import java.lang.reflect.Method;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Streams.stream;
import static io.spine.util.Exceptions.newIllegalArgumentException;

/**
 * Obtains a set of command or event types produced by a {@link Receptor}.
 *
 * <p>The set contains <em>class</em> information collected from method signatures.
 * If a method result refers to an interface (directly or as a generic parameter), this
 * interface is not added to the set. Thus, if a method returns {@code List<EventMessage>},
 * the set would be empty.
 */
final class MethodResults {

    /** Prevents instantiation of this utility class. */
    private MethodResults() {
    }

    /**
     * Collects command or event classes declared in the return type of the handler method.
     *
     * <p>If the method returns a parameterized type like {@link Iterable}, the produced messages
     * are gathered from its generic arguments. Generic arguments are traversed recursively enabling
     * correct parsing of return types like {@code Pair<ProjectCreated, Optional<ProjectStarted>>}.
     *
     * <p>Too broad types are ignored, so methods returning something like
     * {@code Optional<Message>} are deemed producing no types.
     *
     * @param method
     *         the handler method
     * @param <P>
     *         the type of the produced message classes
     * @return the set of message classes produced by the method
     */
    @SuppressWarnings("unchecked") /* The cast to `<P>` is a convenience for calling sites, and
        is safe as all handler methods in the model produce either commands OR events.
        The method is thus parameterized with a produced message class, and the runtime checks
        are only used for convenience. */
    static <P extends MessageClass<?>> ImmutableSet<P> collectMessageClasses(Method method) {
        checkNotNull(method);
        var returnType = method.getGenericReturnType();
        var allTypes = Traverser.forTree(Types::resolveArguments)
                                .breadthFirst(returnType);
        var result = stream(allTypes)
                .map(TypeToken::of)
                .map(TypeToken::getRawType)
                .filter(MethodResults::isCommandOrEvent)
                .map(c -> (P) toMessageClass(c))
                .collect(toImmutableSet());
        return result;
    }

    /**
     * Checks if the class is a concrete {@linkplain CommandMessage command} or
     * {@linkplain EventMessage event}.
     */
    @SuppressWarnings("deprecation") // Handling of `Nothing` is kept for backward compatibility.
    private static boolean isCommandOrEvent(Class<?> cls) {
        if (cls.isInterface()) {
            return false;
        }
        if (Nothing.class.equals(cls) || NoReaction.class.equals(cls)) {
            return false;
        }
        var isCommandOrEvent = CommandMessage.class.isAssignableFrom(cls)
                || EventMessage.class.isAssignableFrom(cls);
        return isCommandOrEvent;
    }

    /**
     * Converts the command or event class to the corresponding {@link MessageClass}.
     */
    @SuppressWarnings("unchecked") // checked by `isAssignableFrom()`
    private static MessageClass<?> toMessageClass(Class<?> cls) {
        if (CommandMessage.class.isAssignableFrom(cls)) {
            return CommandClass.from((Class<? extends CommandMessage>) cls);
        }
        if (EventMessage.class.isAssignableFrom(cls)) {
            return EventClass.from((Class<? extends EventMessage>) cls);
        }
        throw newIllegalArgumentException(
                "A given type `%s` is neither a command nor an event.",
                cls.getCanonicalName()
        );
    }
}
