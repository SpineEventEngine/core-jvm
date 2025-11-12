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
import io.spine.logging.WithLogging;
import io.spine.server.integration.ExternalMessage;
import io.spine.server.transport.ChannelId;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static java.lang.String.format;

/**
 * The in-memory subscriber, which uses single-thread delivery of messages.
 *
 * <p>The messages posted by any number of concurrent publishers are delivered to the observers
 * of this subscriber one-by-one in a single thread.
 *
 * <p>This implementation should not be used in production environments, as it is not designed
 * to operate with external transport.
 */
final class SingleThreadInMemSubscriber extends InMemorySubscriber implements WithLogging {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    SingleThreadInMemSubscriber(ChannelId id) {
        super(id);
    }

    @SuppressWarnings("FutureReturnValueIgnored") // Error handling is done manually.
    @Override
    public void onMessage(ExternalMessage message) {
        CompletableFuture.runAsync(() -> callObservers(message), executor)
                         .exceptionally(throwable -> logError(throwable, message));
    }

    /**
     * Conveniently logs an error in {@link CompletableFuture#exceptionally(Function)}.
     *
     * @return {@code null} always
     */
    private @Nullable Void logError(Throwable throwable, ExternalMessage message) {
        logger().atError().withCause(throwable).log(() -> format(
                "Error dispatching an external message `%s` with ID `%s`.",
                message.getOriginalMessage().getTypeUrl(),
                Identifier.unpack(message.getId())));
        return null;
    }

    @Override
    public void close() {
        super.close();
        executor.shutdown();
    }
}
