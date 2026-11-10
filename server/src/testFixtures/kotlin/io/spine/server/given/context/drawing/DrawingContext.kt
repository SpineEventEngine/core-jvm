/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server.given.context.drawing

import io.spine.base.EventMessage
import io.spine.core.Subscribe
import io.spine.protobuf.pack
import io.spine.server.BoundedContext
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
import io.spine.testing.server.buildForTests

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
    .buildForTests()

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
         * This routing function should be invoked before those that accept
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
