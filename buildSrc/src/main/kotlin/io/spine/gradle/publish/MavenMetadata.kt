/*
 * Copyright 2026, TeamDev. All rights reserved.
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
                val metadata = mapper.readValue(url, MavenMetadata::class.java)
                metadata
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
