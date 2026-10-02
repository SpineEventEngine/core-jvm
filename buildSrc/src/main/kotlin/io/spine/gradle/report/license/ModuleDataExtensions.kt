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
 * Prints the URL to the project that provides the dependency.
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

    manifests.firstOrNull()?.licenses?.mapNotNullTo(result) { license ->
        val name: String? = license.name
        val url: String? = license.url
        when {
            name.isNullOrBlank() -> url?.takeIf { it.isNotBlank() }?.let { License(it, it) }
            name.startsWith("http") -> License(name, url ?: name)
            else -> License(name, url)
        }
    }

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
