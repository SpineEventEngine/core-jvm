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

package io.spine.testing.server.blackbox;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.client.Client;
import io.spine.core.TenantId;
import io.spine.server.BoundedContextBuilder;
import io.spine.testing.server.blackbox.event.BbProjectCreated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.testing.core.given.GivenTenantId.generate;
import static io.spine.testing.server.blackbox.given.Given.createProject;
import static io.spine.testing.server.blackbox.given.Given.createdProjectState;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Multi-tenant `BlackBox` context should")
class MtBlackBoxTest
        extends BlackBoxTest<MtBlackBox> {

    @Override
    BoundedContextBuilder newBuilder() {
        return BoundedContextBuilder.assumingTests(/* multitenant = */ true);
    }

    @BeforeEach
    @OverridingMethodsMustInvokeSuper
    @Override
    void setUp() {
        super.setUp();
        context().withTenant(generate());
    }

    @Test
    @DisplayName("verify using a particular tenant ID")
    void verifyForDifferentTenants() {
        var john = generate();
        var carl = generate();
        var newUser = generate();
        var createJohnProject = createProject();
        var createCarlProject = createProject();
        var context = context()
                // Create a project for John.
                .withTenant(john)
                .receivesCommand(createJohnProject)

                // Create a project for Carl.
                .withTenant(carl)
                .receivesCommand(createCarlProject);
        // Verify project was created for John.
        context.withTenant(john)
               .assertEvents()
               .withType(BbProjectCreated.class)
               .hasSize(1);
        context.assertEntityWithState(createJohnProject.getProjectId(), BbProject.class)
               .hasStateThat()
               .isEqualTo(createdProjectState(createJohnProject));
        // Verify project was created for Carl.
        var contextForCarl = context.withTenant(carl);
        contextForCarl
                .assertEvents()
                .withType(BbProjectCreated.class)
                .hasSize(1);
        contextForCarl
                .assertEntityWithState(createCarlProject.getProjectId(), BbProject.class)
                .hasStateThat()
                .isEqualTo(createdProjectState(createCarlProject));
        // Verify nothing happened for a new user.
        var newUserContext = context.withTenant(newUser);
        newUserContext
                .assertCommands()
                .isEmpty();
        newUserContext
                .assertEvents()
                .isEmpty();
    }

    @Test
    @DisplayName("require tenant ID")
    void requireTenantId() {
        assertThrows(
                IllegalStateException.class,
                () -> BlackBox.from(BoundedContextBuilder.assumingTests(/* multitenant = */ true))
                              .assertEntityWithState(BbProjectId.generate(), BbProject.class)
        );
    }

    @Nested
    @DisplayName("create a `Client` with")
    class CreateClientWith {

        @Test
        @DisplayName("the tenant ID matching the `BlackBox` setting")
        void matchingTenant() {
            BlackBox context = context();
            List<TenantId> threeTenants =  ImmutableList.of(generate(), generate(), generate());
            for (var tenant : threeTenants) {
                assertTenant(tenant, (id) ->
                        context.withTenant(id).clients().withMatchingTenant());
            }
        }

        @Test
        @DisplayName("the specific tenant ID")
        void specificTenant() {
            BlackBox context = context();
            List<TenantId> threeTenants =  ImmutableList.of(generate(), generate(), generate());
            for (var tenant : threeTenants) {
                assertTenant(tenant, (id) -> context.clients().create(id));
            }
        }

        private void assertTenant(TenantId expected, Function<TenantId, Client> createClient) {
            var client = createClient.apply(expected);
            var actual = client.tenant();
            assertThat(actual).hasValue(expected);
        }
    }
}
