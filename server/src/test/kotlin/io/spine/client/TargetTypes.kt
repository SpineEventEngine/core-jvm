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

@file:JvmName("TargetTypes")

package io.spine.client

import com.google.protobuf.Message
import io.spine.type.TypeUrl

/**
 * Obtains the type URLs of the targets of this subscription, in the order of the targets.
 */
internal fun Subscription.typesOfTargets(): List<String> = topic.targetList.map { it.type }

/**
 * Obtains the type URLs of the given classes, in their order.
 */
internal fun typeUrls(vararg types: Class<out Message>): List<String> =
    types.map { TypeUrl.of(it).value() }
