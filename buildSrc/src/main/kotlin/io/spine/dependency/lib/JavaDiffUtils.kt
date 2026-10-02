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
 * The dependency on the `java-diff-utils` library, which is transitive for us at the time
 * of writing.
 *
 * It might become our dependency as a part of
 * the [Spine Text](https://github.com/SpineEventEngine/text) library.
 */
@Suppress("unused", "ConstPropertyName")
object JavaDiffUtils {

    // https://github.com/java-diff-utils/java-diff-utils/releases
    private const val version = "4.17"
    const val lib = "io.github.java-diff-utils:java-diff-utils:$version"
}
