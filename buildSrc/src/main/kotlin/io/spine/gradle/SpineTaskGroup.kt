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

package io.spine.gradle

/**
 * The Gradle task group used by every custom task registered or
 * configured by Spine SDK code.
 *
 * Setting `group = SpineTaskGroup.name` on every Spine-specific task
 * keeps them listed together under `spine` in `./gradlew tasks` and
 * in the IntelliJ IDEA Gradle tool window. See
 * `.agents/skills/gradle-review/spine-task-conventions.md` in the
 * `config` repository for the full convention and rationale.
 *
 * Example:
 * ```
 * tasks.register("generateSpineModel") {
 *     group = SpineTaskGroup.name
 *     description = "Generates Spine model classes from .proto definitions"
 * }
 * ```
 */
object SpineTaskGroup {
    const val name = "spine"
}
