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

import io.spine.base.EntityState;
import io.spine.client.ActorRequestFactory;
import io.spine.protobuf.AnyPacker;
import io.spine.server.Given.CustomerAggregate;
import io.spine.server.Given.CustomerAggregateRepository;
import io.spine.server.stand.given.StandTestEnv.MemoizeSubscriptionCallback;
import io.spine.test.commandservice.customer.Customer;
import io.spine.testing.core.given.GivenTenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.server.entity.given.Given.aggregateOfClass;
import static io.spine.server.stand.given.StandTestEnv.createRequestFactory;
import static io.spine.server.stand.given.StandTestEnv.einCustomer;
import static io.spine.server.stand.given.StandTestEnv.newContext;
import static io.spine.server.stand.given.StandTestEnv.subscribeAndActivate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Multitenant `Stand` should")
class MultitenantStandTest extends StandTest {

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        var tenantId = GivenTenantId.generate();

        setCurrentTenant(tenantId);
        setMultitenant(true);
        setRequestFactory(createRequestFactory(tenantId));
    }

    @AfterEach
    void tearDown() {
        clearCurrentTenant();
    }

    @Test
    @DisplayName("not trigger updates of aggregate records for another tenant subscriptions")
    void updateOnlySameTenant() {
        var repository = new CustomerAggregateRepository();
        var stand = closeAfterTest(newContext(isMultitenant(), repository)).stand();

        // --- Default Tenant
        var requestFactory = getRequestFactory();
        var defaultTenantCallback =
                subscribeToAllOf(stand, requestFactory, Customer.class);

        // --- Another Tenant
        var anotherTenant = GivenTenantId.generate();
        var anotherFactory = createRequestFactory(anotherTenant);
        var anotherCallback =
                subscribeToAllOf(stand, anotherFactory, Customer.class);

        // Trigger updates in Default Tenant.
        var customer = einCustomer();
        var customerId = customer.getId();
        var version = 1;
        var entity = aggregateOfClass(CustomerAggregate.class)
                .withId(customerId)
                .withState(customer)
                .withVersion(version)
                .build();
        stand.post(entity, repository.lifecycleOf(customerId));

        var packedState = AnyPacker.pack(customer);
        // Verify that Default Tenant callback has got the update.
        assertEquals(packedState, defaultTenantCallback.newEntityState());

        // And Another Tenant callback has not been called.
        assertNull(anotherCallback.newEntityState());
    }

    MemoizeSubscriptionCallback
    subscribeToAllOf(Stand stand,
                     ActorRequestFactory requestFactory,
                     Class<? extends EntityState<?>> stateClass) {
        var allCustomers = requestFactory.topic()
                                         .allOf(stateClass);
        var action = new MemoizeSubscriptionCallback();
        subscribeAndActivate(stand, allCustomers, action);

        assertNull(action.newEntityState());
        return action;
    }
}
