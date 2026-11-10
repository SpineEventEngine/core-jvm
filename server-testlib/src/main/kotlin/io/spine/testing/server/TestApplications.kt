/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

@file:JvmName("TestApplications")

package io.spine.testing.server

import io.spine.server.Application
import io.spine.server.serverEnvironment

/**
 * Creates an [Application] for a test, configured by the given block.
 *
 * The server environment of the application is a new one with the technologies of
 * the [Tests][io.spine.environment.Tests] environment: in-memory storage and transport,
 * and a local `Delivery`. The block may set another environment.
 *
 * ```kotlin
 * val application = testApplication {
 *     add(BoundedContext.singleTenant("Users").add(UserRepository()))
 *     add(BoundedContext.singleTenant("Tasks").add(TaskRepository()))
 * }
 * ```
 *
 * The test closes the application, together with its contexts and its environment.
 *
 * @param block The function that adds the contexts of the application.
 * @see newTestApplicationBuilder
 */
public fun testApplication(block: Application.Builder.() -> Unit): Application =
    newTestApplicationBuilder()
        .apply(block)
        .build()

/**
 * Creates a builder of an [Application] for a test.
 *
 * The builder has a new server environment with the technologies of
 * the [Tests][io.spine.environment.Tests] environment. Java code adds the contexts
 * and builds the application:
 *
 * ```java
 * var application = TestApplications.newBuilder()
 *         .add(BoundedContext.singleTenant("Users").add(new UserRepository()))
 *         .build();
 * ```
 *
 * @see testApplication
 */
@JvmName("newBuilder")
public fun newTestApplicationBuilder(): Application.Builder =
    Application.newBuilder()
        .setServerEnvironment(serverEnvironment {})
