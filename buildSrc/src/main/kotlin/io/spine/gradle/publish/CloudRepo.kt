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
