/*
 * Copyright 2023, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
package io.spine.client

import com.google.common.testing.NullPointerTester
import com.google.common.truth.Subject
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Int32Value
import com.google.protobuf.Message
import com.google.protobuf.Timestamp
import com.google.protobuf.util.Durations
import com.google.protobuf.util.Timestamps.subtract
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.base.EntityState
import io.spine.base.Identifier.newUuid
import io.spine.base.Time
import io.spine.client.CompositeFilter.CompositeOperator.ALL
import io.spine.client.CompositeFilter.CompositeOperator.EITHER
import io.spine.client.Filters.all
import io.spine.client.Filters.either
import io.spine.client.Filters.eq
import io.spine.client.OrderBy.Direction.ASCENDING
import io.spine.client.OrderBy.Direction.DESCENDING
import io.spine.client.OrderBy.Direction.OD_UNKNOWN
import io.spine.client.OrderBy.Direction.UNRECOGNIZED
import io.spine.client.given.TestEntities.randomId
import io.spine.protobuf.AnyPacker
import io.spine.protobuf.TypeConverter.toObject
import io.spine.test.client.TestEntity
import io.spine.test.client.TestEntityId
import io.spine.testing.DisplayNames
import io.spine.testing.core.given.GivenUserId
import io.spine.time.ZoneIds
import io.spine.type.TypeUrl
import java.util.*
import java.util.stream.Collectors.toList
import kotlin.Any
import kotlin.Int
import kotlin.String
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import com.google.protobuf.Any as AnyProto

@DisplayName("`QueryBuilder` should")
internal class QueryBuilderSpec {

    companion object {
        
        private val ACTOR = GivenUserId.of(newUuid())
        private val ZONE_ID = ZoneIds.systemDefault()

        val TEST_ENTITY_TYPE: Class<out EntityState<*>> = TestEntity::class.java
        val TEST_ENTITY_TYPE_URL: TypeUrl = TypeUrl.of(TEST_ENTITY_TYPE)
        
        const val SECOND_FIELD = "second_field"
        const val FIRST_FIELD = "first_field"

        fun orderBy(column: String, direction: OrderBy.Direction): OrderBy = orderBy {
            this.column = column
            this.direction = direction
        }

        fun requestFactory(): ActorRequestFactory = ActorRequestFactory.newBuilder().apply {
            setZoneId(ZONE_ID)
            actor = ACTOR
        }.build()
    }

    /**
     * Allows to execute Truth assertions in a block.
     */
    operator fun <T: Subject> T.invoke(assertions: T.() -> Unit): Unit = this.assertions()

    private lateinit var factory: QueryFactory
    
    @BeforeEach
    fun createFactory() {
        factory = requestFactory().query()
    }

    @Nested
    internal inner class `check arguments and` {
        
        @Test
        @DisplayName(DisplayNames.NOT_ACCEPT_NULLS)
        fun notAcceptNulls() {
            val tester = NullPointerTester()
            tester.testAllPublicStaticMethods(QueryBuilder::class.java)
            tester.testAllPublicInstanceMethods(factory.select(TEST_ENTITY_TYPE))
        }

        @Test
        fun `throw 'IAE' if negative limit int value is provided`() {
            assertThrows<IllegalArgumentException> {
                factory.select(TEST_ENTITY_TYPE).limit(-10)
            }
        }

        @Test
        fun `throw 'IAE' if limit int value is 0`() {
            assertThrows<IllegalArgumentException> {
                factory.select(TEST_ENTITY_TYPE).limit(0)
            }
        }

        @Test
        fun `throw 'IAE' if order direction is not ASCENDING or DESCENDING`() {
            val select = factory.select(TEST_ENTITY_TYPE)
                .orderBy(FIRST_FIELD, ASCENDING)
                .orderBy(FIRST_FIELD, DESCENDING)
            
            assertThrows<IllegalArgumentException> {
                select.orderBy(FIRST_FIELD, OD_UNKNOWN) 
            }
            assertThrows<IllegalArgumentException> {
                select.orderBy(FIRST_FIELD, UNRECOGNIZED) 
            }
        }
    }

    @Nested
    internal inner class `create query` {

        @Test
        fun `by only entity type`() {
            val query = factory.select(TEST_ENTITY_TYPE).build()
            query shouldNotBe null

            with(query.format) {
                hasFieldMask() shouldBe false
                orderByCount shouldBe 0
                limit shouldBe 0
            }

            with(query.target) {
                includeAll shouldBe true
                type shouldBe TEST_ENTITY_TYPE_URL.value()
            }
        }

        @Test
        fun `with order`() {
            val query = factory.select(TEST_ENTITY_TYPE)
                .orderBy(FIRST_FIELD, ASCENDING)
                .build()
            query shouldNotBe null

            with(query.format) {
                val expectedOrderBy = orderBy(FIRST_FIELD, ASCENDING)

                hasFieldMask() shouldBe false
                getOrderBy(0) shouldBe expectedOrderBy
                limit shouldBe 0
            }

            with(query.target) {
                includeAll shouldBe true
                type shouldBe TEST_ENTITY_TYPE_URL.value()
            }
        }

        @Test
        fun `with limit`() {
            val expectedLimit = 15
            val query = factory.select(TEST_ENTITY_TYPE)
                .orderBy(SECOND_FIELD, DESCENDING)
                .limit(expectedLimit)
                .build()
            query shouldNotBe null

            with(query.format) {
                val expectedOrderBy = orderBy(SECOND_FIELD, DESCENDING)

                hasFieldMask() shouldBe false
                getOrderBy(0) shouldBe expectedOrderBy
                limit shouldBe expectedLimit
            }

            with(query.target) {
                includeAll shouldBe true
                type shouldBe TEST_ENTITY_TYPE_URL.value()
            }
        }

        @Test
        fun `by ID`() {
            val id1 = 314
            val id2 = 271
            val query = factory.select(TEST_ENTITY_TYPE)
                .byId(id1, id2)
                .build()
            query shouldNotBe null
            query.format.hasFieldMask() shouldBe false

            val target = query.target
            target.includeAll shouldBe false

            val entityFilters = target.filters
            val idFilter = entityFilters.idFilter
            val idValues: Collection<AnyProto> = idFilter.idList
            val intIdValues: Collection<Int> = idValues
                .stream()
                .map { id: AnyProto ->
                    toObject(
                        id, Int::class.java
                    )
                }
                .collect(toList())
            assertThat(idValues).hasSize(2)
            assertThat(intIdValues).containsExactly(id1, id2)
        }

        @Test
        fun `by field mask`() {
            val fieldName = "TestEntity.firstField"
            val query = factory.select(TEST_ENTITY_TYPE)
                .withMask(fieldName)
                .build()
            query shouldNotBe null

            val format = query.format
            format.hasFieldMask() shouldBe true

            val mask = format.fieldMask
            val fieldNames: Collection<String> = mask.pathsList
            val assertFieldNames = assertThat(fieldNames)
            assertFieldNames.hasSize(1)
            assertFieldNames.contains(fieldName)
        }

        @Test
        fun `by column filter`() {
            val columnName = "myImaginaryColumn"
            val columnValue: Any = 42
            val query = factory.select(TEST_ENTITY_TYPE)
                .where(eq(columnName, columnValue))
                .build()
            query shouldNotBe null

            val target = query.target
            target.includeAll shouldBe false

            val entityFilters = target.filters
            val aggregatingFilters = entityFilters.filterList
            assertThat(aggregatingFilters).hasSize(1)

            val aggregatingFilter = aggregatingFilters[0]
            val filters: Collection<Filter> = aggregatingFilter.filterList
            assertThat(filters).hasSize(1)

            val actualValue = findByName(filters, columnName).value
            columnValue shouldNotBe null
            val messageValue = AnyPacker.unpack(actualValue, Int32Value::class.java)
            val actualGenericValue = messageValue.value
            actualGenericValue shouldBe columnValue
        }

        @Test
        fun `by multiple column filters`() {
            val columnName1 = "myColumn"
            val columnValue1: Any = 42
            val columnName2 = "oneMore"
            val columnValue2: Any = randomId()
            val query = factory.select(TEST_ENTITY_TYPE)
                .where(
                    eq(columnName1, columnValue1),
                    eq(columnName2, columnValue2)
                )
                .build()
            query shouldNotBe null
            val target = query.target
            target.includeAll shouldBe false
            val entityFilters = target.filters
            val aggregatingFilters = entityFilters.filterList
            assertThat(aggregatingFilters)
                .hasSize(1)
            val filters: Collection<Filter> = aggregatingFilters[0]
                .filterList
            val actualValue1 = findByName(filters, columnName1).value
            actualValue1 shouldNotBe null
            val actualGenericValue1 = toObject(actualValue1, Int::class.java)
            actualGenericValue1 shouldBe columnValue1
            val actualValue2 = findByName(filters, columnName2).value
            actualValue2 shouldNotBe null
            val actualGenericValue2: Message = toObject(actualValue2, TestEntityId::class.java)
            actualGenericValue2 shouldBe columnValue2
        }

        /**
         * A big test for the grouping operators proper building.
         */
        @Test
        fun `by column filter grouping`() {
            val establishedTimeColumn = "establishedTime"
            val companySizeColumn = "companySize"
            val countryColumn = "country"
            val countryName = "Ukraine"
            val twoDaysAgo = subtract(Time.currentTime(), Durations.fromHours(-48))
            val query = factory.select(TEST_ENTITY_TYPE)
                .where(
                    all(
                        Filters.ge(companySizeColumn, 50),
                        Filters.le(companySizeColumn, 1000)
                    ),
                    either(
                        Filters.gt(establishedTimeColumn, twoDaysAgo),
                        eq(countryColumn, countryName)
                    )
                )
                .build()
            val target = query.target
            val filters = target.filters
                .filterList
            assertThat(filters).hasSize(2)

            val firstFilter = filters[0]
            val secondFilter = filters[1]
            val allFilters: List<Filter>
            val eitherFilters: List<Filter>
            if (firstFilter.operator == ALL) {
                secondFilter.operator shouldBe EITHER
                allFilters = firstFilter.filterList
                eitherFilters = secondFilter.filterList
            } else {
                secondFilter.operator shouldBe ALL
                eitherFilters = firstFilter.filterList
                allFilters = secondFilter.filterList
            }

            assertThat(allFilters).hasSize(2)
            assertThat(eitherFilters).hasSize(2)

            val companySizeLowerBound = allFilters[0]
            val columnName1 = companySizeLowerBound.fieldPath
                .getFieldName(0)
            columnName1 shouldBe companySizeColumn
            toObject(companySizeLowerBound.value, Int::class.java).toLong() shouldBe 50L
            companySizeLowerBound.operator shouldBe Filter.Operator.GREATER_OR_EQUAL
            val companySizeHigherBound = allFilters[1]
            val columnName2 = companySizeHigherBound.fieldPath
                .getFieldName(0)
            columnName2 shouldBe companySizeColumn
            toObject(companySizeHigherBound.value, Int::class.java).toLong() shouldBe 1000L
            companySizeHigherBound.operator shouldBe Filter.Operator.LESS_OR_EQUAL
            val establishedTimeFilter = eitherFilters[0]
            val columnName3 = establishedTimeFilter.fieldPath
                .getFieldName(0)
            columnName3 shouldBe establishedTimeColumn
            toObject(establishedTimeFilter.value, Timestamp::class.java) shouldBe twoDaysAgo
            establishedTimeFilter.operator shouldBe Filter.Operator.GREATER_THAN
            val countryFilter = eitherFilters[1]
            val columnName4 = countryFilter.fieldPath
                .getFieldName(0)
            columnName4 shouldBe countryColumn
            toObject(countryFilter.value, String::class.java) shouldBe countryName
            countryFilter.operator shouldBe Filter.Operator.EQUAL
        }

        /**
         * A big test case covering the query arguments coexistence.
         */
        @Test
        fun `by all available arguments`() {
            val id1 = 314
            val id2 = 271
            val limit = 10
            val columnName1 = "column1"
            val columnValue1: Any = 42
            val columnName2 = "column2"
            val columnValue2: Any = randomId()
            val fieldName = "TestEntity.secondField"
            val query = factory.select(TEST_ENTITY_TYPE)
                .withMask(fieldName)
                .byId(id1, id2)
                .where(
                    eq(columnName1, columnValue1),
                    eq(columnName2, columnValue2)
                )
                .orderBy(SECOND_FIELD, DESCENDING)
                .limit(limit)
                .build()
            query shouldNotBe null
            val format = query.format
            val mask = format.fieldMask
            val fieldNames: Collection<String> = mask.pathsList
            val assertFieldNames = assertThat(fieldNames)
            assertFieldNames.hasSize(1)
            assertFieldNames.containsExactly(fieldName)
            val target = query.target
            target.includeAll shouldBe false
            val entityFilters = target.filters
            val idFilter = entityFilters.idFilter
            val idValues: Collection<AnyProto> = idFilter.idList
            val intIdValues: Collection<Int> = idValues
                .stream()
                .map { id: AnyProto ->
                    toObject(id, Int::class.java)
                }
                .collect(toList())
            assertThat(idValues)
                .hasSize(2)
            assertThat(intIdValues).containsAtLeast(id1, id2)

            // Check query params
            val aggregatingFilters = entityFilters.filterList
            assertThat(aggregatingFilters)
                .hasSize(1)
            val filters: Collection<Filter> = aggregatingFilters[0]
                .filterList
            assertThat(filters)
                .hasSize(2)
            val actualValue1 = findByName(filters, columnName1).value
            actualValue1 shouldNotBe null
            val actualGenericValue1 =
                toObject(actualValue1, Int::class.java)
            actualGenericValue1 shouldBe columnValue1
            val actualValue2 = findByName(filters, columnName2).value
            actualValue2 shouldNotBe null
            val actualGenericValue2: Message =
                toObject(actualValue2, TestEntityId::class.java)
            actualGenericValue2 shouldBe columnValue2
            val expectedOrderBy = orderBy(
                SECOND_FIELD,
                DESCENDING
            )
            format.getOrderBy(0) shouldBe expectedOrderBy
            format.limit shouldBe limit
        }

        private fun findByName(filters: Iterable<Filter>, name: String): Filter {
            for (filter in filters) {
                if (filter.fieldPath.getFieldName(0) == name) {
                    return filter
                }
            }
            fail<Any>(String.format(Locale.US, "No Filter found for %s.", name))
            error("Unreachable code is reached!") // Never happens unless JUnit is broken.
        }
    }

    @Nested
    internal inner class `persist only last given` {

        @Test
        fun `IDs clause`() {
            val genericIds: Iterable<*> = listOf(newUuid(), -1, randomId())
            val longIds = arrayOf(1L, 2L, 3L)
            val messageIds = arrayOf<Message>(randomId(), randomId(), randomId())
            val stringIds = arrayOf(newUuid(), newUuid(), newUuid())
            val intIds = arrayOf(4, 5, 6)
            val query = factory.select(TEST_ENTITY_TYPE)
                .byId(genericIds)
                .byId(*longIds)
                .byId(*stringIds)
                .byId(*intIds)
                .byId(*messageIds)
                .build()
            query shouldNotBe null

            val target = query.target
            val filters = target.filters
            val entityIds: Collection<AnyProto> = filters.idFilter
                .idList
            assertThat(entityIds).hasSize(messageIds.size)

            val actualValues: Iterable<Message> = entityIds
                .stream()
                .map { id: AnyProto -> toObject(id, TestEntityId::class.java) }
                .collect(toList())
            assertThat(actualValues)
                .containsAtLeastElementsIn(messageIds)
        }

        @Test
        fun `field mask`() {
            val iterableFields: Iterable<String> = setOf("TestEntity.firstField")
            val arrayFields = arrayOf("TestEntity.secondField")
            val query = factory.select(TEST_ENTITY_TYPE)
                .withMask(iterableFields)
                .withMask(*arrayFields)
                .build()
            query shouldNotBe null

            val mask = query.format.fieldMask
            val maskFields: Collection<String> = mask.pathsList
            assertThat(maskFields)
                .hasSize(arrayFields.size)
            assertThat(maskFields)
                .containsAtLeastElementsIn(arrayFields)
        }

        @Test
        fun limit() {
            val expectedLimit = 10
            val query = factory.select(TEST_ENTITY_TYPE)
                .orderBy(FIRST_FIELD, ASCENDING)
                .limit(2)
                .limit(5)
                .limit(expectedLimit)
                .build()
            query shouldNotBe null
            query.format.limit shouldBe expectedLimit
        }

        @Test
        fun order() {
            val query = factory.select(TEST_ENTITY_TYPE)
                .orderBy(FIRST_FIELD, ASCENDING)
                .orderBy(SECOND_FIELD, ASCENDING)
                .orderBy(SECOND_FIELD, DESCENDING)
                .orderBy(FIRST_FIELD, DESCENDING)
                .build()
            query shouldNotBe null

            query.format.getOrderBy(0) shouldBe orderBy(FIRST_FIELD, DESCENDING)
        }
    }

    @Test
    fun `provide proper 'toString()' method`() {
        val id1 = 314
        val id2 = 271
        val columnName1 = "column1"
        val columnValue1: Any = 42
        val columnName2 = "column2"
        val columnValue2: Message = randomId()
        val fieldName = "TestEntity.secondField"
        val builder = factory.select(TEST_ENTITY_TYPE)
            .withMask(fieldName)
            .byId(id1, id2)
            .where(
                eq(columnName1, columnValue1),
                eq(columnName2, columnValue2)
            )
        val stringForm = builder.toString()
        val assertStringForm = assertThat(stringForm)
        assertStringForm {
            contains(TEST_ENTITY_TYPE.simpleName)
            contains(id1.toString())
            contains(id2.toString())
            contains(columnName1)
            contains(columnName2)
        }
    }
}
