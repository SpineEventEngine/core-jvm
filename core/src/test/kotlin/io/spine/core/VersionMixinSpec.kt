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

package io.spine.core

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.base.Time.currentTime
import io.spine.testing.core.given.GivenVersion.withNumber
import io.spine.validation.FieldAwareMessage
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import io.kotest.assertions.throwables.shouldThrow

@DisplayName("`VersionMixin` should")
internal class VersionMixinSpec {

    @Test
    fun `return the number part of a version`() {
        withNumber(7).number shouldBe 7
    }

    @Test
    fun `Versions zero() creates a version with number zero`() {
        val v = Versions.zero()
        v.number shouldBe 0
        v.timestamp() shouldNotBe null
    }

    @Test
    fun `readValue dispatches by field index`() {
        val now = currentTime()
        val version = Versions.newVersion(3, now)
        val fa = version as FieldAwareMessage
        val descriptor = version.descriptorForType
        descriptor.fields.forEach { field ->
            fa.readValue(field)
        }
    }

    @Nested inner class
    `Versions checkIsIncrement` {

        @Test
        fun `succeeding when the new version is an increment`() {
            val current = withNumber(5)
            val next = withNumber(6)
            Versions.checkIsIncrement(current, next)
        }

        @Test
        fun `throwing when the new version is not an increment`() {
            val current = withNumber(5)
            val same = withNumber(5)
            shouldThrow<IllegalArgumentException> {
                Versions.checkIsIncrement(current, same)
            }
        }
    }

    @Test
    fun `return the timestamp of a version`() {
        val now = currentTime()
        val v = Versions.newVersion(1, now)
        v.timestamp() shouldBe now
    }

    @Nested inner class
    `identify zero version` {

        @Test
        fun `returning true for number zero`() {
            withNumber(0).isZero shouldBe true
        }

        @Test
        fun `returning false for non-zero number`() {
            withNumber(1).isZero shouldBe false
        }
    }

    @Nested inner class
    `check increment` {

        @Test
        fun `returning true when number is greater`() {
            withNumber(3).isIncrement(withNumber(2)) shouldBe true
        }

        @Test
        fun `returning false when number is equal`() {
            withNumber(2).isIncrement(withNumber(2)) shouldBe false
        }

        @Test
        fun `returning false when number is less`() {
            withNumber(1).isIncrement(withNumber(2)) shouldBe false
        }
    }

    @Nested inner class
    `check increment or equal` {

        @Test
        fun `returning true when greater`() {
            withNumber(5).isIncrementOrEqual(withNumber(4)) shouldBe true
        }

        @Test
        fun `returning true when equal`() {
            withNumber(4).isIncrementOrEqual(withNumber(4)) shouldBe true
        }

        @Test
        fun `returning false when less`() {
            withNumber(3).isIncrementOrEqual(withNumber(4)) shouldBe false
        }
    }

    @Nested inner class
    `check decrement` {

        @Test
        fun `returning true when number is less`() {
            withNumber(1).isDecrement(withNumber(2)) shouldBe true
        }

        @Test
        fun `returning false when number is equal`() {
            withNumber(2).isDecrement(withNumber(2)) shouldBe false
        }

        @Test
        fun `returning false when number is greater`() {
            withNumber(3).isDecrement(withNumber(2)) shouldBe false
        }
    }

    @Nested inner class
    `check decrement or equal` {

        @Test
        fun `returning true when less`() {
            withNumber(1).isDecrementOrEqual(withNumber(2)) shouldBe true
        }

        @Test
        fun `returning true when equal`() {
            withNumber(2).isDecrementOrEqual(withNumber(2)) shouldBe true
        }

        @Test
        fun `returning false when greater`() {
            withNumber(5).isDecrementOrEqual(withNumber(2)) shouldBe false
        }
    }

    @Nested inner class
    `be Comparable, ordering by number` {

        @Test
        fun `treating a greater number as greater`() {
            (withNumber(3) > withNumber(2)) shouldBe true
        }

        @Test
        fun `treating equal numbers as equal regardless of the timestamp`() {
            withNumber(2).compareTo(withNumber(2)) shouldBe 0
        }

        @Test
        fun `treating a smaller number as smaller`() {
            (withNumber(1) < withNumber(2)) shouldBe true
        }
    }
}
