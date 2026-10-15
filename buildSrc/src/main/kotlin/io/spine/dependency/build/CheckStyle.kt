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
     * `12.3.1` is the last release that runs on Java 17, which our builds use
     * to run Checkstyle. `13.0.0` and later require Java 21.
     *
     * `12.3.1` depends on Doxia, which Checkstyle uses only to generate its own
     * website. Through Doxia, it brings two versions each of `commons-lang3` and
     * `httpcore`. `forceVersions()` enables `failOnVersionConflict()` in the
     * `checkstyle` configuration too, so Gradle would reject the graph.
     * [CheckStyleConfig][io.spine.gradle.checkstyle.CheckStyleConfig] therefore
     * excludes the `org.apache.maven.doxia` group from the `checkstyle` configuration.
     * Releases from `13.11.0` on no longer depend on Doxia, so the exclusion can go
     * once we run Checkstyle on Java 21.
     *
     * @see <a href="https://checkstyle.sourceforge.io/releasenotes.html">Checkstyle</a>
     */
    const val version = "12.3.1"
}
