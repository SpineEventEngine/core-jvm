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
 * This package provides classes and interfaces for performing client requests like
 * posting commands, running queries, or creating subscriptions.
 *
 * <p>The term <em>actor</em> used in this API means the user on behalf of whom requests
 * are created and executed. The package provides two levels of API.
 *
 * <dl>
 *   <dt><strong>High-level {@link io.spine.client.Client} API</strong></dt>
 *   <dd>is meant for client-side Java applications that would communicate with backend
 *   services via a gRPC connection. An instance of the {@link io.spine.client.Client} class
 *   establishes this connection and serves as a fluent API gateway for composing and posting
 *   requests.
 *   </dd>
 *
 *   <dt><strong>Low-level {@link io.spine.client.ActorRequestFactory} API</strong></dt>
 *   <dd>is meant for server-side code which needs to speak to backend services without involving
 *   gRPC. This API is also by the High-level API implementation.</dd>
 * </dl>
 *
 * <p>The {@link io.spine.client.Filters} utility class provides methods for composing filtering
 * conditions for both levels of the API.
 *
 * <p>When subscribing the Client API accepts an implementation of a functional interface
 * (see {@link io.spine.client.StateConsumer} and {@link io.spine.client.EventConsumer}).
 * Errors occurred when streaming or consuming messages are handled via
 * {@link io.spine.client.ErrorHandler} and {@link io.spine.client.ConsumerErrorHandler}
 * correspondingly.
 */
@CheckReturnValue
@NullMarked
package io.spine.client;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
