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

package io.spine.dependency.lib

// https://github.com/google/flogger
@Deprecated("Please use Spine Logging library instead.")
@Suppress("unused", "ConstPropertyName")
object Flogger {
    internal const val version = "0.9"
    const val lib = "com.google.flogger:flogger:$version"

    object Runtime {
        const val systemBackend = "com.google.flogger:flogger-system-backend:$version"
        const val log4j2Backend = "com.google.flogger:flogger-log4j2-backend:$version"
        const val slf4JBackend  = "com.google.flogger:flogger-slf4j-backend:$version"
        const val grpcContext   = "com.google.flogger:flogger-grpc-context:$version"
    }
}
