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

/**
 * Code coverage library for Java.
 *
 * @see <a href="https://www.eclemma.org/jacoco/">Releases</a>
 */
@Suppress("ConstPropertyName")
object Jacoco {
    const val version = "0.8.15"

    /**
     * The Maven coordinates of the standalone JaCoCo agent JAR (the `runtime`
     * classifier), attached via `-javaagent:` to forked JVMs — Gradle TestKit
     * workers and the Spine Compiler process — so their execution is credited
     * to coverage.
     */
    const val agent = "org.jacoco:org.jacoco.agent:$version:runtime"
}
