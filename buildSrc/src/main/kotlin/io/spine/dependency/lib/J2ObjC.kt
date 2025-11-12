/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
    private const val version = "2.8"
    const val annotations = "com.google.j2objc:j2objc-annotations:$version"
    @Deprecated("Please use `annotations` instead.", ReplaceWith("annotations"))
    const val lib = annotations
}
