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

// The rewrite of an SPDX document by the components of its artifact belongs together.
@file:Suppress("TooManyFunctions")

package io.spine.gradle.publish

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.Serial
import java.io.Serializable
import org.gradle.api.logging.Logger

/**
 * The components an artifact depends on, and those bundled into it.
 *
 * They tell how the artifact relates to each package the SPDX Gradle Plugin lists
 * for it, all of which the plugin relates to the artifact as its dependencies.
 *
 * @see PublicationSbomTask.artifactComponents
 */
internal data class ArtifactComponents(

    /** The components of the dependency graph of the artifact. */
    val dependencies: Set<ComponentKey> = emptySet(),

    /** The components bundled into the artifact. */
    val bundled: Set<ComponentKey> = emptySet(),
) : Serializable {

    private companion object {
        @Serial
        private const val serialVersionUID: Long = 0L
    }
}

/**
 * Relates the artifact the given SPDX [document] describes to the packages it lists,
 * as to its dependencies and to its content.
 *
 * The plugin walks the configurations of the SBOM one after another, from the
 * module of the artifact, and relates each package it finds as a dependency of
 * the first package through which it found it. The walk of a later configuration
 * goes through the packages found before, so it can relate a package it finds on
 * the way to the bundled ones to a dependency of the artifact. So, in the rewritten
 * document:
 *  - the artifact contains each bundled package, rather than depending on it;
 *  - a package the artifact neither depends on nor bundles is left out, along with
 *    its relationships;
 *  - a dependency of the artifact does not depend on a bundled package that is not
 *    a dependency of the artifact;
 *  - a license text that only the packages left out refer to is left out too.
 *
 * A bundled package that another one already contains, such as a file of
 * a component resolved to several, stays contained in that one alone.
 * A package whose component cannot be told is left as it is. The plugin writes
 * none such.
 */
internal fun ArtifactComponents.describeIn(document: ObjectNode, logger: Logger) {
    val packages = document.arrayNamed(SpdxField.packages)
    val relationships = document.arrayNamed(SpdxField.relationships)
    val artifact = relationships.describedElement()
    val allKeys = packages.componentKeys()
    val keys = allKeys - artifact
    val leftOut = keys.filterValues { it !in dependencies && it !in bundled }.keys
    val content = keys.filterValues { it in bundled }.keys
    val kept = relationships
        .filterIsInstance<ObjectNode>()
        .filterNot { it.spdxElementId in leftOut || it.relatedSpdxElement in leftOut }
        .filterNot { it.bringsBundledIntoDependencies(this@describeIn, keys = keys) }
        .map { it.asContainmentIfBundled(artifact = artifact, content = content) }
        .distinctBy { Triple(it.spdxElementId, it.relationshipType, it.relatedSpdxElement) }
    val contained = kept
        .filter { it.relationshipType == CONTAINS }
        .map { it.relatedSpdxElement }
        .toSet()
    val containment = (content - contained).map {
        relationship(element = artifact, type = CONTAINS, related = it)
    }
    relationships.replaceWith(kept + containment)
    packages.replaceWith(packages.filterNot { it.spdxId in leftOut })
    document.dropUnusedLicenses()
    if (leftOut.isNotEmpty()) {
        logger.info(
            "The SBOM leaves out what the artifact neither depends on nor bundles: {}.",
            leftOut.map { keys.getValue(it) }.sortedBy { it.toString() }
        )
    }
    val unlisted = bundled - allKeys.values.toSet()
    if (unlisted.isNotEmpty()) {
        logger.warn(
            "The SBOM lists no package for these components bundled into the artifact: {}.",
            unlisted.sortedBy { it.toString() }
        )
    }
}

/**
 * Tells if this relationship makes a dependency of the artifact depend on
 * a bundled component that is not a dependency of the artifact.
 *
 * Only the walk of a configuration listing bundled content makes such
 * a relationship: the dependency graph of the artifact does not have it.
 *
 * @param components the components of the artifact.
 * @param keys the keys of the components of the packages, by the SPDX IDs
 *   of the packages.
 */
private fun ObjectNode.bringsBundledIntoDependencies(
    components: ArtifactComponents,
    keys: Map<String, ComponentKey>
): Boolean {
    val element = keys[spdxElementId]
    val related = keys[relatedSpdxElement]
    return relationshipType == DEPENDS_ON
            && element != null && element !in components.bundled
            && related != null && related !in components.dependencies
}

/**
 * Returns the containment of the package this relationship relates to by the [artifact],
 * if the relationship makes the artifact depend on a package of its [content].
 * Otherwise, returns this relationship.
 */
private fun ObjectNode.asContainmentIfBundled(
    artifact: String,
    content: Set<String>
): ObjectNode {
    val bundledDependency = spdxElementId == artifact
            && relationshipType == DEPENDS_ON
            && relatedSpdxElement in content
    return if (bundledDependency) {
        relationship(element = artifact, type = CONTAINS, related = relatedSpdxElement)
    } else {
        this
    }
}

/**
 * The type of the relationship of an SPDX element to one it depends on.
 */
internal const val DEPENDS_ON = "DEPENDS_ON"

/**
 * The type of the relationship of an SPDX element to one it contains.
 */
internal const val CONTAINS = "CONTAINS"

/**
 * The type of the relationship of an SPDX document to the element it describes.
 */
internal const val DESCRIBES = "DESCRIBES"

/**
 * The SPDX ID of the document itself.
 */
internal const val DOCUMENT_ID = "SPDXRef-DOCUMENT"

private val JsonNode.spdxId: String
    get() = text(SpdxField.spdxId)

private val JsonNode.spdxElementId: String
    get() = text(SpdxField.spdxElementId)

private val JsonNode.relationshipType: String
    get() = text(SpdxField.relationshipType)

private val JsonNode.relatedSpdxElement: String
    get() = text(SpdxField.relatedSpdxElement)

/**
 * Returns the text of the given [field] of this SPDX element.
 */
private fun JsonNode.text(field: String): String =
    get(field)?.asText() ?: error("The SPDX element `$this` has no `$field`.")

private fun relationship(element: String, type: String, related: String): ObjectNode =
    JsonNodeFactory.instance.objectNode()
        .put(SpdxField.spdxElementId, element)
        .put(SpdxField.relationshipType, type)
        .put(SpdxField.relatedSpdxElement, related)

private fun ObjectNode.arrayNamed(name: String): ArrayNode =
    get(name) as? ArrayNode ?: error("The SPDX document has no `$name` array.")

private fun ArrayNode.replaceWith(elements: List<JsonNode>) {
    removeAll()
    addAll(elements)
}

/**
 * Returns the SPDX ID of the package this array of relationships tells the document
 * to describe.
 */
private fun ArrayNode.describedElement(): String {
    val described = filter { it.spdxElementId == DOCUMENT_ID && it.relationshipType == DESCRIBES }
    require(described.size == 1) {
        "An SBOM describes the package of its artifact alone, but this one describes" +
                " ${described.size} packages."
    }
    return described.single().relatedSpdxElement
}

/**
 * Returns the keys of the components the packages in this array stand for, by the SPDX
 * IDs of the packages. A package whose component cannot be told is left out.
 */
private fun ArrayNode.componentKeys(): Map<String, ComponentKey> =
    mapNotNull { pkg -> ComponentKey.ofPackage(pkg)?.let { pkg.spdxId to it } }.toMap()

/**
 * The fields of an SPDX package that hold license expressions.
 */
private val licenseFields = listOf(
    SpdxField.licenseConcluded,
    SpdxField.licenseDeclared,
    SpdxField.licenseInfoFromFiles
)

/**
 * Matches a reference to a license text that the document holds.
 */
private val licenseReference = Regex("""LicenseRef-[A-Za-z0-9.\-]+""")

/**
 * Returns the references to the license texts of the document, such as
 * `LicenseRef-gnrtd0`, that the license expressions of the given package make.
 */
internal fun licenseReferencesOf(pkg: JsonNode): Set<String> =
    licenseFields
        // Not `asText()`: it returns an empty string for `licenseInfoFromFiles`, an array.
        .mapNotNull { pkg[it]?.toString() }
        .flatMap { expression -> licenseReference.findAll(expression).map { it.value } }
        .toSet()

/**
 * Leaves out the license texts that no package of this document refers to.
 */
private fun ObjectNode.dropUnusedLicenses() {
    val licenses = get(SpdxField.hasExtractedLicensingInfos) as? ArrayNode ?: return
    val used = arrayNamed(SpdxField.packages).flatMap(::licenseReferencesOf).toSet()
    licenses.replaceWith(licenses.filter { it[SpdxField.licenseId]?.asText() in used })
}
