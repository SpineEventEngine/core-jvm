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

package io.spine.server.trace;

import io.spine.annotation.SPI;
import io.spine.core.Signal;
import io.spine.server.Closeable;
import io.spine.server.ContextSpec;

/**
 * A factory of {@link Tracer}s of signal messages.
 *
 * <p>Each tracer is created for a single signal and should not be reused.
 *
 * <p>Implementations may choose to perform I/O operations (i.e. send traces via network) on
 * {@code close()}. It is expected in general that each call to {@link #trace} results in some
 * tracing data produced, however, implementations may ignore some signals.
 */
@SPI
public interface TracerFactory extends Closeable {

    /**
     * Creates a new instance of {@link Tracer} for the given signal.
     *
     * <p>The tracer will be closed externally once it is no longer needed.
     *
     * @param context
     *         specification of the Bounded Context for signal of which the tracer is created
     * @param signalMessage
     *         the message to trace
     * @return new {@code Tracer}
     */
    Tracer trace(ContextSpec context, Signal<?, ?, ?> signalMessage);
}
