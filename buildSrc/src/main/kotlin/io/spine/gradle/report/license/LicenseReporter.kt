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

import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.LicenseReportExtension.ALL
import com.github.jk1.license.LicenseReportPlugin
import io.spine.dependency.local.Spine
import io.spine.gradle.SpineTaskGroup
import io.spine.gradle.applyPlugin
import io.spine.gradle.getTask
import io.spine.gradle.report.license.Paths.outputFilename
import io.spine.gradle.report.license.Paths.relativePath
import java.io.File
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.the

/**
 * Generates the license report for all Java dependencies used in a single Gradle project
 * and in a repository.
 *
 * Transitive dependencies are included.
 *
 * The output file is placed under `docs/dependencies` of the root Gradle project.
 *
 * Usage:
 *
 * ```
 * // ...
 * subprojects {
 *
 *      LicenseReporter.generateReportIn(project)
 * }
 *
 * // ...
 *
 * LicenseReporter.mergeAllReports(project)
 *
 * ```
 */
object LicenseReporter {

    /**
     * The name of the Gradle task that generates the reports for a specific Gradle project.
     */
    private const val projectTaskName = "generateLicenseReport"

    /**
     * The name of the Gradle task merging the license reports across all Gradle projects
     * in the repository into a single report file.
     */
    private const val mergeTaskName = "mergeAllLicenseReports"

    /**
     * The reason for opting [projectTaskName] out of the build cache.
     *
     * The task only re-renders already resolved dependency metadata into a small
     * Markdown file, so recomputing it is cheaper than a cache round trip.
     * Gradle Doctor consistently reports such tasks as
     * [slower from cache](https://runningcode.github.io/gradle-doctor/slower-from-cache/).
     *
     * Up-to-date checks still apply, so an unchanged project does not re-run the task.
     */
    private const val cacheOptOutReason =
        "Rendering the license report is faster than fetching and unpacking a cache entry."

    /**
     * Enables the generation of the license report for a single Gradle project.
     *
     * Registers `generateLicenseReport` task, which is later picked up
     * by the [merge task][mergeAllReports].
     */
    fun generateReportIn(project: Project) {
        project.applyPlugin(LicenseReportPlugin::class.java)
        val reportOutputDir = project.layout.buildDirectory.dir(relativePath).get().asFile

        with(project.the<LicenseReportExtension>()) {
            outputDir = reportOutputDir.absolutePath
            excludeGroups = arrayOf(
                Spine.group,
                "io.spine.gcloud",
                Spine.toolsGroup,
                "io.spine.validation"
            )
            configurations = ALL

            renderers = arrayOf(MarkdownReportRenderer(outputFilename))
        }

        // The rendered report embeds the project's Maven coordinates — including its
        // version — in the report header (see `Template.writeHeader`). The
        // `generateLicenseReport` task keys its up-to-date check on the resolved
        // dependencies only, not on the project version. Without the version as an explicit
        // input, a version-only change leaves the task `UP-TO-DATE`, so the report keeps the
        // previous version while `pom.xml`, produced by an always-running task, is updated.
        // Declaring the version as an input invalidates the output when it changes, so the
        // report is regenerated. The value is read lazily so it reflects the version resolved
        // at execution time, regardless of when `project.version` is assigned
        // during configuration.
        project.tasks.generateLicenseReport.configure {
            inputs.property("projectVersion", project.provider { project.version.toString() })
            outputs.doNotCacheIf(cacheOptOutReason) { true }
        }
    }

    /**
     * Tells to merge all per-project reports that were previously [generated][generateReportIn]
     * for each of the subprojects of the root Gradle project.
     *
     * The merge result is placed according to [Paths].
     *
     * Registers a `mergeAllLicenseReports` that is specified to be executed after `build`.
     */
    fun mergeAllReports(project: Project) {
        val rootProject = project.rootProject
        val mergeTask = rootProject.tasks.register(mergeTaskName) {
            group = SpineTaskGroup.name
            description = "Merges per-project license reports into a single repository-wide report"
            val consolidationTask = this
            val assembleTask = project.getTask<Task>("assemble")
            val sourceProjects: Iterable<Project> = sourceProjects(rootProject)
            sourceProjects.forEach {
                val perProjectTask = it.getTask<Task>(projectTaskName)
                consolidationTask.dependsOn(perProjectTask)
                perProjectTask.dependsOn(assembleTask)
            }
            doLast {
                mergeReports(sourceProjects, rootProject)
            }
            dependsOn(assembleTask)
        }
        project.getTask<Task>("build")
            .finalizedBy(mergeTask)
    }

    /**
     * Determines the source projects for which the resulting report will be produced.
     */
    private fun Task.sourceProjects(rootProject: Project): Iterable<Project> {
        val targetProjects: Iterable<Project> = if (rootProject.subprojects.isEmpty()) {
            rootProject.logger.debug(
                "The license report will be produced for a single root project."
            )
            listOf(this.project)
        } else {
            rootProject.logger.debug(
                "The license report will be produced for all subprojects of a root project."
            )
            rootProject.subprojects
        }
        return targetProjects
    }

    /**
     * Merges the license reports from all [sourceProjects] into a single file under
     * the [rootProject]'s dependency report directory.
     */
    private fun mergeReports(
        sourceProjects: Iterable<Project>,
        rootProject: Project
    ) {
        val paths = sourceProjects
            .map {
                val buildDir = it.layout.buildDirectory.asFile.get()
                "$buildDir/$relativePath/$outputFilename"
            }.filter {
                val exists = File(it).exists()
                if (!exists) {
                    rootProject.logger.debug("License report file not found: $it")
                }
                exists
            }
        println("Merging the license reports from all projects.")
        val mergedContent = paths.joinToString("\n\n\n") { (File(it)).readText() }
        val output = Paths.outputFile(rootProject.rootDir, outputFilename)
        output.parentFile.mkdirs()
        output.writeText(mergedContent)
    }
}
