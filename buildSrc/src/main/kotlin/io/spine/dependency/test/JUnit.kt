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

package io.spine.dependency.test

import io.spine.dependency.Dependency
import io.spine.dependency.DependencyWithBom

// https://junit.org/
@Suppress("unused", "ConstPropertyName")
object JUnit : DependencyWithBom() {

    override val version = "6.0.0"
    override val group: String = "org.junit"

    /**
     * The BOM of JUnit.
     *
     * This one should be forced in a project via:
     *
     * ```kotlin
     * dependencies {
     *     testImplementation(enforcedPlatform(JUnit.bom))
     * }
     * ```
     * The version of JUnit is forced automatically by
     * the [BomsPlugin][io.spine.dependency.boms.BomsPlugin]
     * when it is applied to the project.
     */
    override val bom = "$group:junit-bom:$version"

    private const val legacyVersion = "4.13.1"

    // https://github.com/apiguardian-team/apiguardian
    private const val apiGuardianVersion = "1.1.2"

    // https://github.com/junit-pioneer/junit-pioneer
    private const val pioneerVersion = "2.3.0"
    const val pioneer = "org.junit-pioneer:junit-pioneer:$pioneerVersion"

    const val legacy = "junit:junit:$legacyVersion"

    object Jupiter : Dependency() {
        override val version = JUnit.version
        override val group = "org.junit.jupiter"
        private const val infix = "junit-jupiter"

        // We do not use versions because they are forced via BOM.
        val api = "$group:$infix-api"
        val params = "$group:$infix-params"
        val engine = "$group:$infix-engine"

        override val modules = listOf(api, params, engine)
    }

    /**
     * The same as [Jupiter.artifacts].
     */
    override val modules = Jupiter.modules

    object Platform : Dependency() {

        /**
         * The version of the platform is defined by JUnit BOM.
         *
         * So when we use JUnit as a platform, this property should be picked up
         * for the dependencies automatically.
         */
        override val version: String = "6.0.0"
        override val group = "org.junit.platform"

        private const val infix = "junit-platform"
        val commons = "$group:$infix-commons"
        val launcher = "$group:$infix-launcher"
        val engine = "$group:$infix-engine"
        val suiteApi = "$group:$infix-suite-api"

        override val modules = listOf(commons, launcher, engine, suiteApi)
    }
}
