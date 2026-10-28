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

package io.spine.server

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.test.delivery.Calc
import io.spine.type.TypeUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests the record of served entity state types on its own.
 *
 * The record is a new instance, not the one of [ServerEnvironment]. The contexts have no
 * repositories, so building them adds nothing to the record of the environment.
 */
@DisplayName("`ServedStateTypes` should")
internal class ServedStateTypesSpec {

    private val record = ServedStateTypes()
    private val first = BoundedContext.singleTenant("First").build()
    private val second = BoundedContext.singleTenant("Second").build()

    @AfterEach
    fun closeContexts() {
        first.close()
        second.close()
    }

    @Test
    fun `keep a type served by another context when told to forget it for a context`() {
        record.serve(calc, first)

        record.forget(calc, second)

        record.contextServing(calc) shouldBeSameInstanceAs first
    }

    @Test
    fun `forget a type served by the given context`() {
        record.serve(calc, first)

        record.forget(calc, first)

        record.contextServing(calc).shouldBeNull()
    }

    private companion object {
        val calc: TypeUrl = TypeUrl.of(Calc::class.java)
    }
}
