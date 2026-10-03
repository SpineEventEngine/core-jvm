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

import io.kotest.matchers.shouldBe
import io.spine.gradle.report.pom.PomGenerator
import java.io.File
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.BasePlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@DisplayName("Dependency reports should")
class DependencyReportOutputTest {

    @TempDir
    lateinit var projectDir: File

    private lateinit var project: Project

    @BeforeEach
    fun setUp() {
        project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()
        project.group = "io.spine"
        project.version = "2.0.0"
    }

    @Test
    fun `write the generated POM under docs-dependencies`() {
        PomGenerator.applyTo(project)

        project.tasks.named("generatePom").get()
            .executeActions()

        val pomFile = projectDir.resolve("docs/dependencies/pom.xml")
        pomFile.exists() shouldBe true
    }

    @Test
    fun `merge license reports under docs-dependencies`() {
        project.pluginManager.apply(BasePlugin::class.java)
        val subproject = subproject("sub")
        LicenseReporter.generateReportIn(subproject)
        val sourceReport = subproject.layout.buildDirectory.asFile.get()
            .resolve(Paths.relativePath)
            .resolve(Paths.outputFilename)
        sourceReport.parentFile.mkdirs()
        sourceReport.writeText("license report")

        LicenseReporter.mergeAllReports(project)

        project.tasks.named("mergeAllLicenseReports").get()
            .executeActions()

        val reportFile = projectDir.resolve("docs/dependencies/dependencies.md")
        reportFile.readText() shouldBe "license report"
    }

    private fun subproject(name: String): Project {
        val subprojectDir = projectDir.resolve(name)
        subprojectDir.mkdirs()
        return ProjectBuilder.builder()
            .withName(name)
            .withParent(project)
            .withProjectDir(subprojectDir)
            .build()
    }

    private fun Task.executeActions() {
        actions.forEach {
            it.execute(this)
        }
    }
}
