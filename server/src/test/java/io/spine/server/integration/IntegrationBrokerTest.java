/*
 * Copyright 2022, TeamDev. All rights reserved.
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

package io.spine.server.integration;

import io.spine.server.ServerEnvironment;
import io.spine.server.integration.broker.ArchivePhotos;
import io.spine.server.integration.broker.CreditsHeld;
import io.spine.server.integration.broker.PhotosMovedToWarehouse;
import io.spine.server.integration.broker.PhotosPreparedForArchiving;
import io.spine.server.integration.broker.PhotosProcessed;
import io.spine.server.integration.broker.PhotosUploaded;
import io.spine.server.integration.broker.TotalPhotosUploadedIncreased;
import io.spine.server.integration.broker.UploadPhotos;
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.AfterAll;
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

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();
        ServerEnvironment.instance()
                         .reset();
    }

    @AfterAll
    static void cleanUpSuite() {
        ModelTests.dropAllModels();
        ServerEnvironment.instance()
                         .reset();
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
                try (var publishingPhotosBc = photosBc();
                     var subscribedBillingBc = subscribedBillingBc()
                ) {
                    publishingPhotosBc.receivesCommand(UploadPhotos.generate());
                    publishingPhotosBc.assertEvent(PhotosUploaded.class);

                    subscribedBillingBc.assertEvent(CreditsHeld.class);
                }
            }

            @Test
            @DisplayName("multiple other BCs")
            void toMultipleOtherBc() {
                try (var publishingPhotosBc = photosBc();
                     var subscribedBillingBc = subscribedBillingBc();
                     var subscribedStatisticsBc = subscribedStatisticsBc()
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
                try (var publishingPhotosBc = photosBc();
                     var subscribedBillingBc = subscribedBillingBc();
                     var subscribedWarehouseBc = subscribedWarehouseBc()
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
                try (var subscribedBillingBc = subscribedBillingBc();
                     var publishingPhotosBc = photosBc()
                ) {
                    publishingPhotosBc.receivesCommand(UploadPhotos.generate());
                    publishingPhotosBc.assertEvent(PhotosUploaded.class);

                    subscribedBillingBc.assertEvent(CreditsHeld.class);
                }
            }

            @Test
            @DisplayName("they are subscribed to each other")
            void whenSubscribedToEachOther() {
                try (var subscribedPhotosBc = subscribedPhotosBc();
                     var subscribedBillingBc = subscribedBillingBc()
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
            try (var projectsBc = photosBc();
                 var billingBc = billingBc()
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
            try (var photosBcAndBillingBc = photosBcAndSubscribedBillingBc()) {

                photosBcAndBillingBc.receivesCommand(UploadPhotos.generate());
                photosBcAndBillingBc.assertEvent(PhotosUploaded.class);

                photosBcAndBillingBc.assertEvents()
                                    .withType(CreditsHeld.class)
                                    .isEmpty();
            }
        }
    }
}
