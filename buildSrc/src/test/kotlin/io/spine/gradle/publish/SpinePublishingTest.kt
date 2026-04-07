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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.gradle.repo.Repository
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`SpinePublishing` should")
class SpinePublishingTest {

    private lateinit var project: Project
    private lateinit var extension: SpinePublishing

    @BeforeEach
    fun setUp() {
        project = ProjectBuilder.builder().build()
        extension = project.spinePublishing { }
    }

    @Nested
    inner class `calculate 'artifactId'` {

        @Test
        fun `with default prefix for non-tool projects`() {
            val subproject = ProjectBuilder.builder()
                .withParent(project)
                .withName("base")
                .build()
            subproject.group = "io.spine"

            extension.artifactId(subproject) shouldBe "spine-base"
        }

        @Test
        fun `with custom prefix for non-tool projects`() {
            extension.artifactPrefix = "custom-"
            val subproject = ProjectBuilder.builder()
                .withParent(project)
                .withName("core")
                .build()
            subproject.group = "io.spine"

            extension.artifactId(subproject) shouldBe "custom-core"
        }

        @Test
        fun `with tool prefix for tool projects`() {
            extension.toolArtifactPrefix = "tool-"
            val toolProject = ProjectBuilder.builder()
                .withParent(project)
                .withName("model-compiler")
                .build()
            toolProject.group = "io.spine.tools"

            extension.artifactId(toolProject) shouldBe "tool-model-compiler"
        }

        @Test
        fun `without prefix for tool projects if 'NONE' is specified`() {
            extension.toolArtifactPrefix = "NONE"
            val toolProject = ProjectBuilder.builder()
                .withParent(project)
                .withName("proto-js")
                .build()
            toolProject.group = "io.spine.tools"

            extension.artifactId(toolProject) shouldBe "proto-js"
        }

        @Test
        fun `throwing IllegalStateException if tool prefix is empty for tool projects`() {
            extension.toolArtifactPrefix = ""
            val toolProject = ProjectBuilder.builder()
                .withParent(project)
                .withName("tool")
                .build()
            toolProject.group = "io.spine.tools"

            shouldThrow<IllegalStateException> {
                extension.artifactId(toolProject)
            }
        }
    }

    @Nested
    inner class `validate configuration` {

        @Test
        fun `ensuring 'testJar' inclusions are published`() {
            extension.modules = setOf("pub-module")
            extension.testJar {
                inclusions = setOf("non-pub-module")
            }

            val exception = shouldThrow<IllegalStateException> {
                extension.configured()
            }
            exception.message!! shouldContain "non-pub-module"
        }

        @Test
        fun `ensuring 'customPublishing' is not misused with modules`() {
            extension.modules = setOf("some-module")
            extension.customPublishing = true

            val exception = shouldThrow<IllegalStateException> {
                extension.configured()
            }
            exception.message!! shouldContain "customPublishing"
        }

        @Test
        fun `ensuring modules are not duplicated in root and subproject`() {
            val rootProject = project
            val subproject = ProjectBuilder.builder()
                .withParent(rootProject)
                .withName("sub")
                .build()

            // Root project already has the 'spinePublishing' extension created in 'setUp'.
            // Let's use it instead of creating a second one with a different name.
            extension.modules = setOf("sub")

            val extensionName = SpinePublishing.extensionName
            val subExtension =
                subproject.extensions.create<SpinePublishing>(extensionName, subproject)

            val exception = shouldThrow<IllegalStateException> {
                subExtension.configured()
            }
            exception.message!! shouldContain "already configured in a root project"
        }
    }

    @Nested
    inner class `identify 'projectsToPublish'` {

        @Test
        fun `as the project itself if no modules are specified`() {
            val projects = extension.invokeProjectsToPublish()
            projects shouldHaveSize 1
            projects shouldContain project
        }

        @Test
        fun `as the specified modules`() {
            val sub1 = ProjectBuilder.builder().withParent(project).withName("sub1").build()
            val sub2 = ProjectBuilder.builder().withParent(project).withName("sub2").build()

            extension.modules = setOf("sub1", "sub2")

            val projects = extension.invokeProjectsToPublish()
            projects shouldHaveSize 2
            projects shouldContain sub1
            projects shouldContain sub2
        }

        @Test
        fun `including modules with custom publishing`() {
            val sub1 = ProjectBuilder.builder().withParent(project).withName("sub1").build()
            val sub2 = ProjectBuilder.builder().withParent(project).withName("sub2").build()

            extension.modules = setOf("sub1")
            extension.modulesWithCustomPublishing = setOf("sub2")

            val projects = extension.invokeProjectsToPublish()
            projects shouldHaveSize 2
            projects shouldContain sub1
            projects shouldContain sub2
        }
    }

    @Nested
    inner class `resolve 'publishTo' repositories` {

        @Test
        fun `from the extension itself if destinations are initialized`() {
            val repo = Repository(
                "test-repo",
                "https://example.com/release",
                "https://example.com/snapshot"
            )
            extension.destinations = setOf(repo)

            val repos = project.invokePublishTo(extension)
            repos shouldHaveSize 1
            repos shouldContain repo
        }

        @Test
        fun `from the parent project if not specified locally`() {
            val repo = Repository(
                "parent-repo",
                "https://example.com/release",
                "https://example.com/snapshot"
            )
            // Root project has its extension named 'spinePublishing' from setUp.
            extension.destinations = setOf(repo)

            val subproject = ProjectBuilder.builder().withParent(project).withName("sub").build()
            // Subproject has no local 'spinePublishing' extension.

            val repos = subproject.invokePublishTo(extension)
            repos shouldHaveSize 1
            repos shouldContain repo
        }
    }
}

/**
 * Accesses private/internal methods of [SpinePublishing] for testing purposes.
 */
private fun SpinePublishing.invokeProjectsToPublish(): Collection<Project> {
    val method = SpinePublishing::class.java.getDeclaredMethod("projectsToPublish")
    method.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return method.invoke(this) as Collection<Project>
}

private fun Project.invokePublishTo(extension: SpinePublishing): Set<Repository> {
    val method = SpinePublishing::class.java.getDeclaredMethod("publishTo", Project::class.java)
    method.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return method.invoke(extension, this) as Set<Repository>
}
