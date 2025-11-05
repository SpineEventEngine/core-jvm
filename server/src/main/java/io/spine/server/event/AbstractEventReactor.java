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

package io.spine.server.event;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.protobuf.Any;
import io.spine.annotation.Internal;
import io.spine.base.Error;
import io.spine.core.MessageId;
import io.spine.core.Version;
import io.spine.core.Versions;
import io.spine.logging.WithLogging;
import io.spine.protobuf.TypeConverter;
import io.spine.server.BoundedContext;
import io.spine.server.ContextAware;
import io.spine.server.Identity;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.dispatch.DispatchOutcomeHandler;
import io.spine.server.event.model.EventReactorClass;
import io.spine.server.stand.Stand;
import io.spine.server.tenant.TenantAwareRunner;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.HandlerFailedUnexpectedly;
import io.spine.system.server.NoOpSystemWriteSide;
import io.spine.system.server.SystemWriteSide;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.function.Supplier;

import static com.google.common.base.Suppliers.memoize;
import static io.spine.server.dispatch.DispatchOutcomes.ignored;
import static java.lang.String.format;

/**
 * An abstract base for all classes that may produce events in response to other events.
 *
 * <p>Events may be produced in response to both domestic and external events.
 *
 * <p>To use one, do the following:
 * <ol>
 *     <li>Create an instance, specifying the event bus which receives emitted events.
 *     <li>{@linkplain io.spine.server.BoundedContextBuilder#addEventDispatcher(EventDispatcher) Register}.
 * </ol>
 *
 * @see React reactors
 */
public abstract class AbstractEventReactor
        implements EventReactor, EventDispatcher, ContextAware, WithLogging {

    private final EventReactorClass<?> thisClass = EventReactorClass.asReactorClass(getClass());
    private final Supplier<MessageId> eventAnchor = memoize(() -> Identity.ofSingleton(getClass()));
    @LazyInit
    private SystemWriteSide system = NoOpSystemWriteSide.INSTANCE;

    /** The event bus to which the emitted events are posted. */
    @LazyInit
    private @MonotonicNonNull EventBus eventBus;

    private final Supplier<Any> producerId =
            memoize(() -> TypeConverter.toAny(getClass().getName()));

    @Override
    public void registerWith(BoundedContext context) {
        checkNotRegistered();
        eventBus = context.eventBus();
        system = context.systemClient().writeSide();
        registerIn(context.stand());
    }

    /**
     * Registers this reactor in {@code Stand} as an event producer.
     *
     * <p>The descendants which emit system events may choose not to expose their signals
     * via {@code Stand}, or expose them partially. In which case this method should be
     * overridden respectively.
     */
    @Internal
    protected void registerIn(Stand stand) {
        stand.registerTypeSupplier(this);
    }

    @Override
    public final boolean isRegistered() {
        return eventBus != null;
    }

    @Override
    public final ImmutableSet<EventClass> messageClasses() {
        return thisClass.events();
    }

    @Override
    public DispatchOutcome dispatch(EventEnvelope event) {
        return TenantAwareRunner.with(event.tenantId())
                                .evaluate(() -> reactAndPost(event));
    }

    @SuppressWarnings("FloggerLogString" /* Re-using the logged message. */)
    private DispatchOutcome reactAndPost(EventEnvelope event) {
        var method = thisClass.reactorOf(event);
        if (method.isPresent()) {
            var outcome = method.get().invoke(this, event);
            DispatchOutcomeHandler
                    .from(outcome)
                    .onEvents(eventBus::post)
                    .onError(error -> postFailure(error, event))
                    .handle();
            return outcome;
        } else {
            var eventId = event.id();
            var msg = format("Reactor `%s` filtered out and ignored event %s[ID: %s].",
                             thisClass, event.messageClass(), eventId.value());
            logger().atDebug().log(() -> msg);
            return ignored(event, msg);
        }
    }

    private MessageId eventAnchor() {
        return eventAnchor.get();
    }

    private void postFailure(Error error, EventEnvelope event) {
        var systemEvent = HandlerFailedUnexpectedly.newBuilder()
                .setEntity(eventAnchor())
                .setHandledSignal(event.messageId())
                .setError(error)
                .build();
        system.postEvent(systemEvent, event.asMessageOrigin());
    }

    /**
     * Obtains the name of this reactor, {@linkplain TypeConverter#toAny(Object) packed to Any}.
     */
    @Override
    public Any producerId() {
        return producerId.get();
    }

    /**
     * Returns a {@linkplain Versions#zero() zero version}.
     */
    @Override
    public Version version() {
        return Versions.zero();
    }

    @Override
    public final ImmutableSet<EventClass> externalEventClasses() {
        return thisClass.externalEvents();
    }

    @Override
    public final ImmutableSet<EventClass> domesticEventClasses() {
        return thisClass.domesticEvents();
    }

    @Override
    public final ImmutableSet<EventClass> producedEvents() {
        return thisClass.reactionOutput();
    }
}
