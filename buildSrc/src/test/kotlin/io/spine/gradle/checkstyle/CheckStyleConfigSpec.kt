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

package io.spine.gradle.checkstyle

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.spine.dependency.build.CheckStyle
import org.gradle.api.Project
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.kotlin.dsl.the
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`CheckStyleConfig` should")
internal class CheckStyleConfigSpec {

    private val project: Project = ProjectBuilder.builder().build().also {
        CheckStyleConfig.applyTo(it)
    }

    @Test
    fun `use the declared Checkstyle version`() {
        project.the<CheckstyleExtension>().toolVersion shouldBe CheckStyle.version
    }

    @Test
    fun `exclude the whole Doxia group from the Checkstyle classpath`() {
        val rules = project.configurations.getByName("checkstyle").excludeRules
        rules.map { it.group to it.module } shouldContain ("org.apache.maven.doxia" to null)
    }
}
