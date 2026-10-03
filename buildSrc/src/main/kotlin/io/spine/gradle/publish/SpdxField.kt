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

/**
 * The names of the fields of an SPDX 2.3 JSON document that the SBOM published with
 * an artifact is written by.
 *
 * @see PublicationSbomTask
 */
@Suppress("ConstPropertyName") // https://bit.ly/kotlin-prop-names
internal object SpdxField {
    const val name = "name"
    const val documentNamespace = "documentNamespace"
    const val packages = "packages"
    const val relationships = "relationships"
    const val hasExtractedLicensingInfos = "hasExtractedLicensingInfos"
    const val licenseId = "licenseId"
    const val spdxId = "SPDXID"
    const val sourceInfo = "sourceInfo"
    const val versionInfo = "versionInfo"
    const val licenseConcluded = "licenseConcluded"
    const val licenseDeclared = "licenseDeclared"
    const val licenseInfoFromFiles = "licenseInfoFromFiles"
    const val externalRefs = "externalRefs"
    const val referenceCategory = "referenceCategory"
    const val referenceLocator = "referenceLocator"
    const val referenceType = "referenceType"
    const val spdxElementId = "spdxElementId"
    const val relationshipType = "relationshipType"
    const val relatedSpdxElement = "relatedSpdxElement"
}
