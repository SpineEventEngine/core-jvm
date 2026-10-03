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

import io.spine.docs.MarkdownDocument
import io.spine.gradle.artifactId
import java.util.Date
import org.gradle.api.Project

/**
 * The template text pieces of the license report.
 */
internal class Template(
    private val project: Project,
    private val out: MarkdownDocument
) {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val longBreak = "\n\n"
    }

    internal fun writeHeader() = with(project) {
        out.nl()
           .h1("Dependencies of `$group:$artifactId:$version`")
           .nl()
    }

    internal fun writeFooter() {
        val currentTime = Date()
        out.text(longBreak)
            .text("The dependencies distributed under several licenses, ")
            .text("are used according their commercial-use-friendly license.")
            .text(longBreak)
            .text("This report was generated on ")
            .bold("$currentTime")
            .text(" using ")
            .nl()
            .link(
                "Gradle-License-Report plugin",
                "https://github.com/jk1/Gradle-License-Report"
            )
            .text(" by Evgeny Naumenko, ")
            .text("licensed under ")
            .nl()
            .link(
                "Apache 2.0 License",
                "https://github.com/jk1/Gradle-License-Report/blob/master/LICENSE"
            )
            .text(".")
    }
}
