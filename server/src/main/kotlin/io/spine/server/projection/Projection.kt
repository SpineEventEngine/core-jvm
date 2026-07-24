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

package io.spine.server.projection

import io.spine.base.Error
import io.spine.base.ProjectionState
import io.spine.core.Event
import io.spine.core.EventValidationError
import io.spine.core.EventValidationError.UNSUPPORTED_EVENT_VALUE
import io.spine.server.dispatch.BatchDispatchOutcome
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.dispatchOutcome
import io.spine.server.entity.EventPlayer
import io.spine.server.entity.TransactionalEntity
import io.spine.server.event.EventSubscriber
import io.spine.server.projection.model.ProjectionClass
import io.spine.server.projection.model.ProjectionClass.asProjectionClass
import io.spine.server.type.EventEnvelope
import io.spine.validation.ValidatingBuilder

/**
 * A `Projection` holds a structural representation of data extracted from a stream of events.
 *
 * The process of projecting the event stream into data we collect is performed
 * by event subscribers for the events of interest. These event handlers are implemented
 * in the classes extending this abstract class.
 *
 * Event subscribers are invoked by a [ProjectionRepository] that manages instances
 * of a stream projection class.
 *
 * @param I The type of the IDs.
 * @param M The type of the state objects holding projection data.
 * @param B The type of the builders for the projection state.
 */
public abstract class Projection<I : Any,
                                 M : ProjectionState<I>,
                                 B : ValidatingBuilder<M>> :
    TransactionalEntity<I, M, B>,
    EventPlayer,
    EventSubscriber {

    /**
     * Creates a new instance.
     */
    protected constructor() : super()

    /**
     * Creates a new instance.
     *
     * @param id The ID for the new instance.
     */
    protected constructor(id: I) : super(id)

    override fun thisClass(): ProjectionClass<*> = super.thisClass() as ProjectionClass<*>

    final override fun modelClass(): ProjectionClass<*> = asProjectionClass(javaClass)

    /**
     * Obtains the builder of the projection state, re-exposing the inherited
     * method to the subscriber methods of this projection.
     */
    protected final override fun builder(): B = super.builder()

    override fun missingTxMessage(): String =
        "Projection modification is not available this way. " +
                "Please modify the state from an event subscribing method."

    /**
     * Dispatches the given event to the subscriber method of this projection.
     */
    @JvmName("apply") // Keeps the JVM name for the Java `ProjectionTransaction` caller.
    internal fun apply(event: EventEnvelope): DispatchOutcome =
        thisClass().subscriberOf(event)
            .map { method -> method.invoke(this, event) }
            .orElseGet { unhandledEvent(event) }

    private fun unhandledEvent(event: EventEnvelope): DispatchOutcome {
        val unsupported = Error.newBuilder()
            .setType(EventValidationError.getDescriptor().fullName)
            .setCode(UNSUPPORTED_EVENT_VALUE)
            .setMessage(
                "Projection `${thisClass()}` cannot handle event `${event.messageTypeName()}`."
            )
            .buildPartial()
        return dispatchOutcome {
            propagatedSignal = event.outerObject().messageId()
            error = unsupported
        }
    }

    override fun play(events: Iterable<Event>): BatchDispatchOutcome {
        val eventPlayer = EventPlayer.forTransactionOf(this)
        return eventPlayer.play(events)
    }

    public companion object {

        /**
         * Plays events on the projection.
         *
         * Unlike [play], this method opens the [transaction][ProjectionTransaction]
         * before events are played and closes it after.
         *
         * @return `true` if the projection state was changed as the result of playing
         *   the events.
         */
        internal fun playOn(projection: Projection<*, *, *>, events: Iterable<Event>): Boolean {
            val tx = ProjectionTransaction.start(projection)
            projection.play(events)
            tx.commitIfActive()
            return projection.changed()
        }
    }
}
