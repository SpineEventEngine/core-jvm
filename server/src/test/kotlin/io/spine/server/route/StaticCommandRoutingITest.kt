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
import io.spine.server.given.context.fizzbuzz.NumberQualification
import io.spine.server.given.context.fizzbuzz.QualifiedNumbers
import io.spine.server.given.context.fizzbuzz.Qualifier
import io.spine.server.given.context.fizzbuzz.Qualifier.BUZZ
import io.spine.server.given.context.fizzbuzz.Qualifier.COMMON
import io.spine.server.given.context.fizzbuzz.Qualifier.FIZZ
import io.spine.server.given.context.fizzbuzz.Qualifier.FIZZ_BUZZ
import io.spine.server.given.context.fizzbuzz.command.qualifyNumber
import io.spine.server.given.context.fizzbuzz.createFizzBuzzContext
import io.spine.testing.server.blackbox.BlackBox
import io.spine.testing.server.blackbox.assertEntity
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Static command routing should")
internal class StaticCommandRoutingITest {

    @Test
    fun `route a command via a static method of an entity class`() {
        val length = 15
        val commands = sequence {
            repeat(length) {
                yield(qualifyNumber { number = it + 1 })
            }
        }.toList()

        BlackBox.from(createFizzBuzzContext()).use { context ->
            commands.forEach {
                context.receivesCommand(it)
            }

            val fizz = numbersFor(context, FIZZ)
            val buzz = numbersFor(context, BUZZ)
            val fizzBuzz = numbersFor(context, FIZZ_BUZZ)
            val common = numbersFor(context, COMMON)

            fizz.numberCount shouldBe 4
            buzz.numberCount shouldBe 2
            fizzBuzz.numberCount shouldBe 1
            common.numberCount shouldBe length - 7
        }
    }

    private fun numbersFor(
        context: BlackBox,
        id: Qualifier
    ) = context.assertEntity<NumberQualification, _>(id.name)
        .actual()?.state() as QualifiedNumbers
}
