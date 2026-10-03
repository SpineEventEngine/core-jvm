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

package io.spine.dependency.test

// https://github.com/google/truth
@Suppress("unused", "ConstPropertyName")
object Truth {
    private const val version = "1.4.5"
    val libs = listOf(
        "com.google.truth:truth:$version",
        "com.google.truth.extensions:truth-java8-extension:$version",
        "com.google.truth.extensions:truth-proto-extension:$version"
    )
}
