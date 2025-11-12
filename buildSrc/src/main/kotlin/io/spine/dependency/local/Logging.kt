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

/**
 * Dependencies on the artifacts of the Spine Logging library.
 *
 * @see <a href="https://github.com/SpineEventEngine/logging">spine-logging</a>
 */
@Suppress("ConstPropertyName", "unused")
object Logging {
    const val version = "2.0.0-SNAPSHOT.411"
    const val group = Spine.group

    const val loggingArtifact = "spine-logging"

    const val lib = "$group:$loggingArtifact:$version"
    const val libJvm = "$group:spine-logging-jvm:$version"

    const val log4j2Backend = "$group:spine-logging-log4j2-backend:$version"
    const val stdContext = "$group:spine-logging-std-context:$version"
    const val grpcContext = "$group:spine-logging-grpc-context:$version"
    const val smokeTest = "$group:spine-logging-smoke-test:$version"

    const val testLib = "${Spine.toolsGroup}:spine-logging-testlib:$version"

    // Transitive dependencies.
    // Make `public` and use them to force a version in a particular repository, if needed.
    internal const val julBackend = "$group:spine-logging-jul-backend:$version"
    const val middleware = "$group:spine-logging-middleware:$version"
    internal const val platformGenerator = "$group:spine-logging-platform-generator:$version"
    internal const val jvmDefaultPlatform = "$group:spine-logging-jvm-default-platform:$version"
}
