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
 * A [high performance](https://github.com/ben-manes/caffeine/wiki/Benchmarks),
 * [near optimal](https://github.com/ben-manes/caffeine/wiki/Efficiency) caching library.
 *
 * This library is a transitive dependency for us via
 * [io.spine.dependency.lib.Aedile] and
 * [io.spine.dependency.build.ErrorProne].
 *
 * @see <a href="https://github.com/ben-manes/caffeine">Caffeine at GitHub</a>
 */
@Suppress("unused")
object Caffeine {
    private const val version = "3.2.4"
    const val lib = "com.github.ben-manes.caffeine:caffeine:$version"
}
