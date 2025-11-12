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
     * A directory which all artifacts are generated into.
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
     * A directory which contains integration test Dart sources.
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
     * As for Dart 2.14,  `pub` still updates the deprecated file for backwards compatibility.
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
 * `pub` tool's input/output files can NOT be overridden because `pub` itself doesn't allow to
 * specify them for its execution.
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
