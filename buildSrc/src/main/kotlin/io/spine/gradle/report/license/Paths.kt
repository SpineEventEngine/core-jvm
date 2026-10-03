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

package io.spine.gradle.report.license

import java.io.File

/**
 * Filesystem paths used by [LicenseReporter] and
 * [PomGenerator][io.spine.gradle.report.pom.PomGenerator].
 */
internal object Paths {

    /**
     * The directory in the root project to which dependency reports are written.
     */
    internal const val outputDirectory = "docs/dependencies"

    /**
     * The output filename of the license report.
     *
     * The file with this name is placed under [outputDirectory] of the root Gradle project —
     * as the result of the [LicenseReporter] work.
     *
     * Its contents describe the licensing information for each of the Java dependencies
     * that are referenced by Gradle projects in the repository.
     */
    internal const val outputFilename = "dependencies.md"

    /**
     * The path to a directory, to which a per-project report is generated.
     */
    internal const val relativePath = "reports/dependency-license/dependency"

    /**
     * Obtains a dependency report file under [outputDirectory] of the root project directory.
     */
    internal fun outputFile(rootDirectory: File, filename: String): File =
        rootDirectory.resolve(outputDirectory).resolve(filename)
}
