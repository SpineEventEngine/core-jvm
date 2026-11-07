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

package io.spine.gradle.testing

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

/**
 * Configures the `Test` tasks of this project so that the Gradle TestKit builds
 * they run use the TestKit directory [shared by all modules][SHARED_TESTKIT_DIR].
 *
 * A TestKit directory is the Gradle user home of the builds that
 * [GradleRunner][org.gradle.testkit.runner.GradleRunner] runs. It holds mostly
 * Gradle's instrumented copies of the JARs on the classpath of the plugin under
 * test, and the compiled build scripts of the test projects. When the directory
 * is empty, the TestKit builds recreate them, which is slow on a CI runner.
 *
 * Without the [`org.gradle.testkit.dir`][TESTKIT_DIR_PROPERTY] system property,
 * TestKit keeps the directory at `build/tmp/<task>/work/.gradle-test-kit`, in
 * the temporary directory of the `Test` task. So each `Test` task of each module
 * has its own, which is empty in a fresh checkout and after `clean`. This
 * function passes the shared directory to the test JVM as that property, which
 * `GradleRunner.create()` reads. A runner given a directory with
 * `withTestKitDir(...)` still uses that one.
 *
 * The property is set from a `doFirst` action rather than at configuration time,
 * so it is not a task input. Where TestKit builds keep their caches does not
 * affect the test results, while the absolute path differs between machines.
 * As an input, it would only break the reuse of the `Test` task results from
 * the build cache across machines. A `Test` task that already has the property
 * keeps its value, so a repeated call has no further effect.
 *
 * The function applies to all the `Test` tasks of the project, whether they
 * run TestKit builds or not, and whether the project collects the coverage of
 * those builds or not (see [enableTestKitCoverage]).
 *
 * Starting from ToolBase `2.0.0-SNAPSHOT.460`, the `GradleProject` API of
 * `plugin-testlib` uses this directory, `caches/spine-testkit` under the Gradle
 * user home, as the default for a project set up with
 * `withSharedTestKitDirectory()`. Earlier versions put such a project's TestKit
 * directory under the repository root, in `.gradle-test-kit`. `GradleProject`
 * computes the path itself, from the Gradle user home that the test JVM sees,
 * so keep [SHARED_TESTKIT_DIR] in sync with it.
 */
fun Project.useSharedTestKitDir() {
    val testKitDir = gradle.gradleUserHomeDir.resolve(SHARED_TESTKIT_DIR)
    tasks.withType<Test>().configureEach {
        doFirst {
            if (TESTKIT_DIR_PROPERTY !in systemProperties) {
                systemProperty(TESTKIT_DIR_PROPERTY, testKitDir.absolutePath)
            }
        }
    }
}

/**
 * The name of the system property carrying the absolute path to the TestKit
 * directory, which `GradleRunner.create()` reads.
 *
 * A runner given a directory with `withTestKitDir(...)` uses that one instead.
 *
 * @see org.gradle.testkit.runner.GradleRunner.withTestKitDir
 */
private const val TESTKIT_DIR_PROPERTY: String = "org.gradle.testkit.dir"

/**
 * The path, relative to the Gradle user home of the build, of the TestKit
 * directory shared by the TestKit builds of all modules.
 *
 * The directory is placed under `caches/`, which `gradle/actions/setup-gradle`
 * restores on every run but saves only on the default branch. So a pull request
 * run starts with a warm directory once a run on the default branch has saved it.
 * Locally, the TestKit builds of all the repositories that use this `buildSrc`
 * share it.
 *
 * Unlike a directory under `build/`, it is not reset by `clean`. Delete it to make
 * the TestKit builds start from an empty one.
 */
private const val SHARED_TESTKIT_DIR: String = "caches/spine-testkit"
