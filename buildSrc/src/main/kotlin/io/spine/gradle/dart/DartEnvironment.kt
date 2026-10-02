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

package io.spine.gradle.dart

import java.io.File
import org.apache.tools.ant.taskdefs.condition.Os

/**
 * Describes the environment in which Dart code is assembled and processed during the build.
 *
 * Consists of two parts describing:
 *
 *  1. The module itself.
 *  2. Tools and their input/output files.
 */
interface DartEnvironment {

    /*
     * A module itself
     ******************/

    /**
     * Module's root catalog.
     */
    val projectDir: File

    /**
     * Module's name.
     */
    val projectName: String

    /**
     * A directory that all artifacts are generated into.
     *
     * Default value: "$projectDir/build".
     */
    val buildDir: File
        get() = projectDir.resolve("build")

    /**
     * A directory where artifacts for further publishing would be prepared.
     *
     * Default value: "$buildDir/pub/publication/$projectName".
     */
    val publicationDir: File
        get() = buildDir
            .resolve("pub")
            .resolve("publication")
            .resolve(projectName)

    /**
     * A directory that contains integration test Dart sources.
     *
     * Default value: "$projectDir/integration-test".
     */
    val integrationTestDir: File
        get() = projectDir.resolve("integration-test")

    /*
     * Tools and their input/output files
     *************************************/

    /**
     * Name of an executable for running `pub` tool.
     *
     * Default value:
     *
     *  1. "pub.bat" for Windows.
     *  2. "pub" for other Oss.
     */
    val pubExecutable: String
        get() = if (isWindows()) "pub.bat" else "pub"

    /**
     * Dart module's metadata file.
     *
     * Every pub package needs some metadata so it can specify its dependencies. Pub packages that
     * are shared with others also need to provide some other information so users can discover
     * them. All of this metadata goes in the package’s `pubspec`.
     *
     * Default value: "$projectDir/pubspec.yaml".
     *
     * See [The pubspec file | Dart](https://dart.dev/tools/pub/pubspec)
     */
    val pubSpec: File
        get() = projectDir.resolve("pubspec.yaml")

    /**
     * Module dependencies' index that maps resolved package names to location URIs.
     *
     * By default, pub creates a [packageConfig] file in the `.dart_tool/` directory for this.
     * Before the [packageConfig], pub used to create this [packageIndex] file in the root
     * directory.
     *
     * As for Dart 2.14, `pub` still updates the deprecated file for backwards compatibility.
     *
     * Default value: "$projectDir/.packages".
     */
    val packageIndex: File
        get() = projectDir.resolve(".packages")

    /**
     * Module dependencies' index that maps resolved package names to location URIs.
     *
     * Default value: "$projectDir/.dart_tool/package_config.json".
     */
    val packageConfig: File
        get() = projectDir
            .resolve(".dart_tool")
            .resolve("package_config.json")
}

/**
 * Allows overriding [DartEnvironment]'s defaults.
 *
 * Please note, not all properties of the environment can be overridden. Properties that describe
 * the `pub` tool's input/output files can NOT be overridden because `pub` itself doesn't allow
 * specifying them for its execution.
 *
 * The next properties could not be overridden:
 *
 *  1. [DartEnvironment.pubSpec].
 *  2. [DartEnvironment.packageIndex].
 *  3. [DartEnvironment.packageConfig].
 */
class ConfigurableDartEnvironment(initialEnv: DartEnvironment)
    : DartEnvironment by initialEnv
{
    /*
     * A module itself
     ******************/

    override var projectDir = initialEnv.projectDir
    override var projectName = initialEnv.projectName
    override var buildDir = initialEnv.buildDir
    override var publicationDir = initialEnv.publicationDir
    override var integrationTestDir = initialEnv.integrationTestDir

    /*
     * Tools and their input/output files
     *************************************/

    override var pubExecutable = initialEnv.pubExecutable
}

internal fun isWindows(): Boolean = Os.isFamily(Os.FAMILY_WINDOWS)
