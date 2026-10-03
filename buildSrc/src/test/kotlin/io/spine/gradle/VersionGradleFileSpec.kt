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

package io.spine.gradle

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`VersionGradleFile` should read the publishing version")
internal class VersionGradleFileSpec {

    @Test
    fun `declared as a literal`() {
        val content = """
            val versionToPublish: String by extra("2.0.0-SNAPSHOT.182")
        """.trimIndent()

        VersionGradleFile.keyForValue(content, "2.0.0-SNAPSHOT.182") shouldBe "versionToPublish"
        VersionGradleFile.valueForKey(content, "versionToPublish") shouldBe "2.0.0-SNAPSHOT.182"
    }

    @Test
    fun `declared as an alias to another 'extra'`() {
        val content = """
            val compilerVersion: String by extra("2.0.0-SNAPSHOT.043")
            val versionToPublish by extra(compilerVersion)
        """.trimIndent()

        VersionGradleFile.valueForKey(content, "versionToPublish") shouldBe "2.0.0-SNAPSHOT.043"
        VersionGradleFile.valueForKey(content, "compilerVersion") shouldBe "2.0.0-SNAPSHOT.043"
    }

    @Test
    fun `declared as an alias to a plain 'val'`() {
        val content = """
            val base = "2.0.0-SNAPSHOT.043"
            val versionToPublish by extra(base)
        """.trimIndent()

        VersionGradleFile.valueForKey(content, "versionToPublish") shouldBe "2.0.0-SNAPSHOT.043"
    }

    @Test
    fun `declared as a literal via 'extra set'`() {
        val content = """
            extra.set("versionToPublish", "2.0.0-SNAPSHOT.182")
        """.trimIndent()

        VersionGradleFile.keyForValue(content, "2.0.0-SNAPSHOT.182") shouldBe "versionToPublish"
        VersionGradleFile.valueForKey(content, "versionToPublish") shouldBe "2.0.0-SNAPSHOT.182"
    }

    @Test
    fun `declared as an alias via 'extra set'`() {
        val content = """
            val compilerVersion = "2.0.0-SNAPSHOT.043"
            extra.set("compilerVersion", compilerVersion)
            extra.set("versionToPublish", compilerVersion)
        """.trimIndent()

        VersionGradleFile.valueForKey(content, "versionToPublish") shouldBe "2.0.0-SNAPSHOT.043"
        VersionGradleFile.valueForKey(content, "compilerVersion") shouldBe "2.0.0-SNAPSHOT.043"
    }

    @Test
    fun `identified by the resolved project version, not a hard-coded name`() {
        val content = """
            val kotlinVersion: String by extra("2.1.0")
            val versionToPublish: String by extra("2.0.0-SNAPSHOT.182")
        """.trimIndent()

        VersionGradleFile.keyForValue(content, "2.0.0-SNAPSHOT.182") shouldBe "versionToPublish"
    }

    @Test
    fun `absent when no property matches`() {
        val content = """
            val versionToPublish: String by extra("2.0.0-SNAPSHOT.182")
        """.trimIndent()

        VersionGradleFile.keyForValue(content, "9.9.9") shouldBe null
        VersionGradleFile.valueForKey(content, "missing") shouldBe null
    }
}
