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

@file:JvmName("TopicTargets")

package io.spine.server.stand

import io.spine.client.Topic

/**
 * Ensures that this topic has a target with the given index.
 *
 * @param index The index of a target of the topic.
 * @throws IllegalArgumentException If the topic has no target with the index.
 */
internal fun Topic.checkHasTarget(index: Int) {
    require(index in 0..<targetCount) {
        "The topic has no target with the index $index."
    }
}
