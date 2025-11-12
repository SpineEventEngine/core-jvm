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

package io.spine.gradle.report.pom

import groovy.xml.MarkupBuilder
import java.io.StringWriter
import org.gradle.kotlin.dsl.withGroovyBuilder

/**
 * The licensing information of Spine.
 */
internal object SpineLicense {

    private const val NAME = "Apache License, Version 2.0"
    private const val URL = "https://www.apache.org/licenses/LICENSE-2.0.txt"
    private const val DISTRIBUTION = "repo"

    /**
     * Returns the licensing information as an XML fragment compatible with `pom.xml` format.
     */
    override fun toString(): String {
        val result = StringWriter()
        val xml = MarkupBuilder(result)
        xml.withGroovyBuilder {
            "licenses" {
                "license" {
                    "name" { xml.text(NAME) }
                    "url" { xml.text(URL) }
                    "distribution" { xml.text(DISTRIBUTION) }
                }
            }
        }
        return result.toString()
    }
}
