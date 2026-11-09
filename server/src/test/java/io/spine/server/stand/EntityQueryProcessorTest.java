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

package io.spine.server.stand;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.client.EntityStateWithVersion;
import io.spine.client.Query;
import io.spine.client.QueryFactory;
import io.spine.client.QueryId;
import io.spine.client.Target;
import io.spine.server.projection.ProjectionRepository;
import io.spine.server.stand.given.MenuRepository;
import io.spine.test.stand.Dish;
import io.spine.test.stand.DishAdded;
import io.spine.test.stand.Menu;
import io.spine.test.stand.MenuId;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.server.blackbox.BlackBox;
import io.spine.type.TypeName;
import io.spine.validation.NonValidated;
import io.spine.validation.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.client.OrderBy.Direction.DESCENDING;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.stand.given.MenuProjection.UUID_COLUMN;
import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`EntityQueryProcessor` should")
class EntityQueryProcessorTest {

    private static final int MENU_COUNT = 16;
    private static final TestActorRequestFactory factory =
            new TestActorRequestFactory(EntityQueryProcessorTest.class);
    private static final QueryFactory queries = factory.query();

    private BlackBox context;
    private EntityQueryProcessor processor;

    @BeforeEach
    void setUp() {
        ProjectionRepository<?, ?, ?> repository = new MenuRepository();
        context = BlackBox.singleTenant("Cafeteria", repository);
        processor = new EntityQueryProcessor(repository);
        fill();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("read all entities")
    void readAll() {
        var query = queries.all(Menu.class);
        var records = processor.process(query);
        assertThat(records).hasSize(MENU_COUNT);
    }

    @Test
    @DisplayName("read one by ID")
    void readById() {
        var event = addDish();
        var id = event.getId();
        var query = queries.byIds(Menu.class, ImmutableSet.of(id));
        var records = processor.process(query);
        assertThat(records).hasSize(1);
        var record = records.asList().get(0);
        var menu = state(record);
        assertThat(menu.getId()).isEqualTo(id);
        assertThat(menu.getDishList()).containsExactly(event.getDish());
    }

    @Test
    @DisplayName("read all entities and sort")
    void readAllAndSort() {
        var limit = MENU_COUNT - 4;
        var query = queries.select(Menu.class)
                           .orderBy(UUID_COLUMN, DESCENDING)
                           .limit(limit)
                           .build();
        var records = processor.process(query);
        var assertRecords = assertThat(records);
        assertRecords.hasSize(limit);
        var uuidOrder = comparing(
                (EntityStateWithVersion record) -> state(record).getId().getUuid()
        );
        assertRecords.isInOrder(uuidOrder.reversed());
    }

    @Test
    @DisplayName("fail if the query does not specify filters and `include_all` is not set")
    void failOnInvalidQuery() {
        @NonValidated Target target = Target.newBuilder()
                .setType(TypeName.of(Menu.class).value())
                .buildPartial();
        @NonValidated Query query = Query.newBuilder()
                .setId(QueryId.newBuilder().setValue(newUuid()))
                .setContext(factory.newActorContext())
                .setTarget(target)
                .buildPartial();
        assertThrows(ValidationException.class, () -> processor.process(query));
    }

    private void fill() {
        for (var i = 0; i < MENU_COUNT; i++) {
            addDish();
        }
    }

    @CanIgnoreReturnValue
    private DishAdded addDish() {
        var dish = Dish.newBuilder()
                .setTitle("Dead beef")
                .setPrice(42)
                .build();
        var id = MenuId.generate();
        var event = DishAdded.newBuilder()
                .setId(id)
                .setDish(dish)
                .build();
        context.receivesEvent(event);
        return event;
    }

    private static Menu state(EntityStateWithVersion record) {
        var state = record.getState();
        var menu = unpack(state, Menu.class);
        return menu;
    }
}
