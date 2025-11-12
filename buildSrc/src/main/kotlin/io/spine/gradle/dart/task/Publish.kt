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

package io.spine.gradle.dart.task

import io.spine.gradle.TaskName
import io.spine.gradle.base.assemble
import io.spine.gradle.named
import io.spine.gradle.publish.publish
import io.spine.gradle.register
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/**
 * Registers tasks for publishing Dart projects.
 *
 * Please note, this task group depends on [build] tasks. Therefore, building tasks should
 * be applied in the first place.
 *
 * List of tasks to be created:
 *
 *  1. [TaskContainer.stagePubPublication].
 *  2. [TaskContainer.activateLocally].
 *  3. [TaskContainer.publishToPub].
 *
 * Usage example:
 *
 * ```
 * import io.spine.gradle.dart.dart
 * import io.spine.gradle.dart.task.build
 * import io.spine.gradle.dart.task.publish
 *
 * // ...
 *
 * dart {
 *     tasks {
 *         build()
 *         publish()
 *     }
 * }
 * ```
 */
fun DartTasks.publish() {

    stagePubPublication()
    activateLocally()

    publishToPub().also {
        publish.configure {
            dependsOn(it)
        }
    }
}

private val stagePubPublicationName = TaskName.of("stagePubPublication", Copy::class)

/**
 * Locates `stagePubPublication` in this [TaskContainer].
 *
 * The task prepares the Dart package for Pub publication in the
 * [publication directory][io.spine.gradle.dart.DartEnvironment.publicationDir].
 */
val TaskContainer.stagePubPublication: TaskProvider<Copy>
    get() = named(stagePubPublicationName)

private fun DartTasks.stagePubPublication(): TaskProvider<Copy> =
    register(stagePubPublicationName) {

        description = "Prepares the Dart package for Pub publication."
        group = DartTasks.Group.publish

        dependsOn(assemble)

        // Beside `.dart` sources itself, `pub` package manager conventions require:
        // 1. README.md and CHANGELOG.md to build a page at `pub.dev/packages/<your_package>;`.
        // 2. `pubspec` file to fill out details about your package on the right side of your
        //    package’s page.
        // 3. LICENSE file.

        from(project.projectDir) {
            include("**/*.dart", "pubspec.yaml", "**/*.md")
            exclude("proto/", "generated/", "build/", "**/.*")
        }
        from("${project.rootDir}/LICENSE")
        into(publicationDir)

        doLast {
            logger.debug("Pub publication is prepared in directory `{}`.", publicationDir)
        }
    }

private val publishToPubName = TaskName.of("publishToPub", Exec::class)

/**
 * Locates `publishToPub` task in this [TaskContainer].
 *
 * The task publishes the prepared publication to Pub using `pub publish` command.
 */
val TaskContainer.publishToPub: TaskProvider<Exec>
    get() = named(publishToPubName)

private fun DartTasks.publishToPub(): TaskProvider<Exec> =
    register(publishToPubName) {

        description = "Publishes the prepared publication to Pub."
        group = DartTasks.Group.publish

        dependsOn(stagePubPublication)

        val sayYes = "y".byteInputStream()
        standardInput = sayYes

        workingDir(publicationDir)

        pub("publish", "--trace")
    }

private val activateLocallyName = TaskName.of("activateLocally", Exec::class)

/**
 * Locates `activateLocally` task in this [TaskContainer].
 *
 * Makes this package available in the command line as an executable.
 *
 * The `dart run` command supports running a Dart program — located in a file, in the current
 * package, or in one of the dependencies of the current package - from the command line.
 * To run a program from an arbitrary location, the package should be "activated".
 *
 * See [dart pub global | Dart](https://dart.dev/tools/pub/cmd/pub-global)
 */
val TaskContainer.activateLocally: TaskProvider<Exec>
    get() = named(activateLocallyName)

private fun DartTasks.activateLocally(): TaskProvider<Exec> =
    register(activateLocallyName) {

        description = "Activates this package locally."
        group = DartTasks.Group.publish

        dependsOn(stagePubPublication)

        workingDir(publicationDir)
        pub(
            "global",
            "activate",
            "--source",
            "path",
            publicationDir,
            "--trace"
        )
    }
