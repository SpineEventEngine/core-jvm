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

package io.spine.server;

import com.google.common.collect.Lists;
import com.google.common.testing.NullPointerTester;
import io.spine.option.EntityOption.Visibility;
import io.spine.server.entity.Repository;
import io.spine.server.entity.given.VisibilityGuardTestEnv.ExposedRepository;
import io.spine.server.entity.given.VisibilityGuardTestEnv.HiddenRepository;
import io.spine.server.entity.given.VisibilityGuardTestEnv.SubscribableRepository;
import io.spine.system.server.Company;
import io.spine.test.entity.FullAccessAggregate;
import io.spine.test.entity.HiddenAggregate;
import io.spine.test.entity.SubscribableAggregate;
import io.spine.type.TypeName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static io.spine.testing.server.TestContexts.buildForTests;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * See `client/spine/test/option/entity_options_should.proto` for definition of messages
 * used for aggregates and repositories in this test.
 */
@DisplayName("`VisibilityGuard` should")
class VisibilityGuardTest {

    private VisibilityGuard guard;
    private List<Repository<?, ?>> repositories;
    private BoundedContext boundedContext;

    @BeforeEach
    void setUp() {
        boundedContext = buildForTests(BoundedContextBuilder.assumingTests());
        repositories = Lists.newArrayList();

        guard = VisibilityGuard.newInstance();
        register(new ExposedRepository());
        register(new SubscribableRepository());
        register(new HiddenRepository());
    }

    private void register(Repository<?, ?> repository) {
        guard.register(repository);
        repositories.add(repository);
    }

    @AfterEach
    void shutDown() throws Exception {
        boundedContext.close();
        repositories.clear();
    }

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void passNullToleranceCheck() {
        new NullPointerTester()
                .setDefault(Repository.class, new ExposedRepository())
                .setDefault(Class.class, FullAccessAggregate.class)
                .setDefault(Visibility.class, Visibility.NONE)
                .testAllPublicInstanceMethods(guard);
    }

    @Test
    @DisplayName("give access to visible repos")
    void giveAccessToVisible() {
        assertTrue(guard.repositoryFor(FullAccessAggregate.class)
                        .isPresent());
        assertTrue(guard.repositoryFor(SubscribableAggregate.class)
                        .isPresent());
    }

    @Test
    @DisplayName("deny access to invisible repos")
    void denyAccessToInvisible() {
        assertFalse(guard.repositoryFor(HiddenAggregate.class)
                         .isPresent());
    }

    @Test
    @DisplayName("obtain repos by visibility")
    void obtainByVisibility() {
        var full = guard.entityStateTypes(Visibility.FULL);
        assertEquals(1, full.size());
        assertTrue(full.contains(TypeName.of(FullAccessAggregate.class)));

        var subscribable = guard.entityStateTypes(Visibility.SUBSCRIBE);
        assertEquals(1, subscribable.size());
        assertTrue(subscribable.contains(TypeName.of(SubscribableAggregate.class)));

        var hidden = guard.entityStateTypes(Visibility.NONE);
        assertEquals(1, hidden.size());
        assertTrue(hidden.contains(TypeName.of(HiddenAggregate.class)));
    }

    @Test
    @DisplayName("shutdown repositories")
    void shutdownRepositories() {
        guard.shutDownRepositories();

        for (var repository : repositories) {
            assertFalse(repository.isOpen());
        }
    }

    @Test
    @DisplayName("not allow double registration")
    void forbidDoubleRegistration() {
        assertThrows(IllegalStateException.class, () -> register(new ExposedRepository()));
    }

    @Test
    @DisplayName("reject unregistered state class")
    void rejectUnregisteredStateClass() {
        assertThrows(IllegalStateException.class, () -> guard.repositoryFor(Company.class));
    }
}
