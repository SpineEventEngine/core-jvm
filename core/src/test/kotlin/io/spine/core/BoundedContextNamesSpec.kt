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
import io.spine.core.BoundedContextNames.assumingTests
import io.spine.core.BoundedContextNames.assumingTestsValue
import io.spine.core.BoundedContextNames.checkValid
import io.spine.core.BoundedContextNames.newName
import io.spine.testing.UtilityClassTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("`BoundedContextNames` utility should")
internal class BoundedContextNamesSpec : UtilityClassTest<BoundedContextNames>(
    BoundedContextNames::class.java
) {

    @Nested
    inner class `create a new name` {

        @Test
        fun `with the given string value`() {
            val name = newName("Orders")
            name.value shouldBe "Orders"
        }

        @Test
        fun `throw when the name is blank`() {
            assertThrows<IllegalArgumentException> { newName("  ") }
        }

        @Test
        fun `throw when the name is empty`() {
            assertThrows<IllegalArgumentException> { newName("") }
        }
    }

    @Nested
    inner class `validate a 'BoundedContextName'` {

        @Test
        fun `passing a non-blank name`() {
            val name = newName("Shipment")
            checkValid(name)
        }

        @Test
        fun `throwing on a blank-value name`() {
            val name = BoundedContextName.newBuilder()
                .setValue(" ")
                .buildPartial()
            assertThrows<IllegalArgumentException> { checkValid(name) }
        }
    }

    @Nested
    inner class `validate a 'String' context name` {

        @Test
        fun `passing a valid name`() {
            checkValid("MyContext")
        }

        @Test
        fun `throwing on empty string`() {
            assertThrows<IllegalArgumentException> { checkValid("") }
        }

        @Test
        fun `throwing on blank string`() {
            assertThrows<IllegalArgumentException> { checkValid("   ") }
        }
    }

    @Test
    fun `return 'assumingTests' value`() {
        assumingTestsValue() shouldNotBe null
        assumingTestsValue().isNotBlank() shouldBe true
    }

    @Test
    fun `return 'assumingTests' name`() {
        val name = assumingTests()
        name shouldNotBe null
        name.value shouldBe assumingTestsValue()
    }
}
