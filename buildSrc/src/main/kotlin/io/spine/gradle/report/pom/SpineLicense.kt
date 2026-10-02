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
