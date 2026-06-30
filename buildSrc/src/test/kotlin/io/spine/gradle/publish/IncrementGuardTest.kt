/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.gradle.publish

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.spine.gradle.publish.IncrementGuard.Companion.localPublishPlanned
import io.spine.gradle.publish.IncrementGuard.Companion.mustVerify
import io.spine.gradle.publish.IncrementGuard.Companion.shouldCheckVersion
import io.spine.gradle.publish.IncrementGuard.Companion.shouldCompareToBase
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`IncrementGuard` should")
class IncrementGuardTest {

    @Nested
    inner class `require the version check` {

        @Test
        fun `for pull requests targeting default branches`() {
            shouldCheckVersion("pull_request", "master") shouldBe true
            shouldCheckVersion("pull_request", "main") shouldBe true
        }

        @Test
        fun `for pull requests targeting release-line branches`() {
            shouldCheckVersion("pull_request", "2.x-jdk8-master") shouldBe true
            shouldCheckVersion("pull_request", "2.x-jdk8-main") shouldBe true
        }
    }

    @Nested
    inner class `not require the version check` {

        @Test
        fun `for pull requests targeting auxiliary branches`() {
            shouldCheckVersion("pull_request", "epic-feature") shouldBe false
            shouldCheckVersion("pull_request", "master-fixes") shouldBe false
        }

        @Test
        fun `for push events`() {
            shouldCheckVersion("push", "master") shouldBe false
            shouldCheckVersion("push", null) shouldBe false
        }

        @Test
        fun `for pull request events without a base branch`() {
            shouldCheckVersion("pull_request", null) shouldBe false
        }

        @Test
        fun `outside GitHub Actions`() {
            shouldCheckVersion(null, null) shouldBe false
        }
    }

    @Nested
    inner class `actually run the check` {

        @Test
        fun `on a CI pull request to a protected branch`() {
            mustVerify(ciPullRequest = true, onCi = true, localPublish = false) shouldBe true
        }

        @Test
        fun `on a local build that publishes to Maven Local`() {
            mustVerify(ciPullRequest = false, onCi = false, localPublish = true) shouldBe true
        }
    }

    @Nested
    inner class `skip the check` {

        @Test
        fun `on a local build that does not publish`() {
            mustVerify(ciPullRequest = false, onCi = false, localPublish = false) shouldBe false
        }

        @Test
        fun `on a CI build that publishes to Maven Local outside a protected-branch PR`() {
            // E.g. a push to `master` or a tag build running integration tests: the
            // version is already published, so re-verifying it would fail the build.
            mustVerify(ciPullRequest = false, onCi = true, localPublish = true) shouldBe false
        }
    }

    @Nested
    inner class `compare against the base branch` {

        @Test
        fun `inside the Version Guard workflow with a base branch`() {
            shouldCompareToBase(underVersionGuard = true, baseRef = "master") shouldBe true
            shouldCompareToBase(underVersionGuard = true, baseRef = "2.x-jdk8-master") shouldBe true
        }
    }

    @Nested
    inner class `not compare against the base branch` {

        @Test
        fun `outside the Version Guard workflow`() {
            // The Ubuntu/Windows CI builds pull the task in via `publishToMavenLocal`,
            // but they never fetch the base ref, so `VERSION_GUARD` is unset.
            shouldCompareToBase(underVersionGuard = false, baseRef = "master") shouldBe false
        }

        @Test
        fun `when no base branch is present`() {
            shouldCompareToBase(underVersionGuard = true, baseRef = null) shouldBe false
            shouldCompareToBase(underVersionGuard = true, baseRef = "") shouldBe false
        }
    }

    @Nested
    inner class `detect a Maven Local publish` {

        @Test
        fun `for the task's own project`() {
            val project = guardedProject()
            val publish = project.tasks
                .register("publishFooPublicationToMavenLocal", PublishToMavenLocal::class.java)
                .get()

            localPublishPlanned(listOf(publish), project) shouldBe true
        }

        @Test
        fun `but not when only a sibling project publishes`() {
            val root = ProjectBuilder.builder().build()
            val lib = ProjectBuilder.builder().withParent(root).withName("lib").build()
            val app = ProjectBuilder.builder().withParent(root).withName("app").build()
            app.pluginManager.apply("maven-publish")
            val appPublish = app.tasks
                .register("publishFooPublicationToMavenLocal", PublishToMavenLocal::class.java)
                .get()

            localPublishPlanned(listOf(appPublish), lib) shouldBe false
        }
    }

    @Nested
    inner class `make 'checkVersionIncrement' a dependency of` {

        @Test
        fun `every Maven Local publishing task`() {
            val project = guardedProject()
            val localPublish = project.tasks.register(
                "publishFooPublicationToMavenLocal",
                PublishToMavenLocal::class.java
            ).get()

            localPublish.dependencyNames() shouldContain IncrementGuard.taskName
        }
    }

    @Nested
    inner class `keep 'checkVersionIncrement' out of` {

        @Test
        fun `the 'check' lifecycle task`() {
            // The CI check runs via the `Version Guard` workflow, which fetches the
            // base branch first. Wiring it into `check` would run it in every
            // `./gradlew build`, where `origin/<base>` is absent and the fail-closed
            // base comparison would break the build.
            val project = guardedProject()
            val check = project.tasks.getByName("check")

            check.dependencyNames() shouldNotContain IncrementGuard.taskName
        }
    }
}

/**
 * Creates a project with the `base` plugin (for the `check` task), the
 * `maven-publish` plugin (for [PublishToMavenLocal] tasks), and [IncrementGuard]
 * applied.
 */
private fun guardedProject(): Project {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("base")
    project.pluginManager.apply("maven-publish")
    project.pluginManager.apply(IncrementGuard::class.java)
    return project
}

/**
 * Obtains the names of the tasks this task directly depends on.
 */
private fun Task.dependencyNames(): Set<String> =
    taskDependencies.getDependencies(this).map { it.name }.toSet()
