/*
 * Copyright 2026, TeamDev. All rights reserved.
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

@file:Suppress("ConstPropertyName")

package io.spine.gradle.publish

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy

/**
 * Configures a `ShadowJar` task for Spine SDK publishing.
 */
@Suppress("unused")
fun ShadowJar.setup() {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE  // Required for service-file merging.
    mergeServiceFiles()
    append(DESC_REF)
    deduplicateEntries()
}

/**
 * The name of a descriptor set reference file.
 */
private const val DESC_REF = "desc.ref"

/**
 * Installs a first-copy-wins exclusion predicate for all JAR entries except those
 * registered for merging, such as service files, descriptor set references, etc.
 *
 * Shadow's [org.gradle.api.file.DuplicatesStrategy.INCLUDE] must remain on the task so
 * that every copy of a merged file reaches its
 * [AppendingTransformer][com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer].
 * All other entries — `.class` files, settings JSONs, Kotlin module descriptors,
 * Maven metadata, etc. — are deduplicated here to suppress duplicate-entry warnings
 * and keep the fat JAR size minimal.
 */
private fun ShadowJar.deduplicateEntries() {
    val seenPaths = mutableSetOf<String>()
    doFirst { seenPaths.clear() }
    eachFile {
        val needsMerging = path.isServiceFile || path.isDescriptorSetReference
        if (!needsMerging && !seenPaths.add(path)) {
            exclude()
        }
    }
}

/**
 * Returns `true` for file paths containing references to descriptor set files.
 */
private val String.isDescriptorSetReference: Boolean
    get() = contains(DESC_REF)

/**
 * Tells if the path belongs to a service file.
 */
private val String.isServiceFile: Boolean
    get() = contains("META-INF/services")
