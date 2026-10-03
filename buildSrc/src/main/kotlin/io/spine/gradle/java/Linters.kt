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

package io.spine.gradle.java

import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.named

/**
 * Disables Java linters in this [Project].
 *
 * In particular, the following linters will be disabled:
 *
 * 1. CheckStyle.
 * 2. PMD.
 * 3. ErrorProne.
 *
 * Apply this configuration for modules that have original Flogger sources,
 * which have not been migrated to Kotlin yet. They produce a lot of
 * errors/warnings failing the build.
 *
 * Our own sources are mostly in Kotlin (as for `spine-logging` repo),
 * so this action seems quite safe.
 */
// TODO:2023-09-22:yevhenii.nadtochii: Remove this piece of configuration.
// See issue: https://github.com/SpineEventEngine/logging/issues/56
fun Project.disableLinters() {
    tasks {
        named("checkstyleMain") { enabled = false }
        named("pmdMain") { enabled = false }
        named<JavaCompile>("compileJava") {
            options.errorprone.enabled.set(false)
        }
    }
}
