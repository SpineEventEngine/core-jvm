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

package io.spine.gradle.javascript.plugin

import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.withGroovyBuilder

/**
 * Applies the `mc-js` plugin and specifies directories for generated code.
 *
 * @see JsPlugins
 */
fun JsPlugins.mcJs() {

    plugins {
        apply("io.spine.mc-js")
    }

    // Temporarily use GroovyInterop.
    // Currently, it is not possible to obtain `McJsPlugin` on the classpath of `buildSrc`.
    // See issue: https://github.com/SpineEventEngine/config/issues/298

    project.withGroovyBuilder {
        "protoJs" {
            setProperty("generatedMainDir", genProtoMain)
            setProperty("generatedTestDir", genProtoTest)
        }
    }
}

/**
 * Locates `generateJsonParsers` in this [TaskContainer].
 *
 * The task generates JSON-parsing code for JavaScript messages compiled from Protobuf.
 */
val TaskContainer.generateJsonParsers: TaskProvider<Task>
    get() = named("generateJsonParsers")
