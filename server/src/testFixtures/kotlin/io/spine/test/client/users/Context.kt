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

@file:JvmName("Context")

package io.spine.test.client.users

import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder

const val NAME = "Test Users"

/**
 * Creates the builder of the Users context.
 *
 * @param multiTenant Tells if the context is multi-tenant.
 */
@JvmOverloads
fun builder(multiTenant: Boolean = false): BoundedContextBuilder =
    (if (multiTenant) BoundedContext.multiTenant(NAME) else BoundedContext.singleTenant(NAME))
        .add(UserAccountAggregate::class.java)
        .add(LoginProcess::class.java)
        .add(ActiveUsersProjection.Repository())
        .addEventDispatcher(PasswordChangeReaction())
