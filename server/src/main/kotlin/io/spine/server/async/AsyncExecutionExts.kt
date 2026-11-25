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

@file:JvmName("AsyncExecutions")

package io.spine.server.async

/**
 * Creates the [AsyncExecution] that runs the invocations on its own threads and limits
 * the number of ongoing invocations per handler class, configured by the given block.
 *
 * Example of usage:
 * ```kotlin
 * val execution = asyncExecution {
 *     limit<AcmeRpcHandler>(2)
 *     limit = 16
 *     closeTimeout = Durations.fromSeconds(10)
 * }
 * ```
 *
 * Here at most two invocations of `AcmeRpcHandler` are ongoing at once, and at most 16 of
 * any other handler class. When the execution is closed, it waits for its invocations
 * for 10 seconds at most. What the block does not set keeps its default value.
 *
 * ### API NOTE
 * This function is the Kotlin counterpart of [AsyncExecution.newBuilder]. The block has
 * the builder as its receiver, and the execution is built when the block ends.
 *
 * @param block The function that configures the execution.
 * @see AsyncExecution.Builder
 */
public fun asyncExecution(block: AsyncExecution.Builder.() -> Unit): AsyncExecution =
    AsyncExecution.newBuilder()
        .apply(block)
        .build()
