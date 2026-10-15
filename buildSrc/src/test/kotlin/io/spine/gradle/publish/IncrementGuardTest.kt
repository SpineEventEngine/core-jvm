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

package io.spine.gradle.publish

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.gradle.publish.IncrementGuard.Companion.localPublishPlanned
import io.spine.gradle.publish.IncrementGuard.Companion.mustVerify
import io.spine.gradle.publish.IncrementGuard.Companion.shouldCheckVersion
import io.spine.gradle.publish.IncrementGuard.Companion.shouldCompareToBase
import io.spine.gradle.repo.Repository
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.kotlin.dsl.create
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

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

        @Test
        fun `but not when the project publishes only to a remote repository`() {
            val project = guardedProject()
            val remotePublish = project.tasks.register(
                "publishFooPublicationToCloudRepository",
                PublishToMavenRepository::class.java
            ).get()

            localPublishPlanned(listOf(remotePublish), project) shouldBe false
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

    @Nested
    inner class `configure 'checkVersionIncrement' with` {

        @Test
        fun `the root directory of the build`() {
            val root = ProjectBuilder.builder().build()
            val sub = ProjectBuilder.builder().withParent(root).withName("sub").build()
            sub.pluginManager.apply(IncrementGuard::class.java)

            sub.checkVersionTask().rootDir.get().asFile shouldBe root.rootDir
        }

        @Test
        fun `the artifact path of each Maven publication`() {
            val project = publishingProject()
            project.publication(
                "kotlinMultiplatform",
                groupId = "io.spine",
                artifactId = "spine-logging"
            )
            project.publication("jvm", groupId = "io.spine", artifactId = "spine-logging-jvm")

            project.checkVersionTask().artifactPaths.get() shouldBe
                    setOf("io/spine/spine-logging", "io/spine/spine-logging-jvm")
        }

        /**
         * The case of `:compiler-plugins` in `core-jvm-compiler`, which publishes
         * `core-jvm-plugins`. A path computed from the module name does not exist,
         * so the check would pass even for an already published version.
         */
        @Test
        fun `the artifact path of a publication with a custom artifact ID`() {
            val project = publishingProject("compiler-plugins")
            project.publication(
                "fatJar",
                groupId = "io.spine.tools",
                artifactId = "core-jvm-plugins"
            )

            project.checkVersionTask().artifactPaths.get() shouldBe
                    setOf("io/spine/tools/core-jvm-plugins")
        }

        @Test
        fun `the artifact path under the tool artifact prefix`() {
            val root = ProjectBuilder.builder().build()
            root.extensions.create<SpinePublishing>(SpinePublishing.extensionName, root).run {
                toolArtifactPrefix = "core-jvm-"
                modulesWithCustomPublishing = setOf("guarded-tool")
            }
            val tool = ProjectBuilder.builder().withParent(root).withName("guarded-tool").build()
            tool.group = "io.spine.tools"
            tool.pluginManager.apply("maven-publish")
            tool.pluginManager.apply(IncrementGuard::class.java)
            val task = tool.checkVersionTask()
            tool.publications.create<MavenPublication>("mavenJava")
            CustomPublicationHandler.serving(tool, emptySet()).apply()

            task.artifactPaths.get() shouldBe
                    setOf("io/spine/tools/core-jvm-guarded-tool")
        }

        @Test
        fun `the coordinates set after the task is created`() {
            val project = publishingProject()
            val task = project.checkVersionTask()
            val publication = project.publication(
                "mavenJava",
                groupId = "io.spine",
                artifactId = "base"
            )
            publication.artifactId = "spine-base"

            task.artifactPaths.get() shouldBe setOf("io/spine/spine-base")
        }

        /**
         * A marker may be uploaded without the plugin it points to,
         * so it must be checked on its own.
         */
        @Test
        fun `the artifact path of a plugin marker`() {
            val project = publishingProject()
            project.publication(
                "pluginMaven",
                groupId = "io.spine.tools",
                artifactId = "core-jvm-gradle-plugin"
            )
            project.publication(
                "coreJvmPluginMarkerMaven",
                groupId = "io.spine.core-jvm",
                artifactId = "io.spine.core-jvm.gradle.plugin"
            )

            project.checkVersionTask().artifactPaths.get() shouldBe setOf(
                "io/spine/tools/core-jvm-gradle-plugin",
                "io/spine/core-jvm/io.spine.core-jvm.gradle.plugin"
            )
        }

        @Test
        fun `no artifact paths for a project that does not publish`() {
            val project = ProjectBuilder.builder().build()
            project.pluginManager.apply(IncrementGuard::class.java)

            project.checkVersionTask().artifactPaths.get().shouldBeEmpty()
        }
    }

    @Nested
    inner class `make 'checkVersionIncrement' fail` {

        @TempDir
        lateinit var repoDir: File

        /**
         * Only the publication checked last has the version published, so
         * the task must look past the artifacts that do not.
         */
        @Test
        fun `when the version of any publication is already published`() {
            val project = publishingProject()
            project.version = publishedVersion
            project.publication(
                "fatJar",
                groupId = "io.spine.tools",
                artifactId = "core-jvm-plugins"
            )
            project.publication(
                "mavenJava",
                groupId = "io.spine.tools",
                artifactId = "core-jvm-gradle-plugin"
            )
            repoDir.writeMetadata(
                repository = "snapshots",
                path = "io/spine/tools/core-jvm-plugins",
                "2.0.0-SNAPSHOT.093"
            )
            repoDir.writeMetadata(
                repository = "snapshots",
                path = "io/spine/tools/core-jvm-gradle-plugin",
                publishedVersion
            )
            val task = project.checkVersionTask()
            task.repository = repoDir.asRepository()

            val exception = shouldThrow<GradleException> { task.checkVersion() }

            exception.message shouldContain "already published"
            exception.message shouldContain "io/spine/tools/core-jvm-gradle-plugin"
        }

        @Test
        fun `when the version is already published to the releases repository`() {
            val project = publishingProject()
            project.version = "2.0.0"
            project.publication("mavenJava", groupId = "io.spine", artifactId = "spine-base")
            repoDir.writeMetadata(repository = "releases", path = "io/spine/spine-base", "2.0.0")
            val task = project.checkVersionTask()
            task.repository = repoDir.asRepository()

            val exception = shouldThrow<GradleException> { task.checkVersion() }

            exception.message shouldContain "already published"
        }

        /**
         * The unreadable metadata in `releases` stands for an unreachable repository,
         * such as a response with the 401 or 5xx code.
         */
        @Test
        fun `when the version is published to a repository checked before a failing one`() {
            val project = publishingProject()
            project.version = publishedVersion
            project.publication("mavenJava", groupId = "io.spine", artifactId = "spine-base")
            repoDir.writeMetadata(
                repository = "snapshots",
                path = "io/spine/spine-base",
                publishedVersion
            )
            repoDir.resolve("releases/io/spine/spine-base/${MavenMetadata.FILE_NAME}").run {
                parentFile.mkdirs()
                writeText("Not a Maven metadata document.")
            }
            val task = project.checkVersionTask()
            task.repository = repoDir.asRepository()

            val exception = shouldThrow<GradleException> { task.checkVersion() }

            exception.message shouldContain "already published"
        }
    }

    @Nested
    inner class `make 'checkVersionIncrement' pass` {

        @TempDir
        lateinit var repoDir: File

        @Test
        fun `when the version is not published yet`() {
            val project = publishingProject()
            project.version = "2.0.0-SNAPSHOT.095"
            project.publication(
                "fatJar",
                groupId = "io.spine.tools",
                artifactId = "core-jvm-plugins"
            )
            repoDir.writeMetadata(
                repository = "snapshots",
                path = "io/spine/tools/core-jvm-plugins",
                publishedVersion
            )
            val task = project.checkVersionTask()
            task.repository = repoDir.asRepository()

            shouldNotThrowAny { task.checkVersion() }
        }

        /**
         * The task logs a warning in this case: it cannot tell a new artifact from
         * a path that does not match the published one.
         *
         * @see CheckVersionIncrementIgTest for the check of the warning
         */
        @Test
        fun `when the artifact has never been published`() {
            val project = publishingProject()
            project.version = publishedVersion
            project.publication(
                "mavenJava",
                groupId = "io.spine.tools",
                artifactId = "core-jvm-new-module"
            )
            val task = project.checkVersionTask()
            task.repository = repoDir.asRepository()

            shouldNotThrowAny { task.checkVersion() }
        }

        @Test
        fun `for a project that does not publish`() {
            val project = ProjectBuilder.builder().build()
            project.version = publishedVersion
            project.pluginManager.apply(IncrementGuard::class.java)
            val task = project.checkVersionTask()
            task.repository = repoDir.asRepository()

            shouldNotThrowAny { task.checkVersion() }
        }
    }
}

/**
 * The version listed as published by the metadata written with [writeMetadata].
 */
private const val publishedVersion = "2.0.0-SNAPSHOT.094"

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
 * Creates a project with the given [name], which has the `maven-publish` plugin and
 * [IncrementGuard] applied.
 */
private fun publishingProject(name: String = "guarded"): Project {
    val project = ProjectBuilder.builder().withName(name).build()
    project.pluginManager.apply("maven-publish")
    project.pluginManager.apply(IncrementGuard::class.java)
    return project
}

/**
 * Creates a Maven publication with the given [name] and coordinates in this project.
 */
private fun Project.publication(
    name: String,
    groupId: String,
    artifactId: String
): MavenPublication = publications.create<MavenPublication>(name) {
    this.groupId = groupId
    this.artifactId = artifactId
}

/**
 * Obtains the [CheckVersionIncrement] task that [IncrementGuard] registered in this project.
 *
 * The task captures the project version when it is created, so the version must be
 * set before this call.
 */
private fun Project.checkVersionTask(): CheckVersionIncrement =
    tasks.named(IncrementGuard.taskName, CheckVersionIncrement::class.java).get()

/**
 * Writes `maven-metadata.xml` listing the given [versions] of the artifact at [path]
 * into the [repository] subdirectory of this directory.
 *
 * The [repository] is either `snapshots` or `releases`, as in [asRepository].
 */
internal fun File.writeMetadata(repository: String, path: String, vararg versions: String) {
    val (groupPath, artifactId) = path.split('/').let { it.dropLast(1) to it.last() }
    val versionElements = versions.joinToString(separator = "") { "<version>$it</version>" }
    val file = resolve("$repository/$path/${MavenMetadata.FILE_NAME}")
    file.parentFile.mkdirs()
    file.writeText(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <metadata>
          <groupId>${groupPath.joinToString(separator = ".")}</groupId>
          <artifactId>$artifactId</artifactId>
          <versioning>
            <versions>$versionElements</versions>
          </versioning>
        </metadata>
        """.trimIndent()
    )
}

/**
 * Represents this directory as a Maven repository with separate destinations
 * for snapshots and releases, stored in the subdirectories of the same names.
 */
private fun File.asRepository(): Repository {
    val url = toURI().toString().removeSuffix("/")
    return Repository(
        name = "local",
        releases = "$url/releases",
        snapshots = "$url/snapshots",
    )
}

/**
 * Obtains the names of the tasks this task directly depends on.
 */
private fun Task.dependencyNames(): Set<String> =
    taskDependencies.getDependencies(this).map { it.name }.toSet()
