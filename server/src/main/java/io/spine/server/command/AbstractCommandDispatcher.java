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

package io.spine.server.command;

import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.protobuf.Any;
import io.spine.base.Error;
import io.spine.core.Event;
import io.spine.core.MessageId;
import io.spine.protobuf.TypeConverter;
import io.spine.server.BoundedContext;
import io.spine.server.ContextAware;
import io.spine.server.Identity;
import io.spine.server.commandbus.CommandDispatcher;
import io.spine.server.event.EventBus;
import io.spine.server.type.SignalEnvelope;
import io.spine.system.server.HandlerFailedUnexpectedly;
import io.spine.system.server.SystemWriteSide;
import io.spine.system.server.event.CommandRejected;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.function.Supplier;

import static com.google.common.base.Suppliers.memoize;
import static io.spine.protobuf.AnyPacker.pack;

/**
 * The abstract base for non-aggregate classes that dispatch commands to their methods
 * and post resulting events to {@link EventBus}.
 */
public abstract class AbstractCommandDispatcher implements CommandDispatcher, ContextAware {

    /** The {@code EventBus} to which the dispatcher posts events it produces. */
    @LazyInit
    private @MonotonicNonNull EventBus eventBus;
    @LazyInit
    private @MonotonicNonNull SystemWriteSide system;

    /** Supplier for a packed version of the dispatcher ID. */
    private final Supplier<Any> producerId =
            memoize(() -> pack(TypeConverter.toMessage(id())));
    private final Supplier<MessageId> eventAnchor =
            memoize(() -> Identity.ofProducer(producerId()));

    @Override
    public void registerWith(BoundedContext context) {
        checkNotRegistered();
        eventBus = context.eventBus();
        system = context.systemClient().writeSide();
    }

    @Override
    public boolean isRegistered() {
        return eventBus != null;
    }

    /**
     * Obtains identity string of the dispatcher.
     *
     * <p>Default implementation returns fully-qualified name of the class.
     *
     * @return the string with the handler identity
     */
    public String id() {
        return getClass().getName();
    }

    /**
     * Obtains {@linkplain #id() ID} packed into {@code Any} for being used in generated events.
     */
    public Any producerId() {
        return producerId.get();
    }

    /**
     * Posts passed events to {@link EventBus}.
     */
    protected void postEvents(Iterable<Event> events) {
        checkRegistered();
        eventBus.post(events);
    }

    protected void onError(SignalEnvelope<?, ?, ?> signal, Error error) {
        checkRegistered();
        postHandlerFailedUnexpectedly(signal, error);
    }

    protected void onRejection(SignalEnvelope<?, ?, ?> signal, Event rejection) {
        checkRegistered();
        eventBus.post(rejection);
        postSignalRejected(signal, rejection);
    }

    private void postHandlerFailedUnexpectedly(SignalEnvelope<?, ?, ?> signal, Error error) {
        var systemEvent = HandlerFailedUnexpectedly.newBuilder()
                .setEntity(eventAnchor.get())
                .setHandledSignal(signal.messageId())
                .setError(error)
                .build();
        var origin = signal.asMessageOrigin();
        system.postEvent(systemEvent, origin);
    }

    private void postSignalRejected(SignalEnvelope<?, ?, ?> signal, Event rejection) {
        var commandRejected = CommandRejected.newBuilder()
                .setId(signal.messageId()
                             .asCommandId())
                .setRejectionEvent(rejection)
                .build();
        var origin = rejection.asMessageOrigin();
        system.postEvent(commandRejected, origin);
    }

    /**
     * Indicates whether some other command handler is "equal to" this one.
     *
     * <p>Two command handlers are equal if they handle the same set of commands.
     *
     * @return if the passed {@code CommandHandler} handles the same set of command classes
     * @see #messageClasses()
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractCommandDispatcher)) {
            return false;
        }
        var otherHandler = (AbstractCommandDispatcher) o;
        var equals = id().equals(otherHandler.id());
        return equals;
    }

    @Override
    public int hashCode() {
        return id().hashCode();
    }
}
