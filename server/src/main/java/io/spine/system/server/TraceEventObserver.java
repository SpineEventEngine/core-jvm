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

package io.spine.system.server;

import io.spine.annotation.Internal;
import io.spine.core.Subscribe;
import io.spine.server.ContextSpec;
import io.spine.server.event.AbstractEventSubscriber;
import io.spine.server.trace.TracerFactory;
import io.spine.system.server.event.CommandDispatchedToHandler;
import io.spine.system.server.event.EventDispatchedToReactor;
import io.spine.system.server.event.EventDispatchedToSubscriber;
import io.spine.system.server.event.EventImported;
import io.spine.system.server.event.SignalDispatchedMixin;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * An event subscriber which listens to {@linkplain SignalDispatchedMixin dispatching events}.
 */
@Internal
public final class TraceEventObserver extends AbstractEventSubscriber {

    private final ContextSpec context;
    private final TracerFactory tracing;

    public TraceEventObserver(ContextSpec context, TracerFactory tracing) {
        super();
        this.context = checkNotNull(context);
        this.tracing = checkNotNull(tracing);
    }

    @Subscribe
    void on(EventDispatchedToSubscriber event) {
        trace(event);
    }

    @Subscribe
    void on(EventDispatchedToReactor event) {
        trace(event);
    }

    @Subscribe
    void on(EventImported event) {
        trace(event);
    }

    @Subscribe
    void on(CommandDispatchedToHandler event) {
        trace(event);
    }

    private void trace(SignalDispatchedMixin<?> event) {
        var payload = event.getPayload();
        try (var tracer = tracing.trace(context, payload)) {
            tracer.processedBy(event.getReceiver(), event.getEntityType());
        } catch (Exception e) {
            logger().atError().withCause(e).log(() -> format(
                    "Error during trace construction on the event with type `%s`.",
                    payload.enclosedTypeUrl()));
        }
    }
}
