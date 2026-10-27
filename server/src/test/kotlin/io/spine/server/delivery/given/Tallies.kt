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

import io.spine.base.ProjectionState
import io.spine.core.Subscribe
import io.spine.server.projection.Projection
import io.spine.server.projection.ProjectionRepository
import io.spine.server.route.EventRouting
import io.spine.test.delivery.NumberAdded
import io.spine.test.delivery.TallyA1
import io.spine.test.delivery.TallyA2
import io.spine.test.delivery.TallyA3
import io.spine.test.delivery.TallyB1
import io.spine.test.delivery.TallyB2
import io.spine.test.delivery.TallyB3
import kotlin.jvm.optionals.getOrNull

/**
 * The weight by which a tally multiplies each value it applies.
 *
 * A test changes it before a catch-up. Then a tally rebuilt by the catch-up differs from
 * a tally that is not.
 */
internal object TallyWeight {

    private const val DEFAULT = 1

    @Volatile
    var value: Int = DEFAULT

    /**
     * Sets the weight back to the default of 1.
     */
    fun reset() {
        value = DEFAULT
    }
}

/**
 * Applies the [NumberAdded] events to the `A` tally of the first Bounded Context.
 */
internal class TallyA1View : Projection<String, TallyA1, TallyA1.Builder>() {

    @Subscribe
    fun on(event: NumberAdded) = alter { addValue(event.value * TallyWeight.value) }
}

/**
 * Applies the [NumberAdded] events to the `B` tally of the first Bounded Context.
 */
internal class TallyB1View : Projection<String, TallyB1, TallyB1.Builder>() {

    @Subscribe
    fun on(event: NumberAdded) = alter { addValue(event.value * TallyWeight.value) }
}

/**
 * Applies the [NumberAdded] events to the `A` tally of the second Bounded Context.
 */
internal class TallyA2View : Projection<String, TallyA2, TallyA2.Builder>() {

    @Subscribe
    fun on(event: NumberAdded) = alter { addValue(event.value * TallyWeight.value) }
}

/**
 * Applies the [NumberAdded] events to the `B` tally of the second Bounded Context.
 */
internal class TallyB2View : Projection<String, TallyB2, TallyB2.Builder>() {

    @Subscribe
    fun on(event: NumberAdded) = alter { addValue(event.value * TallyWeight.value) }
}

/**
 * Applies the [NumberAdded] events to the `A` tally of the third Bounded Context.
 */
internal class TallyA3View : Projection<String, TallyA3, TallyA3.Builder>() {

    @Subscribe
    fun on(event: NumberAdded) = alter { addValue(event.value * TallyWeight.value) }
}

/**
 * Applies the [NumberAdded] events to the `B` tally of the third Bounded Context.
 */
internal class TallyB3View : Projection<String, TallyB3, TallyB3.Builder>() {

    @Subscribe
    fun on(event: NumberAdded) = alter { addValue(event.value * TallyWeight.value) }
}

/**
 * A repository of tallies, which routes each [NumberAdded] event to the tally
 * of its calculator.
 *
 * @param P The type of the tallies.
 * @param S The type of the state of the tallies.
 */
internal abstract class TallyRepository<P : Projection<String, S, *>, S : ProjectionState<String>> :
    ProjectionRepository<String, P, S>() {

    override fun setupEventRouting(routing: EventRouting<String>) {
        super.setupEventRouting(routing)
        routing.unicast<NumberAdded> { it.calculatorId }
    }

    /**
     * Returns the values applied by the tally with the given ID, in the order applied,
     * or `null` if the tally is not stored.
     *
     * In a multi-tenant Bounded Context, call it for the tenant of the tally.
     */
    fun valuesOf(id: String): List<Int>? =
        find(id).getOrNull()?.state()?.let { valuesIn(it) }

    /**
     * Returns the values held in the given state of a tally.
     */
    protected abstract fun valuesIn(state: S): List<Int>
}

internal class TallyA1Repository : TallyRepository<TallyA1View, TallyA1>() {
    override fun valuesIn(state: TallyA1): List<Int> = state.valueList
}

internal class TallyB1Repository : TallyRepository<TallyB1View, TallyB1>() {
    override fun valuesIn(state: TallyB1): List<Int> = state.valueList
}

internal class TallyA2Repository : TallyRepository<TallyA2View, TallyA2>() {
    override fun valuesIn(state: TallyA2): List<Int> = state.valueList
}

internal class TallyB2Repository : TallyRepository<TallyB2View, TallyB2>() {
    override fun valuesIn(state: TallyB2): List<Int> = state.valueList
}

internal class TallyA3Repository : TallyRepository<TallyA3View, TallyA3>() {
    override fun valuesIn(state: TallyA3): List<Int> = state.valueList
}

internal class TallyB3Repository : TallyRepository<TallyB3View, TallyB3>() {
    override fun valuesIn(state: TallyB3): List<Int> = state.valueList
}

/**
 * The repositories of the `A` and `B` tallies of a Bounded Context.
 */
internal class TallyPair(val a: TallyRepository<*, *>, val b: TallyRepository<*, *>) {

    companion object {

        /**
         * Creates the repositories of the Bounded Context with the given number,
         * from 1 to 3.
         */
        fun ofContext(number: Int): TallyPair = when (number) {
            1 -> TallyPair(TallyA1Repository(), TallyB1Repository())
            2 -> TallyPair(TallyA2Repository(), TallyB2Repository())
            3 -> TallyPair(TallyA3Repository(), TallyB3Repository())
            else -> error("There are tallies for three contexts only, not for `$number`.")
        }
    }
}
