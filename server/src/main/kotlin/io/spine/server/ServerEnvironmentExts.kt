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

@file:JvmName("ServerEnvironments")

package io.spine.server

/**
 * Creates a [ServerEnvironment] configured by the given block.
 *
 * Example of usage:
 * ```kotlin
 * val environment = serverEnvironment {
 *     under<Production> {
 *         use(productionStorageFactory)
 *         use(memoizingTracerFactory)
 *     }
 *     under<Tests> {
 *         use(testingStorageFactory)
 *     }
 * }
 * ```
 *
 * ### API NOTE
 * This function is the Kotlin counterpart of [ServerEnvironment.newBuilder]. The block, having
 * the builder as its receiver, configures several types of environment without repeating
 * the receiver, and the environment is built when the block ends.
 *
 * @param block The function that configures the technologies of the environment.
 */
public fun serverEnvironment(block: ServerEnvironment.Builder.() -> Unit): ServerEnvironment =
    ServerEnvironment.newBuilder()
        .apply(block)
        .build()
