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
 * Spine TestLib library.
 *
 * @see <a href="https://github.com/SpineEventEngine/testlib">spine-testlib</a>
 */
@Suppress("ConstPropertyName")
object TestLib {
    const val version = "2.0.0-SNAPSHOT.213"
    const val group = Spine.toolsGroup
    const val artifact = "base-testlib"
    const val lib = "$group:$artifact:$version"
}
