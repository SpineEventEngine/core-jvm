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

package io.spine.server.integration;

import com.google.protobuf.Any;
import io.spine.base.EventMessage;
import io.spine.core.ActorContext;
import io.spine.core.UserId;
import io.spine.server.BoundedContext;
import io.spine.server.Closeable;
import io.spine.server.event.EventFactory;
import io.spine.server.type.EventEnvelope;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.base.Time.currentTime;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;

/**
 * An external non-Spine based upstream system.
 *
 * <p>{@code ThirdPartyContext} helps to represent an external system as a Bounded Context. Events
 * which occur in the external system are converted into domain events of the user's
 * Bounded Contexts and dispatched via {@link IntegrationBroker}.
 *
 * @implSpec Note that a {@code ThirdPartyContext} sends a request for external messages to
 *         other contexts. The {@code ThirdPartyContext} never consumes external messages itself,
 *         but requires the other Bounded Contexts to send their requests, so that the publishing
 *         channels are open. Depending on the implementation of
 *         {@linkplain io.spine.server.transport.TransportFactory transport}, creating
 *         a {@code ThirdPartyContext} may be an expensive operation. Thus, it is recommended that
 *         the instances of this class are reused and {@linkplain #close() closed} when they are
 *         no longer needed.
 */
public final class ThirdPartyContext implements Closeable {

    private final BoundedContext context;
    private final Any producerId;

    /**
     * Creates a new single-tenant instance of {@code ThirdPartyContext} with the given name.
     *
     * @param name
     *         name of the Bounded Context representing a part of a third-party system
     */
    public static ThirdPartyContext singleTenant(String name) {
        return newContext(name, false);
    }

    /**
     * Creates a new multitenant instance of {@code ThirdPartyContext} with the given name.
     *
     * @param name
     *         name of the Bounded Context representing a part of a third-party system
     */
    public static ThirdPartyContext multitenant(String name) {
        return newContext(name, true);
    }

    private static ThirdPartyContext newContext(String name, boolean multitenant) {
        checkNotEmptyOrBlank(name);

        var contextBuilder = multitenant
                             ? BoundedContext.multitenant(name)
                             : BoundedContext.singleTenant(name);
        contextBuilder.systemSettings()
               .disableCommandLog()
               .forgetEvents();
        var context = contextBuilder.build();
        return new ThirdPartyContext(context);
    }

    private ThirdPartyContext(BoundedContext context) {
        this.context = context;
        this.producerId = pack(context.name());
    }

    /**
     * Emits an event from the third-party system.
     *
     * <p>If the event is required by another Context, posts the event into
     * the {@link IntegrationBroker} of the respective Context. Does nothing if the event is not
     * required by any Context.
     *
     * <p>The caller is required to supply the tenant ID via the {@code ActorContext.tenant_id} if
     * this Context is multitenant.
     *
     * @param eventMessage
     *         the event
     * @param actorContext
     *         the info about the actor, a user or a software component, who emits the event
     */
    public void emittedEvent(EventMessage eventMessage, ActorContext actorContext) {
        checkNotNull(actorContext);
        checkNotNull(eventMessage);
        checkTenant(actorContext, eventMessage);

        var eventFactory = EventFactory.forImport(actorContext, producerId);
        @SuppressWarnings("ConstantConditions")     /* No explicit version to be set. */
                var event = eventFactory.createEvent(eventMessage, null);
        context.internalAccess()
               .broker()
               .publish(EventEnvelope.of(event));
    }

    /**
     * Emits an event from the third-party system.
     *
     * <p>If the event is required by another Context, posts the event into
     * the {@link IntegrationBroker} of the respective Context. Does nothing if the event is not
     * required by any Context.
     *
     * <p>This overload may only be used for single-tenant third-party contexts. If this Context is
     * multitenant, this method throws an exception.
     *
     * @param eventMessage
     *         the event
     * @param userId
     *         the ID of the actor, a user or a software component, who emits the event
     */
    public void emittedEvent(EventMessage eventMessage, UserId userId) {
        checkNotNull(userId);
        checkNotNull(eventMessage);
        var context = ActorContext.newBuilder()
                .setActor(userId)
                .setTimestamp(currentTime())
                .build();
        emittedEvent(eventMessage, context);
    }

    private void checkTenant(ActorContext actorContext, EventMessage event) {
        var tenantSupplied = actorContext.hasTenantId();
        var contextName = context.name().getValue();
        var eventType = event.getClass().getName();
        if (context.isMultitenant()) {
            checkArgument(tenantSupplied,
                          "Cannot post `%s` into a third-party multitenant context `%s`." +
                                  " No tenant ID supplied.",
                          eventType, contextName);
        } else {
            checkArgument(!tenantSupplied,
                          "Cannot post `%s` into a third-party single-tenant context `%s`." +
                                  " Tenant ID must NOT be supplied.",
                          eventType, contextName);
        }
    }

    /**
     * Closes this Context and clean up underlying resources.
     *
     * <p>Attempts of emitting an event from a closed Context result in an exception.
     */
    @Override
    public void close() {
        context.close();
    }

    @Override
    public boolean isOpen() {
        return context.isOpen();
    }
}
