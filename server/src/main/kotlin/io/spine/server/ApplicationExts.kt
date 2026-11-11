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

@file:JvmName("Applications")

package io.spine.server

/**
 * Creates an [Application] configured by the given block.
 *
 * Example of usage:
 * ```kotlin
 * val application = application {
 *     serverEnvironment = environment
 *     add(singleTenantContext("Users") { add(UserRepository()) })
 *     add(singleTenantContext("Tasks") { add(TaskRepository()) })
 * }
 * ```
 *
 * @param block The function that configures the application.
 * @see Application.Builder
 */
public fun application(block: Application.Builder.() -> Unit): Application =
    Application.newBuilder()
        .apply(block)
        .build()
