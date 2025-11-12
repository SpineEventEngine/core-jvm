/*
 * Copyright 2025, TeamDev. All rights reserved.
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
        return BoundedContextBuilder.assumingTests(true);
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
                () -> BlackBox.from(BoundedContextBuilder.assumingTests(true))
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
