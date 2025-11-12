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

package io.spine.server.transport.memory;

import io.spine.base.Identifier;
import io.spine.core.BoundedContextNames;
import io.spine.grpc.MemoizingObserver;
import io.spine.protobuf.AnyPacker;
import io.spine.server.integration.ExternalMessage;
import io.spine.server.transport.ChannelId;
import io.spine.server.transport.memory.given.ThrowingObserver;
import io.spine.server.type.given.GivenEvent;
import io.spine.testing.logging.mute.MuteLogging;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;

@DisplayName("`SingleThreadInMemSubscriber` should")
class SingleThreadInMemSubscriberTest {

    /**
     * Wait time to make sure the observer is called.
     */
    private static final int SYNC_TIMEOUT_MILLS = 500;

    @Test
    @MuteLogging
    @DisplayName("not halt after observer throws an error")
    void recoverFromObserverError() throws InterruptedException {
        var subscriber = new SingleThreadInMemSubscriber(ChannelId.getDefaultInstance());

        var throwingObserver = new ThrowingObserver();
        subscriber.addObserver(throwingObserver);

        var id = Identifier.pack(newUuid());
        var externalMessage = ExternalMessage.newBuilder()
                .setId(id)
                .setOriginalMessage(AnyPacker.pack(GivenEvent.arbitrary()))
                .setBoundedContextName(BoundedContextNames.newName("Orders"))
                .build();
        subscriber.onMessage(externalMessage);

        waitForMessage();

        assertThat(throwingObserver.onNextCalled())
                .isTrue();

        subscriber.removeObserver(throwingObserver);

        var memoizingObserver = new MemoizingObserver<ExternalMessage>();
        subscriber.addObserver(memoizingObserver);
        subscriber.onMessage(externalMessage);

        waitForMessage();

        assertThat(memoizingObserver.firstResponse())
                .isEqualTo(externalMessage);
    }

    private static void waitForMessage() throws InterruptedException {
        Thread.sleep(SYNC_TIMEOUT_MILLS);
    }
}
