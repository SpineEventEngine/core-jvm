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

/**
 * This package defines the Spine Trace API.
 *
 * <p>The API allows users integrate with various tracing systems in order to monitor the message
 * flow in their systems.
 *
 * <p>The API is invoked via system events which occur when a message is processed by an entity.
 *
 * <p>Currently, Trace API does not support monitoring standalone commanders, reactors,
 * and subscribers. It is only applicable to entities, such as
 * {@link io.spine.server.aggregate.Aggregate Aggregates},
 * {@link io.spine.server.procman.ProcessManager ProcessManagers},
 * and {@link io.spine.server.projection.Projection Projections}.
 *
 * <p>The entry point to integrating a third-party tracing system with a Spine-based application is
 * {@link io.spine.server.trace.TracerFactory}. A single {@code TracerFactory} can be used in one or
 * more bounded contexts.
 *
 * @see io.spine.server.trace.TracerFactory
 * @see io.spine.server.trace.Tracer
 * @see io.spine.server.ServerEnvironment#tracing()
 */

@CheckReturnValue
@NullMarked
package io.spine.server.trace;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
