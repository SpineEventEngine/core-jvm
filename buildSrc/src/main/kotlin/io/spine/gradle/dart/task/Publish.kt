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

package io.spine.gradle.dart.task

import io.spine.gradle.SpineTaskGroup
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
        group = SpineTaskGroup.name

        dependsOn(assemble)

        // Besides `.dart` sources itself, `pub` package manager conventions require:
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
 * Locates the `publishToPub` task in this [TaskContainer].
 *
 * The task publishes the prepared publication to Pub using the `pub publish` command.
 */
val TaskContainer.publishToPub: TaskProvider<Exec>
    get() = named(publishToPubName)

private fun DartTasks.publishToPub(): TaskProvider<Exec> =
    register(publishToPubName) {

        description = "Publishes the prepared publication to Pub."
        group = SpineTaskGroup.name

        dependsOn(stagePubPublication)

        val sayYes = "y".byteInputStream()
        standardInput = sayYes

        workingDir(publicationDir)

        pub("publish", "--trace")
    }

private val activateLocallyName = TaskName.of("activateLocally", Exec::class)

/**
 * Locates the `activateLocally` task in this [TaskContainer].
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
        group = SpineTaskGroup.name

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
