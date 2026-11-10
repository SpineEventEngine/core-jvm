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

package io.spine.server.delivery.given;

import com.google.common.collect.ImmutableList;
import io.spine.environment.Tests;
import io.spine.server.DefaultRepository;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.DeliveryMonitor;
import io.spine.server.delivery.FailedReception;
import io.spine.server.delivery.InboxContents;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.ShardObserver;
import io.spine.server.tenant.TenantAwareRunner;
import io.spine.test.delivery.Receptionist;
import io.spine.test.delivery.command.TurnConditionerOn;
import io.spine.testing.server.blackbox.BlackBox;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;

/**
 * Test environment for {@link io.spine.server.delivery.ReceptionFailureTest}.
 */
public final class ReceptionFailureTestEnv {

    private ReceptionFailureTestEnv() {
    }

    public static BlackBox blackBox() {
        var repository = DefaultRepository.of(ReceptionistAggregate.class);
        var context = BlackBox.singleTenantWith(repository);
        return context;
    }

    public static void configureDelivery(DeliveryMonitor monitor) {
        var environment = ServerEnvironment.instance();
        var delivery = Delivery.newBuilder(environment.nodeId(), environment.storageFactory())
                .setMonitor(monitor)
                .build();
        delivery.subscribe(new IgnoringObserver());
        ServerEnvironment.under(Tests.class)
                         .use(delivery);
    }

    public static void sleep() {
        sleepUninterruptibly(Duration.ofMillis(900));
    }

    public static Receptionist receptionist(String receptionistId, int cmdsHandled) {
        return Receptionist.newBuilder()
                .setId(receptionistId)
                .setHowManyCmdsHandled(cmdsHandled)
                .build();
    }

    public static TurnConditionerOn tellToTurnConditioner(String receptionistId) {
        var command = TurnConditionerOn
                .newBuilder()
                .setReceptionistId(receptionistId)
                .build();
        return command;
    }

    /**
     * Fetches the contents of a single shard.
     *
     * <p>In case there are many shards configured, this method
     * throws an {@code IllegalStateException}.
     */
    public static ImmutableList<InboxMessage> inboxMessages() {
        var contents = InboxContents.get();
        checkState(contents.size() == 1);
        var messages = contents.values()
                               .iterator()
                               .next();
        return messages;
    }

    /**
     * A shard observer that deliberately ignores any exceptions thrown when dispatching
     * inbox messages.
     */
    private static final class IgnoringObserver implements ShardObserver {

        @Override
        public void onMessage(InboxMessage message) {
            new Thread(() -> runDelivery(message)).start();
        }

        @SuppressWarnings("resource")
        private static void runDelivery(InboxMessage message) {
            var tenant = message.tenant();
            var index = message.shardIndex();
            var delivery = ServerEnvironment.instance()
                                            .delivery();
            try {
                TenantAwareRunner.with(tenant)
                                 .run(() -> delivery.deliverMessagesFrom(index));
            } catch (Exception ignored) {
                // Do nothing.
            }
        }
    }

    /**
     * A delivery monitor that remembers the last observed reception failure.
     *
     * <p>Allows to specify a {@link FailureResolver} and act upon the observed failure.
     */
    public static final class ObservingMonitor extends DeliveryMonitor {

        private @Nullable FailedReception lastFailure = null;
        private @Nullable FailureResolver resolver = null;

        @Override
        public FailedReception.Action onReceptionFailure(FailedReception reception) {
            lastFailure = reception;
            if (resolver != null) {
                return resolver.onFailure(reception);
            }
            return super.onReceptionFailure(reception);
        }

        public Optional<FailedReception> lastFailure() {
            return Optional.ofNullable(lastFailure);
        }

        /**
         * Specifies the resolver for this monitor.
         */
        public void setResolver(FailureResolver resolver) {
            this.resolver = checkNotNull(resolver);
        }
    }

    /**
     * Allows to act upon an observed {@link FailedReception}.
     */
    @FunctionalInterface
    public interface FailureResolver {

        /**
         * Returns an action in response to a failed reception.
         */
        FailedReception.Action onFailure(FailedReception reception);
    }

    /**
     * A delivery monitor that marks the message causing a reception failure
     * as {@linkplain io.spine.server.delivery.InboxMessageStatus#DELIVERED delivered}.
     */
    public static final class MarkFailureDeliveredMonitor extends DeliveryMonitor {

        private boolean failureReceived = false;

        /**
         * In case the reception of the {@code InboxMessage} failed,
         * mark it as delivered anyway.
         */
        @Override
        public FailedReception.Action onReceptionFailure(FailedReception reception) {
            this.failureReceived = true;
            return reception.markDelivered();
        }

        public boolean failureReceived() {
            return failureReceived;
        }
    }
}
