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

package io.spine.gradle.publish

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.gradle.api.logging.Logging
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Verifies how [ArtifactComponents] rewrite the relationships of an SPDX document.
 *
 * The documents are written by hand, in the shape the SPDX Gradle Plugin gives them:
 * the document describes the package of the artifact — the `root` — and each package is
 * listed under the package it was found through. A relationship is written as
 * `"<element> <type> <related element>"`, with the `SPDXRef-` prefix left out.
 */
@DisplayName("`ArtifactComponents` should")
internal class ArtifactComponentsSpec {

    @Nested
    inner class `describe a bundled package` {

        @Test
        fun `as contained in the artifact rather than a dependency of it`() {
            val document = sbom(
                "root DEPENDS_ON a",
                packages = listOf(project("a", path = ":a"))
            )

            ArtifactComponents(bundled = setOf(ComponentKey.ofProject(":a"))).describeIn(document)

            document.relationships() shouldContainExactlyInAnyOrder listOf(
                DESCRIBES_ROOT,
                "root CONTAINS a",
            )
        }

        @Test
        fun `found below another one, keeping their dependency`() {
            val document = sbom(
                "root DEPENDS_ON a",
                "a DEPENDS_ON b",
                packages = listOf(module("a"), module("b"))
            )

            ArtifactComponents(bundled = keys("a", "b")).describeIn(document)

            document.relationships() shouldContainExactlyInAnyOrder listOf(
                DESCRIBES_ROOT,
                "root CONTAINS a",
                "a DEPENDS_ON b",
                "root CONTAINS b",
            )
        }

        @Test
        fun `whose only parent is left out`() {
            val document = sbom(
                "root DEPENDS_ON x",
                "x DEPENDS_ON b",
                packages = listOf(module("x"), module("b"))
            )

            ArtifactComponents(bundled = keys("b")).describeIn(document)

            document.packageIds() shouldContainExactly listOf(ROOT_ID, "b")
            document.relationships() shouldContainExactlyInAnyOrder listOf(
                DESCRIBES_ROOT,
                "root CONTAINS b",
            )
        }

        /**
         * The plugin describes a component resolved to several files as a package
         * containing a package per file. All of them belong to the same component.
         */
        @Test
        fun `made of several files through the package of the component`() {
            val document = sbom(
                "root DEPENDS_ON c",
                *filesOfComponent,
                packages = componentOfTwoFiles()
            )

            ArtifactComponents(bundled = keys("c")).describeIn(document)

            document.relationships() shouldContainExactlyInAnyOrder listOf(
                DESCRIBES_ROOT,
                "root CONTAINS c",
                *filesOfComponent,
            )
        }

        @Test
        fun `that the artifact already contains, writing the containment once`() {
            val document = sbom(
                "root DEPENDS_ON a",
                "root CONTAINS a",
                packages = listOf(module("a"))
            )

            ArtifactComponents(bundled = keys("a")).describeIn(document)

            document.relationships() shouldContainExactly listOf(DESCRIBES_ROOT, "root CONTAINS a")
        }

        /**
         * A component bundled into the artifact, and also a dependency of a dependency,
         * reaches the consumers of the artifact twice. The SBOM tells both.
         */
        @Test
        fun `that a dependency also depends on`() {
            val document = sbom(
                "root DEPENDS_ON d",
                "d DEPENDS_ON g",
                packages = listOf(module("d"), module("g"))
            )

            ArtifactComponents(dependencies = keys("d", "g"), bundled = keys("g"))
                .describeIn(document)

            document.relationships() shouldContainExactlyInAnyOrder listOf(
                DESCRIBES_ROOT,
                "root DEPENDS_ON d",
                "d DEPENDS_ON g",
                "root CONTAINS g",
            )
        }

        @Test
        fun `other than the artifact itself`() {
            val document = sbom("root DEPENDS_ON a", packages = listOf(module("a")))

            ArtifactComponents(dependencies = keys("a"), bundled = setOf(rootKey))
                .describeIn(document)

            document.relationships() shouldContainExactly
                    listOf(DESCRIBES_ROOT, "root DEPENDS_ON a")
        }
    }

    @Nested
    inner class `keep` {

        @Test
        fun `the dependencies of the artifact as they are`() {
            val document = sbom(
                "root DEPENDS_ON d",
                "d DEPENDS_ON e",
                packages = listOf(module("d"), module("e"))
            )

            ArtifactComponents(dependencies = keys("d", "e")).describeIn(document)

            document.relationships() shouldContainExactly listOf(
                DESCRIBES_ROOT,
                "root DEPENDS_ON d",
                "d DEPENDS_ON e",
            )
        }

        @Test
        fun `a package it cannot identify`() {
            val document = sbom(
                "root DEPENDS_ON u",
                packages = listOf(module("u", name = "unidentified"))
            )

            ArtifactComponents().describeIn(document)

            document.packageIds() shouldContainExactly listOf(ROOT_ID, "u")
            document.relationships() shouldContainExactly
                    listOf(DESCRIBES_ROOT, "root DEPENDS_ON u")
        }
    }

    @Nested
    inner class `leave out` {

        @Test
        fun `a package the artifact neither depends on nor bundles`() {
            val document = sbom(
                "root DEPENDS_ON d",
                "root DEPENDS_ON x",
                packages = listOf(module("d"), module("x"))
            )

            ArtifactComponents(dependencies = keys("d")).describeIn(document)

            document.packageIds() shouldContainExactly listOf(ROOT_ID, "d")
            document.relationships() shouldContainExactly
                    listOf(DESCRIBES_ROOT, "root DEPENDS_ON d")
        }

        @Test
        fun `every file of a component it leaves out`() {
            val document = sbom(
                "root DEPENDS_ON c",
                *filesOfComponent,
                packages = componentOfTwoFiles()
            )

            ArtifactComponents().describeIn(document)

            document.packageIds() shouldContainExactly listOf(ROOT_ID)
            document.relationships() shouldContainExactly listOf(DESCRIBES_ROOT)
        }

        /**
         * The plugin walks each configuration of an SBOM from its root, through the
         * packages it already knows, and lists a package it has not seen yet under the
         * package it was found through. So a component found while looking for the
         * bundled ones can land under a dependency whose own dependencies the artifact
         * narrowed, for example by an exclusion.
         */
        @Test
        fun `a dependency of a dependency that only the bundled content brought`() {
            val document = sbom(
                "root DEPENDS_ON d",
                "d DEPENDS_ON b",
                packages = listOf(module("d"), module("b"))
            )

            ArtifactComponents(dependencies = keys("d"), bundled = keys("b"))
                .describeIn(document)

            document.relationships() shouldContainExactlyInAnyOrder listOf(
                DESCRIBES_ROOT,
                "root DEPENDS_ON d",
                "root CONTAINS b",
            )
        }

        @Test
        fun `the license texts only the packages it leaves out refer to`() {
            val kept = "LicenseRef-gnrtd0"
            val leftOut = "LicenseRef-gnrtd1"
            val document = sbom(
                "root DEPENDS_ON d",
                "root DEPENDS_ON x",
                packages = listOf(
                    module("d", license = "($kept AND Apache-2.0)"),
                    module("x", license = leftOut),
                ),
                licenses = listOf(kept, leftOut)
            )

            ArtifactComponents(dependencies = keys("d")).describeIn(document)

            document.licenseIds() shouldContainExactly listOf(kept)
        }
    }

    @Nested
    inner class `reject a document` {

        @Test
        fun `describing no package`() {
            val document = sbom(packages = emptyList(), describes = emptyList())

            shouldThrow<IllegalArgumentException> {
                ArtifactComponents().describeIn(document)
            }
        }

        @Test
        fun `describing several packages`() {
            val document = sbom(packages = listOf(module("a")), describes = listOf(ROOT_ID, "a"))

            shouldThrow<IllegalArgumentException> {
                ArtifactComponents().describeIn(document)
            }
        }
    }
}

private val mapper = ObjectMapper()

private val logger = Logging.getLogger(ArtifactComponentsSpec::class.java)

/** The path of the project whose artifact the documents describe. */
private const val ROOT_PATH = ":artifact"

/** The ID of the package of the artifact, which the documents describe. */
private const val ROOT_ID = "root"

private val rootKey = ComponentKey.ofProject(ROOT_PATH)

private const val DESCRIBES_ROOT = "DOCUMENT DESCRIBES root"

private const val ID_PREFIX = "SPDXRef-"

private fun ArtifactComponents.describeIn(document: ObjectNode) = describeIn(document, logger)

/**
 * Returns the keys of the modules that [module] creates under the given IDs.
 */
private fun keys(vararg ids: String): Set<ComponentKey> =
    ids.map { ComponentKey.ofModule(group = MODULE_GROUP, name = it, version = MODULE_VERSION) }
        .toSet()

private const val MODULE_GROUP = "com.example"

private const val MODULE_VERSION = "1.0"

/**
 * Creates an SPDX document describing the `root` package, with the given [packages]
 * and [relationships].
 */
private fun sbom(
    vararg relationships: String,
    packages: List<ObjectNode>,
    licenses: List<String> = emptyList(),
    describes: List<String> = listOf(ROOT_ID)
): ObjectNode = mapper.createObjectNode().apply {
    put(SpdxField.spdxId, DOCUMENT_ID)
    putArray(SpdxField.hasExtractedLicensingInfos).apply {
        licenses.forEach { addObject().put(SpdxField.licenseId, it).put("extractedText", it) }
    }
    putArray(SpdxField.packages).apply {
        add(project(ROOT_ID, path = ROOT_PATH))
        packages.forEach(::add)
    }
    putArray(SpdxField.relationships).apply {
        describes.forEach { add(relationship("DOCUMENT DESCRIBES $it")) }
        relationships.forEach { add(relationship(it)) }
    }
}

private fun relationship(text: String): ObjectNode {
    val (element, type, related) = text.split(' ')
    return mapper.createObjectNode()
        .put(SpdxField.spdxElementId, ID_PREFIX + element)
        .put(SpdxField.relationshipType, type)
        .put(SpdxField.relatedSpdxElement, ID_PREFIX + related)
}

/**
 * Creates the packages the plugin writes for the component `c`, resolved to two files:
 * the package of the component, and the package of each file.
 */
private fun componentOfTwoFiles(): List<ObjectNode> = listOf(
    module("c"),
    module("c-main", name = "$MODULE_GROUP:c"),
    module("c-extra", name = "$MODULE_GROUP:c:extra"),
)

/**
 * The relationships of the package of the component `c` to the packages of its files.
 */
private val filesOfComponent = arrayOf("c CONTAINS c-main", "c CONTAINS c-extra")

/**
 * Creates the package the SPDX Gradle Plugin writes for the project with the given [path].
 */
private fun project(id: String, path: String): ObjectNode =
    mapper.createObjectNode()
        .put(SpdxField.spdxId, ID_PREFIX + id)
        .put(SpdxField.name, path.substringAfterLast(':'))
        .put(SpdxField.versionInfo, "1.0.0")
        .put(SpdxField.sourceInfo, "git+https://github.com/example/repo@unknown#$id[$path]")
        .put(SpdxField.licenseDeclared, NO_ASSERTION)

/**
 * Creates the package the SPDX Gradle Plugin writes for a Maven module.
 *
 * The package is named `com.example:<id>` unless another [name] is given.
 */
private fun module(
    id: String,
    name: String = "$MODULE_GROUP:$id",
    license: String = NO_ASSERTION
): ObjectNode =
    mapper.createObjectNode()
        .put(SpdxField.spdxId, ID_PREFIX + id)
        .put(SpdxField.name, name)
        .put(SpdxField.versionInfo, MODULE_VERSION)
        .put(SpdxField.licenseConcluded, NO_ASSERTION)
        .put(SpdxField.licenseDeclared, license)

private const val NO_ASSERTION = "NOASSERTION"

private fun ObjectNode.relationships(): List<String> =
    this[SpdxField.relationships].map { relationship ->
        listOf(SpdxField.spdxElementId, SpdxField.relationshipType, SpdxField.relatedSpdxElement)
            .joinToString(" ") { relationship[it].asText().removePrefix(ID_PREFIX) }
    }

private fun ObjectNode.packageIds(): List<String> =
    this[SpdxField.packages].map { it[SpdxField.spdxId].asText().removePrefix(ID_PREFIX) }

private fun ObjectNode.licenseIds(): List<String> =
    this[SpdxField.hasExtractedLicensingInfos].map { it[SpdxField.licenseId].asText() }
