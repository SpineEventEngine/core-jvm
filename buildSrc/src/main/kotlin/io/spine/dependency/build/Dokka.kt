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

package io.spine.dependency.build

import io.spine.dependency.local.Spine

// https://github.com/Kotlin/dokka
@Suppress("unused", "ConstPropertyName")
object Dokka {
    private const val group = "org.jetbrains.dokka"

    /**
     * When changing the version, also change the version used in the
     * `buildSrc/build.gradle.kts`.
     */
    const val version = "2.2.0"

    object GradlePlugin {
        const val id = "org.jetbrains.dokka"

        /**
         * The version of this plugin is already specified in `buildSrc/build.gradle.kts`
         * file. Thus, when applying the plugin to project's build files, only the [id]
         * should be used.
         */
        const val lib = "$group:dokka-gradle-plugin:$version"
    }

    object BasePlugin {
        const val lib = "$group:dokka-base:$version"
    }

    const val analysis = "org.jetbrains.dokka:dokka-analysis:$version"

    object CorePlugin {
        const val lib = "$group:dokka-core:$version"
    }

    /**
     * To generate the documentation as seen from the Java perspective, please use this plugin.
     *
     * @see <a href="https://github.com/Kotlin/dokka#output-formats">
     *     Dokka output formats</a>
     */
    object KotlinAsJavaPlugin {
        const val lib = "$group:kotlin-as-java-plugin:$version"
    }

    /**
     * Custom Dokka plugins developed for Spine-specific needs like excluding by
     * `@Internal` annotation.
     *
     * @see <a href="https://github.com/SpineEventEngine/dokka-tools/tree/master/dokka-extensions">
     *     Custom Dokka Plugins</a>
     */
    object SpineExtensions {
        private const val group = Spine.toolsGroup

        const val version = "2.0.0-SNAPSHOT.9"

        /**
         * The artifact dropped its `spine-` prefix in `2.0.0-SNAPSHOT.9`, to
         * match the other tool artifacts. Earlier versions are published as
         * `spine-dokka-extensions`.
         */
        const val lib = "$group:dokka-extensions:$version"
    }
}
