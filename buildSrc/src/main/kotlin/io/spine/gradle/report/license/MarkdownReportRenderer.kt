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
import com.github.jk1.license.ProjectData
import com.github.jk1.license.render.ReportRenderer
import io.spine.docs.MarkdownDocument
import java.io.File
import org.gradle.api.Project

/**
 * Renders the dependency report for a single [project][ProjectData] in Markdown.
 */
internal class MarkdownReportRenderer(
    private val filename: String
) : ReportRenderer {

    override fun render(data: ProjectData) {
        val project = data.project
        val outputFile = outputFile(project)
        val document = MarkdownDocument()
        val template = Template(project, document)

        template.writeHeader()
        ProjectDependencies.of(data).printTo(document)
        template.writeFooter()

        document.writeToFile(outputFile)
    }

    private fun outputFile(project: Project): File {
        val ext = project.extensions.findByName("licenseReport") as LicenseReportExtension
        return File(ext.outputDir).resolve(filename)
    }
}

