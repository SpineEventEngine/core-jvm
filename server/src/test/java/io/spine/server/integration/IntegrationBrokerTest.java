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

package io.spine.server.integration;

import io.spine.server.integration.broker.ArchivePhotos;
import io.spine.server.integration.broker.CreditsHeld;
import io.spine.server.integration.broker.PhotosMovedToWarehouse;
import io.spine.server.integration.broker.PhotosPreparedForArchiving;
import io.spine.server.integration.broker.PhotosProcessed;
import io.spine.server.integration.broker.PhotosUploaded;
import io.spine.server.integration.broker.TotalPhotosUploadedIncreased;
import io.spine.server.integration.broker.UploadPhotos;
import io.spine.server.transport.memory.InMemoryTransportFactory;
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.spine.server.integration.given.broker.IntegrationBrokerTestEnv.billingBc;
import static io.spine.server.integration.given.broker.IntegrationBrokerTestEnv.photosBc;
import static io.spine.server.integration.given.broker.IntegrationBrokerTestEnv.photosBcAndSubscribedBillingBc;
import static io.spine.server.integration.given.broker.IntegrationBrokerTestEnv.subscribedBillingBc;
import static io.spine.server.integration.given.broker.IntegrationBrokerTestEnv.subscribedPhotosBc;
import static io.spine.server.integration.given.broker.IntegrationBrokerTestEnv.subscribedStatisticsBc;
import static io.spine.server.integration.given.broker.IntegrationBrokerTestEnv.subscribedWarehouseBc;

@DisplayName("`IntegrationBroker` should")
class IntegrationBrokerTest {

    /**
     * The transport through which the contexts of a test exchange their external events.
     */
    private InMemoryTransportFactory transport;

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();
        transport = InMemoryTransportFactory.newInstance();
    }

    @AfterEach
    void closeTransport() {
        transport.close();
    }

    @AfterAll
    static void cleanUpSuite() {
        ModelTests.dropAllModels();
    }

    @Nested
    @DisplayName("dispatch events")
    class DispatchEvents {

        @Nested
        @DisplayName("from a BC to subscribers of external events in")
        class FromOneBc {

            @Test
            @DisplayName("another BC")
            void toAnotherBc() {
                try (var publishingPhotosBc = photosBc(transport);
                     var subscribedBillingBc = subscribedBillingBc(transport)
                ) {
                    publishingPhotosBc.receivesCommand(UploadPhotos.generate());
                    publishingPhotosBc.assertEvent(PhotosUploaded.class);

                    subscribedBillingBc.assertEvent(CreditsHeld.class);
                }
            }

            @Test
            @DisplayName("multiple other BCs")
            void toMultipleOtherBc() {
                try (var publishingPhotosBc = photosBc(transport);
                     var subscribedBillingBc = subscribedBillingBc(transport);
                     var subscribedStatisticsBc = subscribedStatisticsBc(transport)
                ) {
                    publishingPhotosBc.receivesCommand(UploadPhotos.generate());
                    publishingPhotosBc.assertEvent(PhotosUploaded.class);

                    subscribedBillingBc.assertEvent(CreditsHeld.class);
                    subscribedStatisticsBc.assertEvent(TotalPhotosUploadedIncreased.class);
                }
            }

            @Test
            @DisplayName("multiple other BCs with different needs")
            void toMultipleOtherBcWithDifferentNeeds() {
                try (var publishingPhotosBc = photosBc(transport);
                     var subscribedBillingBc = subscribedBillingBc(transport);
                     var subscribedWarehouseBc = subscribedWarehouseBc(transport)
                ) {
                    publishingPhotosBc.receivesCommand(UploadPhotos.generate());
                    publishingPhotosBc.assertEvent(PhotosUploaded.class);
                    subscribedBillingBc.assertEvent(CreditsHeld.class);

                    publishingPhotosBc.receivesCommand(ArchivePhotos.generate());
                    publishingPhotosBc.assertEvent(PhotosPreparedForArchiving.class);
                    subscribedWarehouseBc.assertEvent(PhotosMovedToWarehouse.class);
                }
            }
        }

        @Nested
        @DisplayName("between two BCs when")
        class BetweenTwoBc {

            @Test
            @DisplayName("the subscribed BC is registered before the publishing one")
            void whenSubscribedBcRegisteredBeforePublishing() {
                try (var subscribedBillingBc = subscribedBillingBc(transport);
                     var publishingPhotosBc = photosBc(transport)
                ) {
                    publishingPhotosBc.receivesCommand(UploadPhotos.generate());
                    publishingPhotosBc.assertEvent(PhotosUploaded.class);

                    subscribedBillingBc.assertEvent(CreditsHeld.class);
                }
            }

            @Test
            @DisplayName("they are subscribed to each other")
            void whenSubscribedToEachOther() {
                try (var subscribedPhotosBc = subscribedPhotosBc(transport);
                     var subscribedBillingBc = subscribedBillingBc(transport)
                ) {
                    subscribedPhotosBc.receivesCommand(UploadPhotos.generate());
                    subscribedPhotosBc.assertEvent(PhotosUploaded.class);

                    subscribedBillingBc.assertEvent(CreditsHeld.class);
                    subscribedPhotosBc.assertEvent(PhotosProcessed.class);
                }
            }
        }
    }

    @Nested
    @DisplayName("avoid dispatching events from a BC to subscribers of")
    class AvoidDispatchingEventsToSubscribers {

        @Test
        @DisplayName("internal events in another BC")
        void ofInternalEventsInAnotherBc() {
            try (var projectsBc = photosBc(transport);
                 var billingBc = billingBc(transport)
            ) {
                projectsBc.receivesCommand(UploadPhotos.generate());

                projectsBc.assertEvent(PhotosUploaded.class);
                billingBc.assertEvents()
                         .isEmpty();
            }
        }

        @Test
        @DisplayName("external events in the same BC")
        void ofExternalEventInTheSameBc() {
            try (var photosBcAndBillingBc = photosBcAndSubscribedBillingBc(transport)) {

                photosBcAndBillingBc.receivesCommand(UploadPhotos.generate());
                photosBcAndBillingBc.assertEvent(PhotosUploaded.class);

                photosBcAndBillingBc.assertEvents()
                                    .withType(CreditsHeld.class)
                                    .isEmpty();
            }
        }
    }
}
