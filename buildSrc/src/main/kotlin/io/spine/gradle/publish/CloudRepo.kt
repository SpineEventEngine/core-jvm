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

import io.spine.gradle.repo.Repository

/**
 * CloudRepo Maven repository.
 *
 * There is a special treatment for this repository. Usually, fetching and publishing of artifacts
 * is performed via the same URL. But it is not true for CloudRepo. Fetching is performed via
 * the public repository and publishing via the private one. Their URLs differ in `/public` infix.
 */
@Deprecated(message = "Please use `PublishingRepos.cloudArtifactRegistry` instead.")
internal object CloudRepo {

    private const val name = "CloudRepo"
    private const val credentialsFile = "cloudrepo.properties"
    private const val publicUrl = "https://spine.mycloudrepo.io/public/repositories"
    private val privateUrl = publicUrl.replace("/public", "")

    /**
     * CloudRepo repository for fetching of artifacts.
     *
     * Use this instance to depend on artifacts from this repository.
     */
    val published = Repository(
        name = name,
        releases = "$publicUrl/releases",
        snapshots = "$publicUrl/snapshots",
        credentialsFile = credentialsFile
    )

    /**
     * CloudRepo repository for publishing of artifacts.
     *
     * Use this instance to push new artifacts to this repository.
     */
    val destination = Repository(
        name = name,
        releases = "$privateUrl/releases",
        snapshots = "$privateUrl/snapshots",
        credentialsFile = credentialsFile
    )
}
