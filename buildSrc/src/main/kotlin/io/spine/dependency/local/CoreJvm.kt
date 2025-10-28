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

package io.spine.dependency.local

// For backward compatibility.
typealias CoreJava = CoreJvm

/**
 * Dependencies on `core-java` modules.
 *
 * See [`SpineEventEngine/core-java`](https://github.com/SpineEventEngine/core-java/).
 */
@Suppress("ConstPropertyName", "unused")
object CoreJvm {
    const val group = Spine.group
    const val version = "2.0.0-SNAPSHOT.332"

    const val coreArtifact = "spine-core"
    const val clientArtifact = "spine-client"
    const val serverArtifact = "spine-server"

    const val core = "$group:$coreArtifact:$version"
    const val client = "$group:$clientArtifact:$version"
    const val server = "$group:$serverArtifact:$version"

    @Deprecated("Use `serverTestLib` instead.", ReplaceWith("serverTestLib"))
    const val testUtilServer = "${Spine.toolsGroup}:spine-server-testlib:$version"

    const val coreTestLib = "${Spine.toolsGroup}:spine-core-testlib:$version"
    const val clientTestLib = "${Spine.toolsGroup}:spine-client-testlib:$version"
    const val serverTestLib = "${Spine.toolsGroup}:spine-server-testlib:$version"
}
