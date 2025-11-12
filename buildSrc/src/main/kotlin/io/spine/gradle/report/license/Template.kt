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
