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

package io.spine.server.route

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.protobuf.unpackKnownType
import io.spine.server.given.context.drawing.Classifier
import io.spine.server.given.context.drawing.Classifier.LIFECYCLE
import io.spine.server.given.context.drawing.Classifier.LOCATION
import io.spine.server.given.context.drawing.Classifier.OTHER
import io.spine.server.given.context.drawing.Classifier.STYLE
import io.spine.server.given.context.drawing.DrawingEvents
import io.spine.server.given.context.drawing.LineKt.location
import io.spine.server.given.context.drawing.Log
import io.spine.server.given.context.drawing.color
import io.spine.server.given.context.drawing.drawingContext
import io.spine.server.given.context.drawing.event.LineAdded
import io.spine.server.given.context.drawing.event.LineEvent
import io.spine.server.given.context.drawing.event.LineMoved
import io.spine.server.given.context.drawing.event.LineRemoved
import io.spine.server.given.context.drawing.event.LineSelected
import io.spine.server.given.context.drawing.event.LineStyleChanged
import io.spine.server.given.context.drawing.event.LineUnselected
import io.spine.server.given.context.drawing.event.lineAdded
import io.spine.server.given.context.drawing.event.lineMoved
import io.spine.server.given.context.drawing.event.lineRemoved
import io.spine.server.given.context.drawing.event.lineSelected
import io.spine.server.given.context.drawing.event.lineStyleChanged
import io.spine.server.given.context.drawing.event.lineUnselected
import io.spine.server.given.context.drawing.line
import io.spine.server.given.context.drawing.pen
import io.spine.server.given.context.drawing.point
import io.spine.testing.server.blackbox.BlackBox
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Static routing via interfaces should")
internal class StaticRoutingInheritanceITest {

    @Test
    fun `put more specific interfaces earlier`() {
        val p1 = point { x = 10; y = 10 }
        val p2 = point { x = 20; y = 20 }
        val p3 = point { x = 30; y = 30 }
        val black = color { red = 0; green = 0; blue = 0 }
        val blue = color { red = 0; green = 0; blue = 255 }
        val pen1 = pen { color = black; width = 5.0f }
        val pen2 = pen { color = blue; width = 1.0f }
        val l1 = line { location = location { start = p1; end = p2 }; pen = pen1 }
        val l2 = line { location = location { start = p2; end = p3 }; pen = pen1 }
        val l3 = line { location = location { start = p2; end = p3 }; pen = pen2 }

        val events = listOf(
            lineAdded { line = l1 },
            lineMoved { line = l2; previous = l1.location },
            lineStyleChanged { line = l3; previous = pen1 },
            lineSelected { line = l3 },
            lineUnselected { line = l3 },
            lineRemoved { line = l2 }
        )

        BlackBox.from(drawingContext()).use { context ->
            events.forEach {
                context.receivesEvent(it)
            }

            events(LIFECYCLE, context).run {
                shouldContain<LineAdded>()
                shouldContain<LineRemoved>()
            }

            events(LOCATION, context).run {
                shouldContain<LineAdded>()
                shouldContain<LineMoved>()
            }

            events(STYLE, context).run {
                shouldContain<LineStyleChanged>()

                shouldNotContain<LineAdded>()
            }

            events(OTHER, context).run {
                shouldContain<LineSelected>()
                shouldContain<LineUnselected>()

                shouldNotContain<LineAdded>()
                shouldNotContain<LineMoved>()
                shouldNotContain<LineRemoved>()
            }
        }
    }
}

private inline fun <reified E: LineEvent> Iterable<LineEvent>.shouldContain() {
    find { it is E } shouldNotBe null
}

private inline fun <reified E: LineEvent> Iterable<LineEvent>.shouldNotContain() {
    find { it is E } shouldBe null
}

private fun events(c: Classifier, context: BlackBox): List<LineEvent> {
    val log = context.assertEntity(c.name, DrawingEvents::class.java).actual()?.state() as Log
    val events = log.eventList.map { it.unpackKnownType() as LineEvent }
    return events
}
