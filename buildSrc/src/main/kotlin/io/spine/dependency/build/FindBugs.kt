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
 * The FindBugs project has been dead since 2017. It has a successor called SpotBugs,
 * but we don't use it. We use ErrorProne for static analysis instead.
 * The only reason for having this dependency is the annotations for null-checking
 * introduced by JSR-305. These annotations are troublesome,
 * but no alternatives are known for some of them so far.
 * Please see [this issue](https://github.com/SpineEventEngine/base/issues/108) for more details.
 */
@Suppress("unused", "ConstPropertyName")
object FindBugs {
    private const val version = "3.0.2"
    const val annotations = "com.google.code.findbugs:jsr305:$version"
}
