/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.server.projection;

import io.spine.annotation.Internal;
import io.spine.base.Error;
import io.spine.base.ProjectionState;
import io.spine.core.Event;
import io.spine.core.EventValidationError;
import io.spine.server.dispatch.BatchDispatchOutcome;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.EventPlayer;
import io.spine.server.entity.HasLifecycleColumns;
import io.spine.server.entity.HasVersionColumn;
import io.spine.server.entity.TransactionalEntity;
import io.spine.server.event.EventSubscriber;
import io.spine.server.projection.model.ProjectionClass;
import io.spine.server.type.EventEnvelope;
import io.spine.validation.ValidatingBuilder;

import static io.spine.core.EventValidationError.UNSUPPORTED_EVENT_VALUE;
import static io.spine.server.projection.model.ProjectionClass.asProjectionClass;
import static java.lang.String.format;

/**
 * {@link Projection} holds a structural representation of data extracted from a stream of events.
 *
 * <p>The process of projecting the event stream into data we collect is performed
 * by event subscribers for the events of interest. These event handlers are implemented
 * in the classes extending this abstract class.
 *
 * <p>Event subscribers are invoked by a {@link ProjectionRepository} that manages instances
 * of a stream projection class.
 *
 * @param <I>
 *         the type of the IDs
 * @param <M>
 *         the type of the state objects holding projection data
 * @param <B>
 *         the type of the builders for the projection state
 */
public abstract class Projection<I,
                                 M extends ProjectionState<I>,
                                 B extends ValidatingBuilder<M>>
        extends TransactionalEntity<I, M, B>
        implements EventPlayer,
                   EventSubscriber,
                   HasVersionColumn<I, M>,
                   HasLifecycleColumns<I, M> {

    /**
     * Creates a new instance.
     */
    protected Projection() {
        super();
    }

    /**
     * Creates a new instance.
     *
     * @param id
     *         the ID for the new instance
     */
    protected Projection(I id) {
        super(id);
    }

    @Override
    protected ProjectionClass<?> thisClass() {
        return (ProjectionClass<?>) super.thisClass();
    }

    @Internal
    @Override
    public final ProjectionClass<?> modelClass() {
        return asProjectionClass(getClass());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overridden to expose the method to the package.
     */
    @Override
    protected final B builder() {
        return super.builder();
    }

    @Override
    protected String missingTxMessage() {
        return "Projection modification is not available this way. " +
                "Please modify the state from an event subscribing method.";
    }

    /**
     * Plays events on the projection.
     *
     * <p>Unlike {@link Projection#play(Iterable)}, this static method opens the
     * {@linkplain ProjectionTransaction transaction} before events are played and closes it after.
     *
     * @return {@code true} if the projection state was changed as the result of playing the events
     */
    @SuppressWarnings("HandleMethodResult") /* Projection dispatching has no outcome. */
    static boolean playOn(Projection<?, ?, ?> projection, Iterable<Event> events) {
        ProjectionTransaction<?, ?, ?> tx = ProjectionTransaction.start(projection);
        projection.play(events);
        tx.commitIfActive();
        return projection.changed();
    }

    DispatchOutcome apply(EventEnvelope event) {
        return thisClass()
                .subscriberOf(event)
                .map(method -> method.invoke(this, event))
                .orElseGet(() -> unhandledEvent(event));
    }

    private DispatchOutcome unhandledEvent(EventEnvelope event) {
        var error = Error.newBuilder()
                .setType(EventValidationError.getDescriptor().getFullName())
                .setCode(UNSUPPORTED_EVENT_VALUE)
                .setMessage(format(
                        "Projection `%s` cannot handle event `%s`.",
                        thisClass(), event.messageTypeName()
                ))
                .buildPartial();
        return DispatchOutcome.newBuilder()
                .setPropagatedSignal(event.outerObject().messageId())
                .setError(error)
                .build();
    }

    @Override
    public BatchDispatchOutcome play(Iterable<Event> events) {
        var eventPlayer = EventPlayer.forTransactionOf(this);
        return eventPlayer.play(events);
    }
}
