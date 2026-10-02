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
 * Information about the Spine's inception year.
 */
internal object InceptionYear {

    /**
     * The year of the inception of Spine.
     */
    const val value = "2015"

    /**
     * Returns a string containing the inception year of Spine in a `pom.xml` format.
     */
    override fun toString(): String {
        val writer = StringWriter()
        val xml = MarkupBuilder(writer)
        xml.withGroovyBuilder {
            "inceptionYear" { xml.text(value) }
        }
        return writer.toString()
    }
}
