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

package io.spine.gradle.report.coverage

import io.spine.gradle.report.coverage.FileExtension.COMPILED_CLASS
import io.spine.gradle.report.coverage.FileExtension.JAVA_SOURCE
import io.spine.gradle.report.coverage.PathMarker.ANONYMOUS_CLASS
import io.spine.gradle.report.coverage.PathMarker.GENERATED
import io.spine.gradle.report.coverage.PathMarker.GRPC_SRC_FOLDER
import io.spine.gradle.report.coverage.PathMarker.JAVA_OUTPUT_FOLDER
import io.spine.gradle.report.coverage.PathMarker.JAVA_SRC_FOLDER
import io.spine.gradle.report.coverage.PathMarker.SPINE_JAVA_SRC_FOLDER
import java.io.File

/**
 * This file contains extension methods and properties for `java.io.File`.
 */

/**
 * Parses the name of a class from the absolute path of this file.
 *
 * Treats the fragment between the [precedingMarker] and [extension] as the value to look for.
 * In case the fragment is located and it contains `/` symbols, they are treated
 * as Java package delimiters and are replaced by `.` symbols before returning the value.
 *
 * If the absolute path of this file has either no [precedingMarker] or no [extension],
 * returns `null`.
 */
internal fun File.parseClassName(
    precedingMarker: PathMarker,
    extension: FileExtension
): String? {
    val index = this.absolutePath.lastIndexOf(precedingMarker.infix)
    return if (index > 0) {
        var inFolder = this.absolutePath.substring(index + precedingMarker.length)
        if (inFolder.endsWith(extension.value)) {
            inFolder = inFolder.substring(0, inFolder.length - extension.length)
            inFolder.replace('/', '.')
        } else {
            null
        }
    } else {
        null
    }
}

/**
 * Attempts to parse the file name with either of the specified [parsers],
 * in their respective order.
 *
 * Returns the first non-`null` parsed value.
 *
 * If none of the parsers returns non-`null` value, returns `null`.
 */
internal fun File.parseName(vararg parsers: (file: File) -> String?): String? {
    for (parser in parsers) {
        val className = parser.invoke(this)
        if (className != null) {
            return className
        }
    }
    return null
}

/**
 * Attempts to parse the Java fully-qualified class name from the absolute path of this file,
 * treating it as a path to a human-produced `.java` file.
 */
internal fun File.asJavaClassName(): String? =
    this.parseClassName(JAVA_SRC_FOLDER, JAVA_SOURCE)

/**
 * Attempts to parse the Java fully-qualified class name from the absolute path of this file,
 * treating it as a path to a compiled `.class` file.
 *
 * If the `.class` file corresponds to the anonymous class, only the name of the parent
 * class is returned.
 */
internal fun File.asJavaCompiledClassName(): String? {
    var className = this.parseClassName(JAVA_OUTPUT_FOLDER, COMPILED_CLASS)
    if (className != null && className.contains(ANONYMOUS_CLASS.infix)) {
        className = className.split(ANONYMOUS_CLASS.infix)[0]
    }
    return className
}

/**
 * Attempts to parse the Java fully-qualified class name from the absolute path of this file,
 * treating it as a path to a gRPC-generated `.java` file.
 */
internal fun File.asGrpcClassName(): String? =
    this.parseClassName(GRPC_SRC_FOLDER, JAVA_SOURCE)

/**
 * Attempts to parse the Java fully-qualified class name from the absolute path of this file,
 * treating it as a path to a Spine-generated `.java` file.
 */
internal fun File.asSpineClassName(): String? =
    this.parseClassName(SPINE_JAVA_SRC_FOLDER, JAVA_SOURCE)

/**
 * Tells whether this file is a part of the generated sources, and not produced by a human.
 */
internal val File.isGenerated
    get() = this.absolutePath.contains(GENERATED.infix)
