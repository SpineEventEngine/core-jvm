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

package io.spine.client;

import io.grpc.stub.StreamObserver;

import static io.spine.client.EntityStateUpdate.KindCase.NO_LONGER_MATCHING;

/**
 * An observer taking {@code SubscriptionUpdate} and passing it to the specified
 * {@code NoLongerMatchingConsumer} in case the {@code SubscriptionUpdate} tells
 * that some entity stopped matching the subscription criteria.
 *
 * <p>If {@code SubscriptionUpdate} does not correspond to "no longer matching" scenario,
 * does nothing.
 */
final class NoLongerMatchingFilter implements StreamObserver<SubscriptionUpdate> {

    private final NoLongerMatchingConsumer<?> consumer;

    NoLongerMatchingFilter(NoLongerMatchingConsumer<?> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void onNext(SubscriptionUpdate value) {
        if (value.hasEntityUpdates()) {
            var updates = value.getEntityUpdates()
                               .getUpdateList();
            var stream = updates.stream();
            stream.filter(NoLongerMatchingFilter::isNoLongerMatching)
                  .map(EntityStateUpdate::getId)
                  .forEach(consumer);
        }
    }

    private static boolean isNoLongerMatching(EntityStateUpdate update) {
        return NO_LONGER_MATCHING == update.getKindCase();
    }

    @Override
    public void onError(Throwable t) {
        // Do nothing.
    }

    @Override
    public void onCompleted() {
        // Do nothing.
    }
}
