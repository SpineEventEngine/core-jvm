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

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Proto extensions should")
internal class ProtoExtsSpec {

    private val project: Project = ProjectBuilder.builder().build().also {
        it.pluginManager.apply(BasePlugin::class.java)
    }

    private val jar: Jar = project.tasks.register("protoJar", Jar::class.java).get()

    @Test
    fun `make a JAR depend on generateProto registered later, without creating it`() {
        jar.dependOnGenerateProto()
        var created = false
        project.tasks.register("generateProto") { created = true }

        created shouldBe false
        jar.dependencyNames() shouldContainExactly listOf("generateProto")
    }

    @Test
    fun `add no dependency to a JAR in a project without generateProto`() {
        jar.dependOnGenerateProto()

        jar.dependencyNames().shouldBeEmpty()
    }

    private fun Jar.dependencyNames(): List<String> =
        taskDependencies.getDependencies(this).map { it.name }
}
