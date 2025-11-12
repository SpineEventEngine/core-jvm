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

package io.spine.server.given.context.drawing

import io.spine.base.EventMessage
import io.spine.core.Subscribe
import io.spine.protobuf.pack
import io.spine.server.BoundedContext
import io.spine.server.entity.alter
import io.spine.server.given.context.drawing.Classifier.LIFECYCLE
import io.spine.server.given.context.drawing.Classifier.LOCATION
import io.spine.server.given.context.drawing.Classifier.OTHER
import io.spine.server.given.context.drawing.Classifier.STYLE
import io.spine.server.given.context.drawing.event.LineAdded
import io.spine.server.given.context.drawing.event.LineEvent
import io.spine.server.given.context.drawing.event.LineInitEvent
import io.spine.server.given.context.drawing.event.LineLifecycleEvent
import io.spine.server.given.context.drawing.event.LineLocationEvent
import io.spine.server.given.context.drawing.event.LineMoved
import io.spine.server.given.context.drawing.event.LineRemoved
import io.spine.server.given.context.drawing.event.LineSelected
import io.spine.server.given.context.drawing.event.LineStyleChanged
import io.spine.server.given.context.drawing.event.LineUnselected
import io.spine.server.projection.Projection
import io.spine.server.route.Route

/**
 * This context declares several events on a very simple drawing scenario for lines.
 *
 * The events are grouped by interfaces:
 *  * The [LineEvent] interface is implemented by all the events.
 *  * Events related to a creation or removal of a line implement [LineLifecycleEvent].
 *  * The [LineLocationEvent] interface covers the movement events.
 *
 * The [LineInitEvent] interface serves for mixing [LineLifecycleEvent] and [LineLocationEvent]
 * interface for the [LineAdded] event.
 *
 * The [DrawingEvents] projection simply accumulates the received events in its state.
 *
 * It's the routing methods declared by the [DrawingEvents] class that serve the purpose of
 * test demonstrating the routing by interfaces.
 *
 * The routing methods "classify" the events by returning the name of the [Classifier] enum
 * item as the target entity ID(s).
 */
fun drawingContext(): BoundedContext = BoundedContext.singleTenant("Drawing")
    .add(DrawingEvents::class.java)
    .build()

class DrawingEvents : Projection<String, Log, Log.Builder>() {

    @Subscribe internal fun on(e: LineAdded) = add(e)
    @Subscribe internal fun on(e: LineRemoved) = add(e)
    @Subscribe internal fun on(e: LineMoved) = add(e)
    @Subscribe internal fun on(e: LineStyleChanged) = add(e)
    @Subscribe internal fun on(e: LineSelected) = add(e)
    @Subscribe internal fun on(e: LineUnselected) = add(e)

    private fun add(e: EventMessage) = alter {
        addEvent(e.pack())
    }

    @Suppress("UNUSED_PARAMETER")
    companion object {

        /**
         * This routing function should be invoked before those which accept
         * [LineLifecycleEvent] or [LineLocationEvent] because [LineInitEvent] is a more
         * specific interface.
         */
        @Route
        @JvmStatic
        fun route(e: LineInitEvent): Set<String> = setOf(
            LIFECYCLE.name,
            LOCATION.name
        )

        @Route
        @JvmStatic
        fun route(e: LineLifecycleEvent): String = LIFECYCLE.name

        @Route
        @JvmStatic
        fun route(e: LineLocationEvent): String = LOCATION.name

        /**
         * This is the widest routing function, which should cover events that are
         * not covered by other routing functions.
         */
        @Route
        @JvmStatic
        fun route(e: LineEvent): String = OTHER.name

        /**
         * Even though this routing function is declared after the one that
         * accepts the widest interface [LineEvent], it should be invoked for
         * the specified type of the parameter.
         */
        @Route
        @JvmStatic
        fun route(e: LineStyleChanged): String = STYLE.name
    }
}
