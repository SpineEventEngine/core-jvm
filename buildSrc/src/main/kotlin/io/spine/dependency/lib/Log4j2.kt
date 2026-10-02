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

/**
 * An open-source logging framework.
 *
 * Spine uses its own [logging library][io.spine.dependency.local.Logging], but also
 * provides a backend implementation for [Log4j2]. This is why
 * this dependency is needed.
 *
 * @see <a href="https://github.com/apache/logging-log4j2">Log4j2 releases at GitHub</a>
 */
@Suppress("unused", "ConstPropertyName")
object Log4j2 {
    const val version = "2.26.1"

    const val core = "org.apache.logging.log4j:log4j-core:$version"

    /**
     * Routes the calls made through the [SLF4J API][Slf4J.lib] to the [core] backend.
     *
     * Add this artifact when a third-party library logs via SLF4J — such as Micronaut —
     * and its output should reach the Log4j2 backend of the application.
     *
     * The artifact is `log4j-slf4j2-impl`, the binding for the SLF4J 2.x that [Slf4J]
     * declares. Do not substitute `log4j-slf4j-impl`: it binds SLF4J 1.7 only, and
     * under SLF4J 2.x it registers no provider, which leaves the logging silently
     * unbound instead of failing the build.
     *
     * @see <a href="https://logging.apache.org/log4j/2.x/log4j-slf4j2-impl/">Log4j2 SLF4J 2.x binding</a>
     */
    const val slf4j2Bridge = "org.apache.logging.log4j:log4j-slf4j2-impl:$version"
}
