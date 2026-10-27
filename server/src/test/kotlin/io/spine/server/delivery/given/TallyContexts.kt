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

package io.spine.server.delivery.given

import io.spine.server.BoundedContext
import io.spine.server.entity.Repository
import io.spine.server.event.EventDispatcher
import io.spine.test.delivery.numberAdded
import io.spine.testing.server.blackbox.BlackBox

/**
 * The single-tenant Bounded Contexts of a test with tallies, and the values posted
 * to the tallies.
 *
 * Each value posted is new, so a tally that applies a value twice, or misses one,
 * holds other values than [caughtUp] or [notCaughtUp] tell.
 */
internal class TallyContexts : AutoCloseable {

    private val boxes = mutableMapOf<String, BlackBox>()
    private val posted = mutableMapOf<Pair<String, String>, MutableList<Posted>>()
    private var lastValue = 0

    /**
     * Builds a context with the given name, repositories, and dispatcher, if any.
     */
    fun create(
        name: String,
        vararg repositories: Repository<*, *>,
        dispatcher: EventDispatcher? = null
    ): BlackBox {
        val builder = BoundedContext.singleTenant(name)
        repositories.forEach { builder.add(it) }
        dispatcher?.let { builder.addEventDispatcher(it) }
        return add(name, BlackBox.from(builder))
    }

    /**
     * Adds the given context, built by the test, under the given name.
     */
    fun add(name: String, box: BlackBox): BlackBox {
        check(name !in boxes) { "A context named `$name` is added already." }
        boxes[name] = box
        return box
    }

    /**
     * Posts [count] events with new values to each of the given tallies of the context.
     */
    fun emit(context: String, vararg ids: String, count: Int = EVENTS_PER_TALLY) {
        repeat(count) {
            ids.forEach { post(context, it) }
        }
    }

    /**
     * Posts an event with a new value to the given tally of the context.
     *
     * @param live Whether the event is a live one, posted after the weight is changed.
     */
    fun post(context: String, id: String, live: Boolean = false) {
        lastValue++
        val newValue = lastValue
        boxes.getValue(context).receivesEvent(numberAdded {
            calculatorId = id
            value = newValue
        })
        posted.getOrPut(context to id) { mutableListOf() }
            .add(Posted(newValue, live))
    }

    /**
     * Returns the values that a tally caught up at the given weight holds:
     * all of them, at that weight.
     */
    fun caughtUp(context: String, id: String, weight: Int): List<Int> =
        postedTo(context, id).map { it.value * weight }

    /**
     * Returns the values that a tally not caught up holds: the history at the weight of 1,
     * and the live events at the given weight.
     */
    fun notCaughtUp(context: String, id: String, weight: Int): List<Int> =
        postedTo(context, id).map { it.value * (if (it.live) weight else 1) }

    private fun postedTo(context: String, id: String): List<Posted> =
        posted[context to id].orEmpty()

    /**
     * Closes the context with the given name.
     */
    fun close(context: String) {
        boxes.remove(context)?.close()
    }

    override fun close() {
        try {
            closeAll(boxes.values)
        } finally {
            boxes.clear()
        }
    }

    private data class Posted(val value: Int, val live: Boolean)

    companion object {

        /**
         * The number of history events each tally gets by default.
         */
        const val EVENTS_PER_TALLY = 3
    }
}

/**
 * Closes each of the given resources, even if closing one of them fails.
 *
 * A context left open would keep serving its entity state types for the rest
 * of the test run.
 *
 * @throws RuntimeException The first failure, with the later ones suppressed.
 */
internal fun closeAll(resources: Iterable<AutoCloseable>) {
    var failure: RuntimeException? = null
    resources.forEach { resource ->
        try {
            resource.close()
        } catch (e: RuntimeException) {
            failure?.addSuppressed(e) ?: run { failure = e }
        }
    }
    failure?.let { throw it }
}
