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
 * [J2ObjC](https://developers.google.com/j2objc) is a transitive dependency,
 * which we don't use directly. This object is used for forcing the version.
 */
@Suppress("unused", "ConstPropertyName")
object J2ObjC {
    /**
     * See [J2ObjC releases](https://github.com/google/j2objc/releases).
     *
     * `1.3` was the latest version available from Maven Central.
     * Now `2.8` is the latest version available.
     * As [HttpClient]
     * [migrated](https://github.com/googleapis/google-http-java-client/releases/tag/v1.43.3) to v2,
     * we set the latest v2 version as well.
     *
     * @see <a href="https://search.maven.org/artifact/com.google.j2objc/j2objc-annotations">
     *     J2ObjC on Maven Central</a>
     */
    private const val version = "3.1"
    const val annotations = "com.google.j2objc:j2objc-annotations:$version"
    @Deprecated("Please use `annotations` instead.", ReplaceWith("annotations"))
    const val lib = annotations
}
