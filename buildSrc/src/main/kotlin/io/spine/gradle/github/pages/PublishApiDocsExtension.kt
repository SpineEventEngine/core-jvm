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

package io.spine.gradle.github.pages

import java.io.File
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.property

/**
 * Configures the `publishApiDocs` extension.
 */
@Suppress("unused")
fun Project.publishApiDocs(
    action: PublishApiDocsExtension.() -> Unit
) {
    apply<PublishApiDocs>()

    val extension = extensions.getByType(PublishApiDocsExtension::class)
    extension.action()
}

/**
 * The extension for configuring the [PublishApiDocs] plugin.
 *
 * @property rootFolder The root folder of the repository to which the documented `Project` belongs.
 * @property includeInputs The external inputs, which output should be included
 *   into the published documentation. The values are interpreted according to
 *   [Copy.from][org.gradle.api.tasks.Copy.from] specification.
 *   This property is optional.
 */
class PublishApiDocsExtension private constructor(
    val rootFolder: Property<File>,
    val includeInputs: SetProperty<Any>
) {
    internal companion object {

        /**
         * The name of the extension.
         */
        const val name = "publishApiDocs"

        /**
         * Creates a new extension and adds it to the passed project.
         */
        fun createIn(project: Project): PublishApiDocsExtension {
            val factory = project.objects
            val result = PublishApiDocsExtension(
                rootFolder = factory.property(File::class),
                includeInputs = factory.setProperty(Any::class.java)
            )
            project.extensions.add(result.javaClass, name, result)
            return result
        }
    }

    /**
     * Returns the local root folder of the repository, to which the handled Gradle
     * Project belongs.
     */
    fun rootFolder(): File = rootFolder.get()

    /**
     * Returns the external inputs, whose results should be included into the
     * published documentation.
     */
    fun includedInputs(): Set<Any> = includeInputs.get()
}
