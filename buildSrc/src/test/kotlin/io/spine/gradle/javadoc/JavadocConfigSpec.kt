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

package io.spine.gradle.javadoc

import io.kotest.matchers.shouldBe
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`JavadocConfig` should")
internal class JavadocConfigSpec {

    private val project: Project = ProjectBuilder.builder().build().also {
        it.pluginManager.apply(JavaPlugin::class.java)
    }

    /**
     * The project of a build without subprojects is not a production module,
     * so its `javadoc` task is disabled.
     */
    @Test
    fun `configure the javadoc task once Gradle creates it`() {
        var created = false
        project.tasks.named { it == "javadoc" }.configureEach { created = true }

        JavadocConfig.applyTo(project)

        created shouldBe false
        project.tasks.javadocTask().get().enabled shouldBe false
    }
}
