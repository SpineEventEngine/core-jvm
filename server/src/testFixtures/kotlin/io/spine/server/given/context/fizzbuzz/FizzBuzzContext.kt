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

package io.spine.server.given.context.fizzbuzz

import io.spine.annotation.VisibleForTesting
import io.spine.server.BoundedContextBuilder
import io.spine.server.BoundedContextBuilder.assumingTests
import io.spine.server.command.Assign
import io.spine.server.event.NoReaction
import io.spine.server.event.React
import io.spine.server.given.context.fizzbuzz.Qualifier.BUZZ
import io.spine.server.given.context.fizzbuzz.Qualifier.COMMON
import io.spine.server.given.context.fizzbuzz.Qualifier.FIZZ
import io.spine.server.given.context.fizzbuzz.Qualifier.FIZZ_BUZZ
import io.spine.server.given.context.fizzbuzz.command.QualifyNumber
import io.spine.server.given.context.fizzbuzz.event.NumberQualified
import io.spine.server.given.context.fizzbuzz.event.numberQualified
import io.spine.server.procman.ProcessManager
import io.spine.server.route.Route

/**
 * Creates the builder of a toy context around the rules of
 * the [Fizz Buzz](https://en.wikipedia.org/wiki/Fizz_buzz) game.
 *
 * The purpose of the context is to test the command rounting based on a static method
 * of the [NumberQualification] process manager.
 *
 * @see io.spine.server.given.context.sorting.createSortingContext
 */
fun createFizzBuzzContext(): BoundedContextBuilder = assumingTests("FizzBuzz").apply {
    add(NumberQualification::class.java)
}

/**
 * This process manager handles the [QualifyNumber] command and the [NumberQualified] event
 * that it produces in response to the command.
 *
 * Even though qualifying the numbers is based on the [Qualifier] enum type, the ID of
 * this process manager is of the type `String` because Spine does not support
 * enum-based IDs at the time of writing. So, we use the names of the enum items as IDs.
 *
 * The routing function [qualify] calculates the ID in for the [QualifyNumber] command
 * using the rules of the [Fizz Buzz](https://en.wikipedia.org/wiki/Fizz_buzz) game.
 *
 * The [NumberQualified] event is routed by the [routeEvent] static method.
 *
 * The process manager remembers the number in its state so that the tests can check it
 * assuming successful dispatching of commands and events.
 */
@VisibleForTesting
class NumberQualification :
    ProcessManager<String, QualifiedNumbers, QualifiedNumbers.Builder>() {

    @Assign
    internal fun handle(command: QualifyNumber): NumberQualified {
        return numberQualified {
            number = command.number
            qualifier = Qualifier.valueOf(id())
        }
    }

    @React
    internal fun on(event: NumberQualified): NoReaction {
        alter {
            addNumber(event.number)
        }
        return noReaction()
    }

    companion object {

        @Route
        @JvmStatic
        fun qualify(command: QualifyNumber): String {
            val number = command.number
            val dividedByThree = (number % 3) == 0
            val dividedByFive = (number % 5) == 0
            val result = when {
                dividedByThree && dividedByFive -> FIZZ_BUZZ
                dividedByThree -> FIZZ
                dividedByFive -> BUZZ
                else -> COMMON
            }.name
            return result
        }

        @Route
        @JvmStatic
        fun routeEvent(event: NumberQualified): String = event.qualifier.name
    }
}
