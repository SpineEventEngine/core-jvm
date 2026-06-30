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
