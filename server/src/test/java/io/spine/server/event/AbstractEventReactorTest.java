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

package io.spine.server.event;

import com.google.common.collect.ImmutableList;
import io.spine.server.BoundedContext;
import io.spine.server.event.given.AbstractReactorTestEnv.AutoCharityDonor;
import io.spine.server.event.given.AbstractReactorTestEnv.RestaurantNotifier;
import io.spine.server.event.given.AbstractReactorTestEnv.ServicePerformanceTracker;
import io.spine.testing.server.blackbox.BlackBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.event.given.AbstractReactorTestEnv.someOrderPaidFor;
import static io.spine.server.event.given.AbstractReactorTestEnv.someOrderReady;
import static io.spine.server.event.given.AbstractReactorTestEnv.someOrderServedInTime;
import static io.spine.server.event.given.AbstractReactorTestEnv.someOrderServedLate;

@DisplayName("`AbstractEventReactor` should")
class AbstractEventReactorTest {

    private BlackBox restaurantContext;
    private BlackBox charityContext;

    private AutoCharityDonor charityDonor;
    private ServicePerformanceTracker performanceTracker;

    @BeforeEach
    void setUp() {
        performanceTracker = new ServicePerformanceTracker();
        var notifier = new RestaurantNotifier();
        restaurantContext = BlackBox.from(
                BoundedContext.singleTenant("Restaurant")
                              .addEventDispatcher(performanceTracker)
                              .addEventDispatcher(notifier)
        );

        charityDonor = new AutoCharityDonor();
        charityContext = BlackBox.from(
                BoundedContext.singleTenant("Charity")
                              .addEventDispatcher(charityDonor)
        );
    }

    @AfterEach
    void closeContexts() {
        try {
            restaurantContext.close();
        } finally {
            charityContext.close();
        }
    }

    @Nested
    @DisplayName("while dealing with domestic events")
    class DomesticEvents {

        @Test
        @DisplayName("receive one")
        void receive() {
            var orderServed = someOrderServedInTime();
            restaurantContext.receivesEvent(orderServed);
            var ordersServed = performanceTracker.ordersServed();
            var ordersServedLate = performanceTracker.ordersServedLate();
            assertThat(ordersServed).containsExactly(orderServed);
            assertThat(ordersServedLate).isEmpty();
        }

        @Test
        @DisplayName("receive several")
        void receiveSeveral() {
            var eventsToEmit = ImmutableList.of(
                    someOrderServedInTime(), someOrderServedInTime(), someOrderServedLate()
            );
            eventsToEmit.forEach(restaurantContext::receivesEvent);
            var ordersServed = performanceTracker.ordersServed();
            var ordersServedLate = performanceTracker.ordersServedLate();
            assertThat(ordersServed).hasSize(eventsToEmit.size());
            var ordersInTime = ordersServed.stream()
                    .filter(orderServed -> !performanceTracker.servedLate(orderServed))
                    .count();
            assertThat(ordersInTime).isEqualTo(2);
            assertThat(ordersServedLate).hasSize(1);
        }

        @Test
        @DisplayName("react with one")
        void react() {
            var servedLate = someOrderServedLate();
            restaurantContext.receivesEvent(servedLate)
                             .assertEvents()
                             .withType(OrderServedLate.class)
                             .hasSize(1);
        }

        @Test
        @DisplayName("react with none")
        void reactWithNone() {
            var orderServed = someOrderServedInTime();
            restaurantContext.receivesEvent(orderServed)
                             .assertEvents()
                             .withType(OrderServedLate.class)
                             .isEmpty();
        }

        @Test
        @DisplayName("react with several events")
        void reactWithSeveral() {
            var orderIsReady = someOrderReady();
            var assertEvents = restaurantContext.receivesEvent(orderIsReady)
                                                .assertEvents();
            assertEvents.withType(CustomerNotified.class).hasSize(1);
            assertEvents.withType(DeliveryServiceNotified.class).hasSize(1);
        }
    }

    @Nested
    @DisplayName("while dealing with external events")
    class ExternalEvents {

        @Test
        @DisplayName("receive one")
        void receive() {
            var orderPaidFor = someOrderPaidFor();
            charityContext.receivesExternalEvent(orderPaidFor);

            var orderCost = orderPaidFor.getOrder().getPriceInUsd();
            var expectedDonationAmount = orderCost * 0.02;
            assertThat(charityDonor.totalDonated()).isEqualTo(expectedDonationAmount);
        }

        @Test
        @DisplayName("react to one")
        void reactToOne() {
            var orderPaidFor = someOrderPaidFor();
            charityContext.receivesExternalEvent(orderPaidFor)
                          .assertEvents()
                          .withType(DonationMade.class)
                          .hasSize(1);
        }

        @Test
        @DisplayName("react to several")
        void reactToSeveral() {
            var paidInRestaurant = someOrderPaidFor();
            var paidToDelivery = someOrderPaidFor();

            charityContext.receivesExternalEvent(paidToDelivery)
                          .receivesExternalEvent(paidInRestaurant)
                          .assertEvents()
                          .withType(DonationMade.class)
                          .hasSize(2);
        }
    }
}
