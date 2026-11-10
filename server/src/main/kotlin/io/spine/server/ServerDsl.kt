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

package io.spine.server

/**
 * Marks the builders of the Kotlin DSL that configures a server.
 *
 * Inside a block nested in another block of this DSL, such as `under<Tests> { }` inside
 * `serverEnvironment { }`, the members of the outer builder cannot be called without
 * naming their receiver. So a call meant for the inner builder cannot reach the outer one
 * by mistake.
 *
 * @see application
 * @see serverEnvironment
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
public annotation class ServerDsl
