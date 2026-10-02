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
 * Dependencies on the artifacts of the Spine Logging library.
 *
 * @see <a href="https://github.com/SpineEventEngine/logging">spine-logging</a>
 */
@Suppress("ConstPropertyName", "unused")
object Logging {
    const val version = "2.0.0-SNAPSHOT.425"
    const val group = Spine.group

    const val loggingArtifact = "spine-logging"

    const val lib = "$group:$loggingArtifact:$version"
    const val libJvm = "$group:spine-logging-jvm:$version"

    const val log4j2Backend = "$group:spine-logging-log4j2-backend:$version"
    const val stdContext = "$group:spine-logging-std-context:$version"
    const val grpcContext = "$group:spine-logging-grpc-context:$version"
    const val smokeTest = "$group:spine-logging-smoke-test:$version"

    const val testLib = "${Spine.toolsGroup}:logging-testlib:$version"

    // Transitive dependencies.
    // Make `public` and use them to force a version in a particular repository, if needed.
    internal const val julBackend = "$group:spine-logging-jul-backend:$version"
    const val middleware = "$group:spine-logging-middleware:$version"
    internal const val platformGenerator = "$group:spine-logging-platform-generator:$version"
    internal const val jvmDefaultPlatform = "$group:spine-logging-jvm-default-platform:$version"
}
