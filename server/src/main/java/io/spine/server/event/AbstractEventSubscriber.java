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
import io.spine.base.Error;
import io.spine.core.BoundedContextName;
import io.spine.core.MessageId;
import io.spine.logging.WithLogging;
import io.spine.server.BoundedContext;
import io.spine.server.ContextAware;
import io.spine.server.Identity;
import io.spine.server.bus.MessageDispatcher;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.dispatch.DispatchOutcomeHandler;
import io.spine.server.event.model.EventSubscriberClass;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.HandlerFailedUnexpectedly;
import io.spine.system.server.NoOpSystemWriteSide;
import io.spine.system.server.SystemWriteSide;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.memoize;
import static io.spine.server.Ignored.ignored;
import static io.spine.server.event.model.EventSubscriberClass.asEventSubscriberClass;
import static io.spine.server.tenant.TenantAwareRunner.with;
import static java.lang.String.format;

/**
 * The abstract base for objects that can be subscribed to receive events from {@link EventBus}.
 *
 * <p>Objects may also receive events via {@link EventDispatcher}s that can be
 * registered with {@code EventBus}.
 *
 * @see EventBus#register(MessageDispatcher)
 * @see io.spine.core.Subscribe
 */
public abstract class AbstractEventSubscriber
        implements EventDispatcher, EventSubscriber, ContextAware, WithLogging {

    /** Model class for this subscriber. */
    private final EventSubscriberClass<?> thisClass = asEventSubscriberClass(getClass());
    private final Supplier<MessageId> eventAnchor = memoize(() -> Identity.ofSingleton(getClass()));
    @LazyInit
    private SystemWriteSide system = NoOpSystemWriteSide.INSTANCE;
    @LazyInit
    private @MonotonicNonNull BoundedContextName contextName;

    @Override
    public void registerWith(BoundedContext context) {
        checkNotNull(context);
        checkNotRegistered();
        contextName = context.name();
        system = context.systemClient()
                        .writeSide();
    }

    @Override
    public boolean isRegistered() {
        return contextName != null;
    }

    /**
     * Dispatches the event to the handling method.
     *
     * @param event
     *         the envelope with the event
     */
    @Override
    public final DispatchOutcome dispatch(EventEnvelope event) {
        return with(event.tenantId()).evaluate(() -> handle(event));
    }

    /**
     * Handles an event dispatched to this subscriber instance.
     *
     * <p>By default, passes the event to the corresponding
     * {@linkplain io.spine.core.Subscribe subscriber} method of the entity.
     */
    protected DispatchOutcome handle(EventEnvelope event) {
        var outcome =
                thisClass.subscriberOf(event)
                         .map(method -> method.invoke(this, event))
                         .orElseGet(() -> ignored(thisClass, event));
        DispatchOutcomeHandler.from(outcome)
                .onError(e -> postFailure(e, event))
                .onIgnored(i -> logOnIgnored(event))
                .handle();
        return outcome;
    }

    private void postFailure(Error error, EventEnvelope event) {
        var systemEvent = HandlerFailedUnexpectedly.newBuilder()
                .setEntity(eventAnchor())
                .setHandledSignal(event.messageId())
                .setError(error)
                .build();
        system.postEvent(systemEvent, event.asMessageOrigin());
    }

    private void logOnIgnored(EventEnvelope event) {
        if (!event.isEntityStateUpdate()) {
            logger().atDebug().log(() -> format(
                    "Subscriber `%s` filtered out and ignored event %s[ID: %s].",
                    thisClass, event.messageClass(), event.id().value()));
        }
    }

    private MessageId eventAnchor() {
        return eventAnchor.get();
    }

    @Override
    public boolean canDispatch(EventEnvelope event) {
        var subscriber = thisClass.subscriberOf(event);
        return subscriber.isPresent();
    }

    @Override
    public ImmutableSet<EventClass> messageClasses() {
        return thisClass.events();
    }

    @Override
    public ImmutableSet<EventClass> externalEventClasses() {
        return thisClass.externalEvents();
    }

    @Override
    public ImmutableSet<EventClass> domesticEventClasses() {
        return thisClass.domesticEvents();
    }
}
