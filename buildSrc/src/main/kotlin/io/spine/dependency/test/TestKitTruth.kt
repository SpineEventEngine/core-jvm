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

@file:Suppress("MaxLineLength")

package io.spine.dependency.test

/**
 * Gradle TestKit extension for Google Truth.
 *
 * @see <a href="https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin/tree/main/testkit-truth">TestKit source code</a>
 * @see <a href="https://dev.to/autonomousapps/gradle-all-the-way-down-testing-your-gradle-plugin-with-gradle-testkit-2hmc">Usage description</a>
 */
@Suppress("unused", "ConstPropertyName")
object TestKitTruth {
    private const val version = "1.20.0"
    const val lib = "com.autonomousapps:testkit-truth:$version"
}
