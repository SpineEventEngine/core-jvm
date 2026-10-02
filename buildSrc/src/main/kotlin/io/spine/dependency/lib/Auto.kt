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

@file:Suppress("unused", "ConstPropertyName")

package io.spine.dependency.lib

// https://github.com/google/auto
object AutoCommon {
    private const val version = "1.2.2"
    const val lib = "com.google.auto:auto-common:$version"
}

// https://github.com/google/auto
object AutoService {
    private const val version = "1.1.1"
    const val annotations = "com.google.auto.service:auto-service-annotations:$version"
    @Suppress("unused")
    const val processor   = "com.google.auto.service:auto-service:$version"
}

// https://github.com/google/auto
object AutoValue {
    private const val version = "1.11.1"
    const val annotations = "com.google.auto.value:auto-value-annotations:$version"
}

// https://github.com/ZacSweers/auto-service-ksp
object AutoServiceKsp {
    private const val version = "1.2.0"
    const val processor = "dev.zacsweers.autoservice:auto-service-ksp:$version"
}
