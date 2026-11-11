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

@file:JvmName("BoundedContexts")

package io.spine.server

/**
 * Creates a builder of a single-tenant Bounded Context with the given name,
 * configured by the given block.
 *
 * Add the returned builder to an [Application], which builds the context:
 * ```kotlin
 * val application = application {
 *     serverEnvironment = environment
 *     add(singleTenantContext("Tasks") {
 *         add(TaskRepository())
 *         add(TaskViewRepository())
 *     })
 * }
 * ```
 *
 * @param name The name of the Bounded Context.
 * @param block The function that configures the builder.
 * @see multiTenantContext
 * @see BoundedContext.singleTenant
 */
public fun singleTenantContext(
    name: String,
    block: (@ServerDsl BoundedContextBuilder).() -> Unit
): BoundedContextBuilder =
    BoundedContext.singleTenant(name)
        .apply(block)

/**
 * Creates a builder of a multitenant Bounded Context with the given name,
 * configured by the given block.
 *
 * Add the returned builder to an [Application], which builds the context:
 * ```kotlin
 * val application = application {
 *     serverEnvironment = environment
 *     setTenantIndex(tenantIndex)
 *     add(multiTenantContext("Tasks") {
 *         add(TaskRepository())
 *         add(TaskViewRepository())
 *     })
 * }
 * ```
 *
 * @param name The name of the Bounded Context.
 * @param block The function that configures the builder.
 * @see singleTenantContext
 * @see BoundedContext.multitenant
 */
public fun multiTenantContext(
    name: String,
    block: (@ServerDsl BoundedContextBuilder).() -> Unit
): BoundedContextBuilder =
    BoundedContext.multitenant(name)
        .apply(block)
