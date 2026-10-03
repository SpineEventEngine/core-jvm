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

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`MavenMetadata` should")
internal class MavenMetadataSpec {

    /**
     * Round-trips through the same [XmlMapper] used in production, asserting the version list
     * survives. This guards the `var` properties of [MavenMetadata] and [Versioning]: a `val`
     * (or `internal`-mangled setter) would leave the list empty after deserialization, silently
     * disabling the "already published" check.
     */
    @Test
    fun `survive a Jackson round-trip, keeping its versions`() {
        val versions = listOf("2.0.0-SNAPSHOT.79", "2.0.0-SNAPSHOT.80", "2.0.0-SNAPSHOT.81")
        val mapper = XmlMapper()

        val xml = mapper.writeValueAsString(MavenMetadata(Versioning(versions)))
        val parsed = mapper.readValue(xml, MavenMetadata::class.java)

        parsed.versioning.versions shouldContainExactly versions
    }
}
