/*
 * Copyright 2025, TeamDev. All rights reserved.
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
 * Let's imagine one wants to apply and configure `FooBar` plugin. To do that, several steps
 * should be completed:
 *
 *  1. Declare the corresponding extension function upon [JsPlugins] named after the plugin.
 *  2. Apply and configure the plugin inside that function.
 *  3. Call the resulted extension in your `build.gradle.kts` file.
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
