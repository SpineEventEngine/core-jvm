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

package io.spine.server.stand

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.spine.client.OrderBy.Direction.ASCENDING
import io.spine.client.Query
import io.spine.client.orderBy
import io.spine.client.responseFormat
import io.spine.server.stand.given.MenuRepository
import io.spine.test.stand.Menu
import io.spine.testing.client.TestActorRequestFactory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Supplements the Java suite `QueryValidatorTest` with the specs of the rule for
 * the query limit.
 */
@DisplayName("`QueryValidator` should")
internal class QueryValidatorSpec {

    private val validator = QueryValidator(
        InMemoryTypeRegistry.newInstance().apply { register(MenuRepository()) }
    )
    private val query: Query = TestActorRequestFactory(javaClass).query().all(Menu::class.java)

    @Test
    fun `accept a limit with ordering`() {
        val ordering = orderBy {
            column = "uuid"
            direction = ASCENDING
        }
        val limited = query.toBuilder()
            .setFormat(responseFormat {
                limit = 5
                orderBy += ordering
            })
            .build()

        validator.validate(limited)
    }

    @Test
    fun `report a limit without ordering as a violation of the limit field`() {
        val limited = query.toBuilder()
            .setFormat(responseFormat { limit = 5 })
            .build()

        val exception = shouldThrow<InvalidRequestException> {
            validator.validate(limited)
        }

        exception.request shouldBe limited
        val violation = exception.validationError().constraintViolationList.single()
        violation.fieldPath.fieldNameList shouldContainExactly listOf("format", "limit")
    }
}
