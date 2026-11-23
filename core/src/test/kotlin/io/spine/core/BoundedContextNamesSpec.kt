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

package io.spine.core

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.core.BoundedContextNames.assumingTests
import io.spine.core.BoundedContextNames.assumingTestsValue
import io.spine.core.BoundedContextNames.checkValid
import io.spine.core.BoundedContextNames.contextOf
import io.spine.core.BoundedContextNames.newName
import io.spine.testing.UtilityClassTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tres.quattro.Counter
import uno.dos.Encounter

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

    @Nested inner class
    `find the context of a class` {

        @Test
        fun `from the annotation of its package`() {
            contextOf(Counter::class.java).value shouldBe "Counting"
        }

        @Test
        fun `giving one name to packages annotated with one name`() {
            contextOf(Encounter::class.java) shouldBe contextOf(Counter::class.java)
        }

        @Test
        fun `assuming the name used in tests when no package is annotated`() {
            contextOf(BoundedContextNamesSpec::class.java) shouldBe assumingTests()
        }

        @Test
        fun `giving the same name on every request`() {
            contextOf(Counter::class.java) shouldBeSameInstanceAs contextOf(Counter::class.java)
        }
    }
}
