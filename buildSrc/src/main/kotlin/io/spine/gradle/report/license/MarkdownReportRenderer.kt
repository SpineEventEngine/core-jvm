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

