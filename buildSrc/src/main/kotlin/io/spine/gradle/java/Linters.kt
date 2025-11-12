/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
            options.errorprone.isEnabled.set(false)
        }
    }
}
