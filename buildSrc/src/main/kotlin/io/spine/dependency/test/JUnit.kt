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

package io.spine.dependency.test

import io.spine.dependency.Dependency
import io.spine.dependency.DependencyWithBom

// https://junit.org/
@Suppress("unused", "ConstPropertyName")
object JUnit : DependencyWithBom() {

    override val version = "6.1.3"
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

    private const val legacyVersion = "4.13.2"

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
         *
         * Since JUnit 6 the platform shares the version line of JUnit itself,
         * so this property mirrors [JUnit.version] rather than repeating it.
         * Under JUnit 5 the two differed — the platform was `1.x` while
         * Jupiter was `5.x`.
         */
        override val version: String = JUnit.version
        override val group = "org.junit.platform"

        private const val infix = "junit-platform"
        val commons = "$group:$infix-commons"
        val launcher = "$group:$infix-launcher"
        val engine = "$group:$infix-engine"
        val suiteApi = "$group:$infix-suite-api"

        override val modules = listOf(commons, launcher, engine, suiteApi)
    }
}
