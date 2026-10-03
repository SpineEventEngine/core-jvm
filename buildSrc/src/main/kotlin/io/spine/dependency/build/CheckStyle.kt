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

package io.spine.dependency.build

/**
 * Dependencies on Checkstyle Java linter.
 *
 * @see <a href="https://checkstyle.sourceforge.io/">Checkstyle</a>
 * @see [io.spine.gradle.checkstyle.CheckStyleConfig]
 */
@Suppress("unused", "ConstPropertyName")
object CheckStyle {
    /**
     * The version to be used in the project.
     *
     * `10.12.1` is the last version in `10.12.0`, which does not introduce
     * capability conflict over `google-collections` with Guava.
     *
     * @see <a href="https://checkstyle.sourceforge.io/releasenotes.html">Checkstyle</a>
     */
    const val version = "10.12.1"
}
