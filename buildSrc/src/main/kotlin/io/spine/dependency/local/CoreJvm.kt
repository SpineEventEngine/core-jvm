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

// For backward compatibility.
@Suppress("unused")
@Deprecated("Use `CoreJvm` instead.", ReplaceWith("CoreJvm"))
typealias CoreJava = CoreJvm

/**
 * Dependencies on `core-jvm` modules.
 *
 * See [`SpineEventEngine/core-jvm`](https://github.com/SpineEventEngine/core-jvm/).
 */
@Suppress("ConstPropertyName", "unused")
object CoreJvm {
    const val group = Spine.group
    const val version = "2.0.0-SNAPSHOT.552"

    const val coreArtifact = "spine-core"
    const val clientArtifact = "spine-client"
    const val serverArtifact = "spine-server"

    const val core = "$group:$coreArtifact:$version"
    const val client = "$group:$clientArtifact:$version"
    const val server = "$group:$serverArtifact:$version"

    @Deprecated("Use `serverTestLib` instead.", ReplaceWith("serverTestLib"))
    const val testUtilServer = "${Spine.toolsGroup}:server-testlib:$version"

    const val coreTestLib = "${Spine.toolsGroup}:core-testlib:$version"
    const val clientTestLib = "${Spine.toolsGroup}:client-testlib:$version"
    const val serverTestLib = "${Spine.toolsGroup}:server-testlib:$version"
}
