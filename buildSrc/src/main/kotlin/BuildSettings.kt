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

import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * This object provides high-level constants, like the version of JVM, to be used
 * throughout the project.
 */
object BuildSettings {
    private const val JVM_VERSION = 17
    val javaVersion: JavaLanguageVersion = JavaLanguageVersion.of(JVM_VERSION)
    @Suppress("unused")
    val javaVersionCompat = JavaVersion.toVersion(JVM_VERSION)
    val jvmTarget = JvmTarget.JVM_17
    const val REMOTE_DEBUG_PORT = 5566
}
