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

@file:JvmName("TestContexts")

package io.spine.testing.server

import io.spine.server.Application
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.ServerEnvironment
import io.spine.server.application
import io.spine.server.serverEnvironment

/**
 * Builds the Bounded Context of this builder for a test.
 *
 * The context is the only context of a new [Application], whose technologies are those of
 * the given environment. By default, the environment is a new one with the technologies
 * of the [Tests][io.spine.environment.Tests] environment: in-memory storage and transport,
 * and a local `Delivery`.
 *
 * Closing the context does not close the application or its environment. A test that needs
 * them closed builds the [Application] itself.
 *
 * In Kotlin:
 * ```
 * val context = BoundedContext.singleTenant("Tasks")
 *     .add(TaskRepository())
 *     .buildForTests()
 * ```
 * In Java:
 * ```
 * var context = TestContexts.buildForTests(
 *         BoundedContext.singleTenant("Tasks").add(new TaskRepository())
 * );
 * ```
 *
 * @param environment The environment that provides the technologies to the context.
 * @return The built context.
 */
@JvmOverloads
public fun BoundedContextBuilder.buildForTests(
    environment: ServerEnvironment = serverEnvironment {}
): BoundedContext = applicationForTests(environment).contexts()
    .single()

/**
 * Creates a new [Application] with the Bounded Context of this builder as its only context.
 *
 * Unlike [buildForTests], gives the application, which the test closes together with
 * its context and its environment.
 *
 * @param environment The environment that provides the technologies to the context.
 * @return The built application.
 */
@JvmOverloads
public fun BoundedContextBuilder.applicationForTests(
    environment: ServerEnvironment = serverEnvironment {}
): Application = application {
    serverEnvironment = environment
    add(this@applicationForTests)
}
