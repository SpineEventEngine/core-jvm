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

package io.spine.gradle

import io.kotest.matchers.shouldBe
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@DisplayName("`ClonedRepo` should")
internal class ClonedRepoSpec {

    @TempDir
    lateinit var tempDir: Path

    /** The `buildSrc` of the local `config` to be copied. */
    private lateinit var source: Path

    /** The root of the repository under test. */
    private lateinit var location: Path

    @BeforeEach
    fun prepareFolders() {
        source = tempDir.resolve("config").resolve(BUILD_SRC)
        location = tempDir.resolve("repo")
    }

    @Test
    fun `copy the files, and not only the folders`() {
        source.createFile("quality/detekt-config.yml", "The quality settings.")
        source.createFile("src/main/kotlin/io/spine/dependency/build/Ksp.kt", "object Ksp")

        replaceBuildSrc()

        val copied = location.resolve(BUILD_SRC)
        copied.resolve("quality/detekt-config.yml").readText() shouldBe "The quality settings."
        copied.resolve("src/main/kotlin/io/spine/dependency/build/Ksp.kt")
            .readText() shouldBe "object Ksp"
    }

    @Test
    fun `skip the build output and the Git metadata`() {
        source.createFile(BUILD_SCRIPT, "The build script.")
        source.createFile("src/main/kotlin/Deps.kt", "object Deps")
        source.createFile("build/classes/Deps.class", "The compiled code.")
        source.createFile(".gradle/file-system.probe", "The probe.")
        source.createFile(".kotlin/sessions/session.salive", "The session.")
        source.createFile(".git/HEAD", "ref: refs/heads/master")

        replaceBuildSrc()

        val copied = location.resolve(BUILD_SRC)
        copied.resolve("src/main/kotlin/Deps.kt").exists() shouldBe true
        copied.resolve("build").exists() shouldBe false
        copied.resolve(".gradle").exists() shouldBe false
        copied.resolve(".kotlin").exists() shouldBe false
        copied.resolve(".git").exists() shouldBe false
    }

    @Test
    fun `skip the build output of a nested Gradle project`() {
        val config = tempDir.resolve("config")
        config.createFile(BUILD_SCRIPT, "The build script.")
        config.createFile("$BUILD_SRC/settings.gradle.kts", "The settings of `buildSrc`.")
        config.createFile("$BUILD_SRC/build/classes/Deps.class", "The compiled code.")
        config.createFile("$BUILD_SRC/.gradle/file-system.probe", "The probe.")
        config.createFile("$BUILD_SRC/src/main/kotlin/io/spine/dependency/build/Ksp.kt", "object Ksp")

        val repo = GitRepository(URI("https://github.com/SpineEventEngine/base"))
        ClonedRepo(repo, location).replaceConfig(config, null)

        val copied = location.resolve("config")
        copied.resolve("$BUILD_SRC/build").exists() shouldBe false
        copied.resolve("$BUILD_SRC/.gradle").exists() shouldBe false
        copied.resolve("$BUILD_SRC/src/main/kotlin/io/spine/dependency/build/Ksp.kt")
            .readText() shouldBe "object Ksp"
    }

    @Test
    fun `preserve the 'module gradle kts' of the repository under test`() {
        source.createFile(MODULE_SCRIPT, TEMPLATE_MODULE_SCRIPT)
        location.resolve(BUILD_SRC).createFile(MODULE_SCRIPT, OWN_MODULE_SCRIPT)

        replaceBuildSrc()

        location.resolve(BUILD_SRC).resolve(MODULE_SCRIPT)
            .readText() shouldBe OWN_MODULE_SCRIPT
        location.resolve("$BUILD_SRC-original").resolve(MODULE_SCRIPT)
            .readText() shouldBe OWN_MODULE_SCRIPT
    }

    @Test
    fun `take the template 'module gradle kts' when the repository has none`() {
        source.createFile(MODULE_SCRIPT, TEMPLATE_MODULE_SCRIPT)

        replaceBuildSrc()

        location.resolve(BUILD_SRC).resolve(MODULE_SCRIPT)
            .readText() shouldBe TEMPLATE_MODULE_SCRIPT
    }

    @Test
    fun `skip a link that does not resolve`() {
        source.createFile("src/main/kotlin/Deps.kt", "object Deps")
        val link = source.resolve("guidelines")
        link.createSymbolicLinkPointingTo(source.resolve("shared/guidelines"))

        replaceBuildSrc()

        val copied = location.resolve(BUILD_SRC)
        copied.resolve("src/main/kotlin/Deps.kt").exists() shouldBe true
        copied.resolve("guidelines").exists() shouldBe false
    }

    @Test
    fun `skip the ignored folder`() {
        source.createFile("src/main/kotlin/Deps.kt", "object Deps")
        source.createFile("tmp/base/README.md", "The checked out sources.")

        replaceBuildSrc(ignoredFolder = source.resolve("tmp"))

        val copied = location.resolve(BUILD_SRC)
        copied.resolve("src/main/kotlin/Deps.kt").exists() shouldBe true
        copied.resolve("tmp").exists() shouldBe false
    }

    private fun replaceBuildSrc(ignoredFolder: Path? = null) {
        val repo = GitRepository(URI("https://github.com/SpineEventEngine/base"))
        ClonedRepo(repo, location).replaceBuildSrc(source, ignoredFolder)
    }

    private companion object {

        const val BUILD_SRC = "buildSrc"
        const val BUILD_SCRIPT = "build.gradle.kts"
        const val MODULE_SCRIPT = "src/main/kotlin/module.gradle.kts"
        const val TEMPLATE_MODULE_SCRIPT = "// A template to be defined by a project."
        const val OWN_MODULE_SCRIPT = "plugins { `java-library` }"
    }
}
