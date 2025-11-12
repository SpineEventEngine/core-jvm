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

package io.spine.gradle.report.license

import com.github.jk1.license.ModuleData
import io.spine.docs.MarkdownDocument
import kotlin.reflect.KCallable

/**
 * This file declares the Kotlin extensions that help printing `ModuleData` in Markdown format.
 */

/**
 * Prints several of the module data dependencies under the section with the passed [title].
 */
internal fun MarkdownDocument.printSection(
    title: String,
    modules: Iterable<ModuleData>
): MarkdownDocument {
    this.h2(title)
    modules.forEach {
        printModule(it)
    }
    return this
}

/**
 * Prints the module metadata to this [MarkdownDocument].
 */
private fun MarkdownDocument.printModule(module: ModuleData) {
    ol()

    this.print(ModuleData::getGroup, module, "Group")
        .print(ModuleData::getName, module, "Name")
        .print(ModuleData::getVersion, module, "Version")

    val projectUrl = module.projectUrl()
    val licenses = module.licenses()

    if (projectUrl.isNullOrEmpty() && licenses.isEmpty()) {
        bold("No license information found")
        return
    }

    @SuppressWarnings("MagicNumber")    /* As per the original document layout. */
    val listIndent = 5
    printProjectUrl(projectUrl, listIndent)
    printLicenses(licenses, listIndent)

    nl()
}

/**
 * Prints the value of the [ModuleData] property by the passed [getter].
 *
 * The property is printed with the passed [title].
 */
private fun MarkdownDocument.print(
    getter: KCallable<*>,
    module: ModuleData,
    title: String
): MarkdownDocument {
    val value = getter.call(module)
    if (value != null) {
        space().bold(title).and().text(": $value.")
    }
    return this
}

/**
 * Prints the URL to the project which provides the dependency.
 *
 * If the passed project URL is `null` or empty, it is not printed.
 */
@Suppress("SameParameterValue" /* Indentation is consistent across the list. */)
private fun MarkdownDocument.printProjectUrl(projectUrl: String?, indent: Int) {
    if (!projectUrl.isNullOrEmpty()) {
        ul(indent).bold("Project URL:").and().link(projectUrl)
    }
}

/**
 * Prints the links to the source code licenses.
 */
@Suppress("SameParameterValue" /* Indentation is consistent across the list. */)
private fun MarkdownDocument.printLicenses(licenses: Set<License>, indent: Int) {
    for (license in licenses) {
        ul(indent).bold("License:").and()
        if (license.url.isNullOrEmpty()) {
            text(license.text)
        } else {
            link(license.text, license.url)
        }
    }
}

/**
 * Searches for the URL of the project in the module's metadata.
 *
 * Returns `null` if none is found.
 */
private fun ModuleData.projectUrl(): String? {
    val pomUrl = this.poms.firstOrNull()?.projectUrl
    if (!pomUrl.isNullOrBlank()) {
        return pomUrl
    }
    return this.manifests.firstOrNull()?.url
}

/**
 * Collects the links to the source code licenses, under which the module dependency is distributed.
 */
private fun ModuleData.licenses(): Set<License> {
    val result = mutableSetOf<License>()

    val manifestLicense: License? = manifests.firstOrNull()?.let { manifest ->
        val value = manifest.license
        if (!value.isNullOrBlank()) {
            if (value.startsWith("http")) {
                License(value, value)
            } else {
                License(value, manifest.url)
            }
        }
        null
    }
    manifestLicense?.let { result.add(it) }

    val pomLicenses = poms.firstOrNull()?.licenses?.map { license ->
        License(license.name, license.url)
    }
    pomLicenses?.let {
        result.addAll(it)
    }
    return result.toSet()
}

/**
 * The source code license with the URL leading to the license text, as defined
 * by the project's dependency.
 *
 * The URL to the license text may be not defined.
 */
private data class License(val text: String, val url: String?)
