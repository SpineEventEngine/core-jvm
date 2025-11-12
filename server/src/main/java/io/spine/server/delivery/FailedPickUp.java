/*
 * Copyright 2023, TeamDev. All rights reserved.
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

package io.spine.server.delivery;

import io.spine.annotation.Internal;
import io.spine.annotation.SPI;

import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An abstract base for shard pick-up-related failures.
 *
 * <p>Although not marked {@code Internal} explicitly, this type <em>is</em> internal to framework
 * in terms of extensibility. End-users should not extend it directly.
 * Its descendants are provided by the framework itself, and represent different
 * pick-up failure scenarios which may happen in {@code Delivery} at run-time.
 *
 * <p>On the other hand, nested {@link Action} is designed to be implemented by the framework
 * users to provide a custom behaviour for pickup failures in the {@linkplain DeliveryMonitor}
 * in addition to predefined behaviours provided by the inheritors of this class.
 */
@SuppressWarnings("AbstractClassWithoutAbstractMethods" /* To emphasize the design intention. */)
public abstract class FailedPickUp {

    private final ShardIndex shard;

    private final Supplier<Optional<DeliveryStats>> retryDelivery;

    /**
     * Creates a new {@code FailedPickUp} with the given {@code shard}
     * and {@code retryDelivery} action.
     */
    FailedPickUp(ShardIndex shard, RetryDelivery retryDelivery) {
        checkNotNull(shard);
        checkNotNull(retryDelivery);
        this.retryDelivery = retryDelivery;
        this.shard = shard;
    }

    /**
     * Returns an {@code Action} that will retry the delivery from the shard.
     */
    public final Action retry() {
        return retryDelivery::get;
    }

    /**
     * Returns an action that makes the delivery process
     * to finish without any processing done.
     */
    public Action doNothing() {
        return Optional::empty;
    }

    /**
     * Returns an index of the shard which failed to pick up.
     */
    public final ShardIndex shard() {
        return shard;
    }

    /**
     * Action to take in relation to failed shard pick-up.
     *
     * <p>May be used to {@linkplain Delivery#deliverMessagesFrom(ShardIndex)
     * retry the delivery run}, and return its results. To initiate such a retry attempt,
     * one should return {@linkplain FailedPickUp#retry() FailedPickUp.retry()} action.
     *
     * <p>Another option provided is to just {@linkplain FailedPickUp#doNothing()
     * ignore the failure}, returning which leads to the completion
     * of the delivery process with no job done.
     *
     * <p>See the descendants of {@code FailedPickUp} for other actions
     * provided out-of-the-box by the framework.
     */
    @SPI
    @FunctionalInterface
    public interface Action {

        /**
         * Executes the {@code Action}.
         *
         * @return the results of a
         *         {@linkplain Delivery#deliverMessagesFrom(ShardIndex) delivery run}, should
         *         end-users choose to repeat it,
         *         or {@code Optional.empty()} if no re-run is performed
         */
        Optional<DeliveryStats> execute();
    }

    /**
     * A callback window into {@code Delivery} process, providing a way to retry
     * the delivery process with the same inputs, as was the failed attempt
     * described by {@link FailedPickUp}.
     *
     * <p>This interface is designed to be used internally by {@code Delivery}
     * in order to power up the actions returned by end-users via {@code DeliveryMonitor}.
     */
    @Internal
    @FunctionalInterface
    interface RetryDelivery extends Supplier<Optional<DeliveryStats>> {

    }
}
