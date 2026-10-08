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

package io.spine.dependency.local

/**
 * Custom Dokka plugins developed for Spine-specific needs like excluding by
 * `@Internal` annotation.
 *
 * See [`SpineEventEngine/dokka-tools`](https://github.com/SpineEventEngine/dokka-tools/).
 */
@Suppress(
    "unused" /* Some subprojects do not use the Dokka tools directly. */,
    "ConstPropertyName" /* We use a custom convention for artifact properties. */,
)
object DokkaTools {
    const val group = Spine.toolsGroup
    const val version = "2.0.0-SNAPSHOT.10"

    /**
     * The artifact dropped its `spine-` prefix in `2.0.0-SNAPSHOT.9`, to
     * match the other tool artifacts. Earlier versions are published as
     * `spine-dokka-extensions`.
     */
    const val extensions = "$group:dokka-extensions:$version"
}
