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

package io.spine.test.client.board

import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder

const val NAME = "Test Board"

/**
 * Creates the builder of the Board context, whose cards have columns to filter
 * client subscriptions by.
 */
fun builder(): BoundedContextBuilder =
    BoundedContext.singleTenant(NAME)
        .add(CardAggregate::class.java)
