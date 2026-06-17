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

package io.spine.server.commandbus;

import io.grpc.stub.StreamObserver;
import io.spine.core.Ack;
import io.spine.server.event.EventBus;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.core.Status.StatusCase.REJECTION;

/**
 * An observer that publishes rejections on {@linkplain Ack acknowledgement} to
 * an {@link EventBus}.
 *
 * <p>The {@linkplain io.spine.core.Event rejection events} passed to the {@code Ack} instances are
 * by default generated in-place and thus not "known" to the system.
 *
 * <p>The {@code AckRejectionPublisher} ensures all types that {@linkplain io.spine.core.Subscribe
 * subscribe} or {@linkplain io.spine.server.event.React react} to such rejection types are
 * notified when a rejection occurs.
 */
final class AckRejectionPublisher implements StreamObserver<Ack> {

    private final EventBus eventBus;

    AckRejectionPublisher(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void onNext(Ack value) {
        checkNotNull(value);
        var status = value.getStatus();
        if (status.getStatusCase() == REJECTION) {
            eventBus.post(status.getRejection());
        }
    }

    @Override
    public void onError(Throwable t) {
        throw new IllegalStateException(t);
    }

    @Override
    public void onCompleted() {
        // NO-OP.
    }
}
