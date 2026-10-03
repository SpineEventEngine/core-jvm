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

import io.spine.gradle.javascript.JsContext
import io.spine.gradle.javascript.JsEnvironment
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.tasks.TaskContainer

/**
 * A scope for applying and configuring JavaScript-related plugins.
 *
 * The scope extends [JsContext] and provides shortcuts for key project's containers:
 *
 *  1. [plugins].
 *  2. [extensions].
 *  3. [tasks].
 *
 * Let's imagine one wants to apply and configure the `FooBar` plugin. To do that, several steps
 * should be completed:
 *
 *  1. Declare the corresponding extension function upon [JsPlugins] named after the plugin.
 *  2. Apply and configure the plugin inside that function.
 *  3. Call the resulting extension in your `build.gradle.kts` file.
 *
 * Here's an example of `javascript/plugin/FooBar.kt`:
 *
 * ```
 * fun JsPlugins.fooBar() {
 *     plugins.apply("com.fooBar")
 *     extensions.configure<FooBarExtension> {
 *         // ...
 *     }
 * }
 * ```
 *
 * And here's how to apply it in `build.gradle.kts`:
 *
 *  ```
 * import io.spine.gradle.javascript.javascript
 * import io.spine.gradle.javascript.plugins.fooBar
 *
 * // ...
 *
 * javascript {
 *     plugins {
 *         fooBar()
 *     }
 * }
 *  ```
 */
class JsPlugins(jsEnv: JsEnvironment, project: Project) : JsContext(jsEnv, project) {

    internal val plugins = project.plugins
    internal val extensions = project.extensions
    internal val tasks = project.tasks

    internal fun plugins(configurations: PluginContainer.() -> Unit) =
        plugins.run(configurations)

    internal fun extensions(configurations: ExtensionContainer.() -> Unit) =
        extensions.run(configurations)

    internal fun tasks(configurations: TaskContainer.() -> Unit) =
        tasks.run(configurations)
}
