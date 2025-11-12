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

package io.spine.client;

import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;

import java.util.Optional;

import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.util.Exceptions.unsupported;

/**
 * A {@link StreamObserver} of {@link SubscriptionUpdate} messages translating the message
 * payload to the given delegate {@code StreamObserver}.
 *
 * <p>The errors and completion acknowledgements are translated directly to the delegate.
 *
 * <p>The {@linkplain SubscriptionUpdate#getEntityUpdates() messages} are unpacked
 * and sent to the delegate observer one by one.
 *
 * @param <M>
 *         the type of the delegate-observer's messages, which can either be
 *         an unpacked entity state or an {@code Event}
 */
final class SubscriptionObserver<M extends Message>
        implements StreamObserver<SubscriptionUpdate> {

    /**
     * Delegate which would receive the unpacked domain-specific messages, such as
     * entity states or {@code Event}s.
     */
    private final StreamObserver<M> delegate;


    /**
     * Optional chained observer of raw {@code SubscriptionUpdate}s,
     * which would receive its input within the same subscription.
     *
     * <p>Such an observer may be handy for descendants which need more details
     * than just an "unpacked" domain message.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType"
            /* Could have been `@Nullable`,
            but it is always used as `Optional`;
            so having `Optional` field is an optimization. */)
    private final Optional<StreamObserver<SubscriptionUpdate>> chain;

    /**
     * Creates a new instance of {@code SubscriptionObserver}.
     *
     * <p>Specifies no chained observer.
     *
     * @param targetObserver a delegate consuming the {@code Entity} state, or an {@code Event}
     */
    SubscriptionObserver(StreamObserver<M> targetObserver) {
        this.delegate = targetObserver;
        this.chain = Optional.empty();
    }

    /**
     * Creates a new instance of {@code SubscriptionObserver}.
     *
     * <p>Use this constructor in favour of
     * {@link SubscriptionObserver#SubscriptionObserver(StreamObserver)
     * SubscriptionObserver(StreamObserver)} to additionally set the observer chain.
     *
     * @param targetObserver
     *         a delegate consuming the {@code Entity} state, or an {@code Event}
     * @param chain
     *         chained observer consuming {@code SubscriptionUpdate} obtained within
     *         the same subscription
     */
    SubscriptionObserver(StreamObserver<M> targetObserver,
                         StreamObserver<SubscriptionUpdate> chain) {
        this.delegate = targetObserver;
        this.chain = Optional.of(chain);
    }

    @SuppressWarnings("unchecked") // Logically correct.
    @Override
    public void onNext(SubscriptionUpdate value) {
        var updateCase = value.getUpdateCase();
        switch (updateCase) {
            case ENTITY_UPDATES:
                value.getEntityUpdates()
                     .getUpdateList()
                     .stream()
                     .filter(u -> u.getKindCase() == EntityStateUpdate.KindCase.STATE)
                     .map(EntityStateUpdate::getState)
                     .map(any -> (M) unpack(any))
                     .forEach(delegate::onNext);
                break;
            case EVENT_UPDATES:
                value.getEventUpdates()
                     .getEventList()
                     .stream()
                     .map(e -> (M) e)
                     .forEach(delegate::onNext);
                break;
            case UPDATE_NOT_SET:
            default:
                throw unsupported("Unsupported update case `%s`.", updateCase);
        }
        chain.ifPresent(observer -> observer.onNext(value));
    }

    @Override
    public void onError(Throwable t) {
        delegate.onError(t);
    }

    @Override
    public void onCompleted() {
        delegate.onCompleted();
    }
}
