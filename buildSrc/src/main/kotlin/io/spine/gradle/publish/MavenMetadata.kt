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

package io.spine.gradle.publish

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import java.io.FileNotFoundException
import java.net.URL

/**
 * A minimal model of a Maven `maven-metadata.xml` document, exposing the published
 * versions of an artifact.
 *
 * Instances are produced by [XmlMapper] from a registry response; only the `<versioning>`
 * element is mapped, and unknown elements are ignored.
 *
 * @property versioning The `<versioning>` element holding the list of published versions.
 *   It is `var` with a default value purely to support deserialization: `buildSrc` uses a
 *   plain [XmlMapper] without the Kotlin module, so Jackson instantiates this class through
 *   the synthesized no-arg constructor and then assigns the property through its setter. The
 *   mutability is required by Jackson, not used by our own code; a `val` would silently
 *   leave the version list empty.
 */
internal data class MavenMetadata(var versioning: Versioning = Versioning()) {

    companion object {

        const val FILE_NAME = "maven-metadata.xml"

        private val mapper = XmlMapper()

        init {
            mapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

        /**
         * Fetches the metadata for the repository and parses the document.
         *
         * If the document could not be found, assumes that the module was never
         * released and thus has no metadata.
         */
        fun fetchAndParse(url: URL): MavenMetadata? {
            return try {
                url.openStream().use { stream ->
                    mapper.readValue(stream, MavenMetadata::class.java)
                }
            } catch (_: FileNotFoundException) {
                null
            }
        }
    }
}

/**
 * The `<versioning>` element of a `maven-metadata.xml` document, listing the published versions.
 *
 * @property versions The published version strings. It is `var` for the same reason as
 *   [MavenMetadata.versioning]: Jackson assigns it through the setter during deserialization
 *   (`buildSrc` has no Kotlin module), so a `val` would leave it empty.
 */
internal data class Versioning(var versions: List<String> = listOf())
