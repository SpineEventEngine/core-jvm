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

package io.spine.client;

import com.google.common.testing.NullPointerTester;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import io.spine.test.client.TestEntity;
import io.spine.test.client.TestEntityId;
import io.spine.testing.core.given.GivenUserId;
import io.spine.time.ZoneIds;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.client.CompositeFilter.CompositeOperator.ALL;
import static io.spine.client.CompositeFilter.CompositeOperator.EITHER;
import static io.spine.client.Filter.Operator.EQUAL;
import static io.spine.client.Filter.Operator.GREATER_OR_EQUAL;
import static io.spine.client.Filter.Operator.LESS_OR_EQUAL;
import static io.spine.client.Filters.all;
import static io.spine.client.Filters.either;
import static io.spine.client.Filters.eq;
import static io.spine.client.Filters.ge;
import static io.spine.client.Filters.le;
import static io.spine.client.given.TestEntities.randomId;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.protobuf.TypeConverter.toObject;
import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("`Topic` builder should")
@SuppressWarnings("DuplicateStringLiteralInspection")
class TopicBuilderTest {

    private static final Class<? extends Message> TEST_ENTITY_TYPE = TestEntity.class;
    private static final TypeUrl TEST_ENTITY_TYPE_URL = TypeUrl.of(TEST_ENTITY_TYPE);
    private TopicFactory factory;

    static Filter findByName(Iterable<Filter> filters, String name) {
        for (var filter : filters) {
            if (filter.getFieldPath()
                      .getFieldName(0)
                      .equals(name)) {
                return filter;
            }
        }
        fail(format("No Filter found for %s. field", name));
        // avoid returning `null`
        throw new RuntimeException("never happens unless JUnit is broken");
    }

    private static ActorRequestFactory requestFactory() {
        return ActorRequestFactory.newBuilder()
                .setZoneId(ZoneIds.systemDefault())
                .setActor(GivenUserId.of(newUuid()))
                .build();
    }

    @BeforeEach
    void createFactory() {
        factory = requestFactory().topic();
    }

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void notAcceptNulls() {
        new NullPointerTester().testAllPublicStaticMethods(TopicBuilder.class);
    }

    @Nested
    @DisplayName("create a topic")
    class CreateTopic {

        @Test
        @DisplayName("for the type")
        void byType() {
            var topic = factory.select(TEST_ENTITY_TYPE)
                               .build();
            assertNotNull(topic);

            var target = topic.getTarget();
            assertTrue(target.getIncludeAll());

            assertEquals(TEST_ENTITY_TYPE_URL.value(), target.getType());
        }

        @Test
        @DisplayName("for IDs")
        void byId() {
            var id1 = 314;
            var id2 = 271;
            var topic = factory.select(TEST_ENTITY_TYPE)
                               .byId(id1, id2)
                               .build();
            assertNotNull(topic);

            var target = topic.getTarget();
            assertFalse(target.getIncludeAll());

            var entityFilters = target.getFilters();
            var idFilter = entityFilters.getIdFilter();
            Collection<Any> idValues = idFilter.getIdList();
            Collection<Integer> intIdValues = idValues
                    .stream()
                    .map(id -> toObject(id, int.class))
                    .collect(Collectors.toList());

            assertThat(intIdValues)
                 .containsExactly(id1, id2);
        }

        @Test
        @DisplayName("ignoring the field mask")
        @SuppressWarnings("deprecation") // Checking that the deprecated API does nothing.
        void ignoringFieldMask() {
            var topic = factory.select(TEST_ENTITY_TYPE)
                               .withMask("TestEntity.firstField")
                               .withMask(singleton("TestEntity.secondField"))
                               .build();
            assertNotNull(topic);
            assertFalse(topic.hasFieldMask());
        }

        @Test
        @DisplayName("matching a predicate")
        void byFilter() {
            var columnName = "first_field";
            Object columnValue = "a column value";

            var topic = factory.select(TEST_ENTITY_TYPE)
                               .where(eq(columnName, columnValue))
                               .build();
            assertNotNull(topic);
            var target = topic.getTarget();
            assertFalse(target.getIncludeAll());

            var entityFilters = target.getFilters();
            var aggregatingFilters = entityFilters.getFilterList();
            assertThat(aggregatingFilters)
                 .hasSize(1);
            var aggregatingFilter = aggregatingFilters.get(0);
            Collection<Filter> filters = aggregatingFilter.getFilterList();
            assertThat(filters)
                 .hasSize(1);
            var actualValue = findByName(filters, columnName).getValue();
            assertNotNull(columnValue);
            var messageValue = unpack(actualValue, StringValue.class);
            var actualGenericValue = messageValue.getValue();
            assertEquals(columnValue, actualGenericValue);
        }

        @Test
        @DisplayName("matching multiple predicates")
        void byMultipleFilters() {
            var columnName1 = "first_field";
            Object columnValue1 = "the column value";
            var columnName2 = "second_field";
            Object columnValue2 = false;

            var topic = factory.select(TEST_ENTITY_TYPE)
                               .where(eq(columnName1, columnValue1),
                                        eq(columnName2, columnValue2))
                               .build();
            assertNotNull(topic);
            var target = topic.getTarget();
            assertFalse(target.getIncludeAll());

            var entityFilters = target.getFilters();
            var aggregatingFilters = entityFilters.getFilterList();
            assertThat(aggregatingFilters)
                 .hasSize(1);
            Collection<Filter> filters = aggregatingFilters.get(0)
                                                           .getFilterList();
            var actualValue1 = findByName(filters, columnName1).getValue();
            assertNotNull(actualValue1);
            var actualGenericValue1 = toObject(actualValue1, String.class);
            assertEquals(columnValue1, actualGenericValue1);

            var actualValue2 = findByName(filters, columnName2).getValue();
            assertNotNull(actualValue2);
            boolean actualGenericValue2 = toObject(actualValue2, boolean.class);
            assertEquals(columnValue2, actualGenericValue2);
        }

        @SuppressWarnings("OverlyLongMethod")
        // A big test for the grouping operators proper building.
        @Test
        @DisplayName("with filter groupings")
        void byFilterGrouping() {
            var firstColumn = "first_field";
            var secondColumn = "second_field";
            var thirdColumn = "third_field";
            var countryName = "Ukraine";

            var topic = factory.select(TEST_ENTITY_TYPE)
                               .where(all(ge(thirdColumn, 50),
                                            le(thirdColumn, 1000)),
                                        either(eq(secondColumn, false),
                                               eq(firstColumn, countryName)))
                               .build();
            var target = topic.getTarget();
            var filters = target.getFilters()
                                .getFilterList();
            assertThat(filters)
                 .hasSize(2);

            var firstFilter = filters.get(0);
            var secondFilter = filters.get(1);

            List<Filter> allFilters;
            List<Filter> eitherFilters;
            if (firstFilter.getOperator() == ALL) {
                assertEquals(EITHER, secondFilter.getOperator());
                allFilters = firstFilter.getFilterList();
                eitherFilters = secondFilter.getFilterList();
            } else {
                assertEquals(ALL, secondFilter.getOperator());
                eitherFilters = firstFilter.getFilterList();
                allFilters = secondFilter.getFilterList();
            }
            assertThat(allFilters)
                 .hasSize(2);
            assertThat(eitherFilters)
                 .hasSize(2);

            var lowerBound = allFilters.get(0);
            var columnName1 = lowerBound.getFieldPath()
                                        .getFieldName(0);
            assertEquals(thirdColumn, columnName1);
            assertEquals(50L, (long) toObject(lowerBound.getValue(), int.class));
            assertEquals(GREATER_OR_EQUAL, lowerBound.getOperator());

            var higherBound = allFilters.get(1);
            var columnName2 = higherBound.getFieldPath()
                                         .getFieldName(0);
            assertEquals(thirdColumn, columnName2);
            assertEquals(1000L, (long) toObject(higherBound.getValue(), int.class));
            assertEquals(LESS_OR_EQUAL, higherBound.getOperator());

            var establishedTimeFilter = eitherFilters.get(0);
            var columnName3 = establishedTimeFilter.getFieldPath()
                                                   .getFieldName(0);
            assertEquals(secondColumn, columnName3);
            assertEquals(false, toObject(establishedTimeFilter.getValue(), boolean.class));
            assertEquals(EQUAL, establishedTimeFilter.getOperator());

            var countryFilter = eitherFilters.get(1);
            var columnName4 = countryFilter.getFieldPath()
                                           .getFieldName(0);
            assertEquals(firstColumn, columnName4);
            assertEquals(countryName, toObject(countryFilter.getValue(), String.class));
            assertEquals(EQUAL, countryFilter.getOperator());
        }

        @SuppressWarnings("OverlyLongMethod")
        // A big test case covering the query arguments coexistence.
        @Test
        @DisplayName("with all parameters")
        void byAllArguments() {
            var id1 = 314;
            var id2 = 271;
            var columnName1 = "first_field";
            Object columnValue1 = "some column value";
            var columnName2 = "second_field";
            Object columnValue2 = true;
            var query = factory.select(TEST_ENTITY_TYPE)
                               .byId(id1, id2)
                               .where(eq(columnName1, columnValue1),
                                        eq(columnName2, columnValue2))
                               .build();
            assertNotNull(query);

            var target = query.getTarget();
            assertFalse(target.getIncludeAll());
            var targetFilters = target.getFilters();

            // Check IDs
            var idFilter = targetFilters.getIdFilter();
            Collection<Any> idValues = idFilter.getIdList();
            Collection<Integer> intIdValues = idValues
                    .stream()
                    .map(id -> toObject(id, int.class))
                    .collect(Collectors.toList());
            assertThat(intIdValues)
                .containsExactly(id1, id2);

            // Check query params
            var aggregatingFilters = targetFilters.getFilterList();
            assertThat(aggregatingFilters)
                 .hasSize(1);
            Collection<Filter> filters = aggregatingFilters.get(0)
                                                           .getFilterList();
            assertThat(filters)
                 .hasSize(2);

            var actualValue1 = findByName(filters, columnName1).getValue();
            assertNotNull(actualValue1);
            var actualGenericValue1 = toObject(actualValue1, String.class);
            assertEquals(columnValue1, actualGenericValue1);

            var actualValue2 = findByName(filters, columnName2).getValue();
            assertNotNull(actualValue2);
            boolean actualGenericValue2 = toObject(actualValue2, boolean.class);
            assertEquals(columnValue2, actualGenericValue2);
        }
    }

    @SuppressWarnings("CheckReturnValue")
    @Test
    @DisplayName("fail when creating a topic with an invalid filter")
    void failOnInvalidFilter() {
        var builder = new TopicBuilder(TEST_ENTITY_TYPE, factory);
        builder.where(eq("non_existent_column", "some value"));

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Nested
    @DisplayName("persist only last received")
    class Persist {

        @Test
        @DisplayName("IDs")
        void lastIds() {
            Iterable<?> genericIds = asList(newUuid(), -1, randomId());
            Long[] longIds = {1L, 2L, 3L};
            Message[] messageIds = {randomId(), randomId(), randomId()};
            var stringIds = new String[]{newUuid(), newUuid(), newUuid()};
            Integer[] intIds = {4, 5, 6};

            var topic = factory.select(TEST_ENTITY_TYPE)
                               .byId(genericIds)
                               .byId(longIds)
                               .byId(stringIds)
                               .byId(intIds)
                               .byId(messageIds)
                               .build();
            assertNotNull(topic);

            var target = topic.getTarget();
            var filters = target.getFilters();
            Collection<Any> entityIds = filters.getIdFilter()
                                               .getIdList();
            assertThat(entityIds)
                 .hasSize(messageIds.length);
            Iterable<? extends Message> actualValues = entityIds
                    .stream()
                    .map(id -> toObject(id, TestEntityId.class))
                    .collect(Collectors.toList());
            assertThat(actualValues)
                .containsExactlyElementsIn(messageIds);
        }

    }

    @Test
    @DisplayName("be represented as a comprehensible string")
    void supportToString() {
        var id1 = 314;
        var id2 = 271;
        var columnName1 = "column1";
        Object columnValue1 = 42;
        var columnName2 = "column2";
        Message columnValue2 = randomId();
        var builder = factory.select(TEST_ENTITY_TYPE)
                             .byId(id1, id2)
                             .where(eq(columnName1, columnValue1),
                                             eq(columnName2, columnValue2));
        var topicString = builder.toString();

        var assertTopic = assertThat(topicString);
        assertTopic.contains(TEST_ENTITY_TYPE.getSimpleName());
        assertTopic.contains(String.valueOf(id1));
        assertTopic.contains(String.valueOf(id2));
        assertTopic.contains(columnName1);
        assertTopic.contains(columnName2);
    }
}
