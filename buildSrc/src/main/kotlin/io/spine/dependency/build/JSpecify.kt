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

/**
 * An artifact of well-specified annotations to power static analysis
 * checks and JVM language interop. Developed by consensus of the partner
 * organizations listed at [the project site](https://jspecify.org).
 *
 * @see <a href="https://github.com/jspecify/jspecify">JSpecify at GitHub</a>
 */
@Suppress("ConstPropertyName")
object JSpecify {
    const val version = "1.0.1"
    const val annotations = "org.jspecify:jspecify:$version"
}
