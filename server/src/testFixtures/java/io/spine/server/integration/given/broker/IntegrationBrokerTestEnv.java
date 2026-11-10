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

package io.spine.server.integration.given.broker;

import io.spine.environment.Tests;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.ServerEnvironment;
import io.spine.server.transport.TransportFactory;
import io.spine.testing.server.blackbox.BlackBox;

import static io.spine.testing.server.TestContexts.buildForTests;

/**
 * Test environment for {@link io.spine.server.integration.IntegrationBrokerTest}.
 */
public class IntegrationBrokerTestEnv {

    private static final String testClassName = IntegrationBrokerTestEnv.class.getSimpleName();

    private IntegrationBrokerTestEnv() {
    }

    /**
     * Creates a black box over the context of the given builder, which exchanges messages
     * with other contexts through the given transport.
     *
     * <p>Each context is the only context of an application of its own. The contexts of
     * a test share the transport, so that they receive the external events of each other.
     * Closing the black box leaves the transport open.
     */
    private static BlackBox blackBox(TransportFactory transport, BoundedContextBuilder builder) {
        var environment = ServerEnvironment.newBuilder()
                .under(Tests.class, env -> env.use(transport))
                .build();
        return BlackBox.from(buildForTests(builder, environment));
    }

    public static BlackBox billingBc(TransportFactory transport) {
        return blackBox(transport,
                BoundedContext.singleTenant("BillingBc-" + testClassName)
                              .add(BillingAggregate.class)
        );
    }

    public static BlackBox subscribedBillingBc(TransportFactory transport) {
        return blackBox(transport,
                BoundedContext.singleTenant("SubscribedBillingBc-" + testClassName)
                              .add(SubscribedBillingAggregate.class)
        );
    }

    public static BlackBox photosBc(TransportFactory transport) {
        return blackBox(transport,
                BoundedContext.singleTenant("PhotosBc-" + testClassName)
                              .add(PhotosAggregate.class)
        );
    }

    public static BlackBox subscribedPhotosBc(TransportFactory transport) {
        return blackBox(transport,
                BoundedContext.singleTenant("SubscribedPhotosBc-" + testClassName)
                              .add(SubscribedPhotosAggregate.class)
        );
    }

    public static BlackBox subscribedWarehouseBc(TransportFactory transport) {
        return blackBox(transport,
                BoundedContext.singleTenant("SubscribedWarehouseBc-" + testClassName)
                              .add(SubscribedWarehouseAggregate.class)
        );
    }

    public static BlackBox subscribedStatisticsBc(TransportFactory transport) {
        return blackBox(transport,
                BoundedContext.singleTenant("SubscribedStatisticsBc-" + testClassName)
                              .add(SubscribedStatisticsAggregate.class)
        );
    }

    public static BlackBox photosBcAndSubscribedBillingBc(TransportFactory transport) {
        return blackBox(transport,
                BoundedContext.singleTenant("photosBcAndSubscribedBillingBc-" + testClassName)
                              .add(PhotosAggregate.class)
                              .add(SubscribedBillingAggregate.class)
        );
    }
}
