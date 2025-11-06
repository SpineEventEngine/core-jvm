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

package io.spine.server.type;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import io.spine.annotation.Internal;
import io.spine.base.EventMessage;
import io.spine.base.RejectionMessage;
import io.spine.base.RejectionThrowable;
import io.spine.core.Event;
import io.spine.system.server.event.EntityStateChanged;
import io.spine.type.MessageClass;
import io.spine.type.TypeUrl;

import java.io.Serial;
import java.lang.reflect.Method;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.core.Events.ensureMessage;
import static io.spine.util.Exceptions.illegalStateWithCauseOf;

/**
 * A value object holding a class of events.
 */
public final class EventClass extends MessageClass<EventMessage> {

    @Serial
    private static final long serialVersionUID = 0L;
    
    /**
     * The name of the {@link RejectionThrowable#messageThrown()} method.
     *
     * @see #from(Class)
     */
    private static final String MESSAGE_THROWN_METHOD = "messageThrown";

    private EventClass(Class<? extends EventMessage> value) {
        super(value);
    }

    private EventClass(Class<? extends EventMessage> value, TypeUrl typeUrl) {
        super(value, typeUrl);
    }

    /**
     * Creates a new instance of the event class.
     *
     * @param rawClass
      *     a class of an event message or a rejection message
     * @return new instance
     */
    public static EventClass from(Class<? extends EventMessage> rawClass) {
        return new EventClass(checkNotNull(rawClass));
    }

    /**
     * Obtains the class of the rejection by the class of corresponding throwable message.
     */
    public static EventClass fromThrowable(Class<? extends RejectionThrowable> cls) {
        Method messageThrownMethod;
        try {
            messageThrownMethod = cls.getMethod(MESSAGE_THROWN_METHOD);
        } catch (NoSuchMethodException e) {
            throw illegalStateWithCauseOf(e);
        }
        @SuppressWarnings("unchecked") // Safe as declared by `RejectionThrowable.messageThrown`.
        var returnType = (Class<? extends RejectionMessage>)
                messageThrownMethod.getReturnType();
        return from(returnType);
    }

    /**
     * Creates a new {@code EventClass} instance from the passed type URL.
     *
     * @throws IllegalArgumentException
     *         if the passed {@code TypeUrl} does not represent an event type
     */
    @SuppressWarnings("unchecked") // Logically checked.
    public static EventClass from(TypeUrl typeUrl) {
        Class<? extends Message> messageClass = typeUrl.getMessageClass();
        checkArgument(EventMessage.class.isAssignableFrom(messageClass),
                      "Event class cannot be constructed from non-EventMessage type URL: `%s`.",
                      typeUrl.value());
        return new EventClass((Class<? extends EventMessage>) messageClass, typeUrl);
    }

    /**
     * Creates a new {@code EventClass} from the given event.
     *
     * <p>Named {@code from} to avoid collision with {@link #of(Message)}.
     */
    public static EventClass from(Event event) {
        return from(event.enclosedTypeUrl());
    }

    /**
     * Creates a new instance of the event class by passed event instance.
     *
     * <p>If an instance of {@link Event} is passed to this method, enclosing event message will be
     * un-wrapped to determine the class of the event.
     *
     * <p>If an instance of {@link Any} is passed, it will be unpacked, and the class of the wrapped
     * message will be used.
     *
     * @param eventOrMessage an event message, or {@link Any}, {@link Event}
     * @return new instance
     */
    public static EventClass of(Message eventOrMessage) {
        var eventMessage = ensureMessage(eventOrMessage);
        var typeUrl = eventMessage.typeUrl();
        return new EventClass(eventMessage.getClass(), typeUrl);
    }

    /**
     * Creates a set of {@code EventClass} from the passed set.
     */
    public static ImmutableSet<EventClass> setOf(Iterable<Class<? extends EventMessage>> classes) {
        ImmutableSet.Builder<EventClass> builder = ImmutableSet.builder();
        for (var cls : classes) {
            builder.add(from(cls));
        }
        return builder.build();
    }

    /**
     * Creates a set of {@code EventClass} from the passed classes.
     */
    @SafeVarargs
    public static ImmutableSet<EventClass> setOf(Class<? extends EventMessage>... classes) {
        return setOf(Arrays.asList(classes));
    }

    /**
     * Creates a set with only one passed {@code EventClass}.
     */
    public static ImmutableSet<EventClass> setOf(Class<? extends EventMessage> cls) {
        return ImmutableSet.of(from(cls));
    }

    /**
     * Returns an empty {@code EventClass} set.
     */
    public static ImmutableSet<EventClass> emptySet(){
        return ImmutableSet.of();
    }

    /**
     * Checks if this entity class represents {@link EntityStateChanged}.
     */
    @Internal
    public boolean isEntityStateChanged() {
        return value().equals(EntityStateChanged.class);
    }
}
