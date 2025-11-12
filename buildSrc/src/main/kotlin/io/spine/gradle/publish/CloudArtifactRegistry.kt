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

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.artifactregistry.auth.DefaultCredentialProvider
import io.spine.gradle.repo.Credentials
import io.spine.gradle.repo.Repository
import java.io.IOException
import org.gradle.api.Project

/**
 * The experimental Google Cloud Artifact Registry repository.
 *
 * In order to successfully publish into this repository, a service account key is needed.
 * The published must create a service account, grant it the permission to write into
 * Artifact Registry, and generate a JSON key.
 * Then, the key must be placed somewhere on the file system and the environment variable
 * `GOOGLE_APPLICATION_CREDENTIALS` must be set to point at the key file.
 * Once these preconditions are met, publishing becomes possible.
 *
 * Google provides a Gradle plugin for configuring the publishing repository credentials
 * automatically. We achieve the same goal by assembling the credentials manually. We do so
 * in order to fit the Google Cloud Artifact Registry repository into the standard frame of
 * the Maven [Repository]-s. Applying the plugin would take a substantial effort due to the fact
 * that both our publishing scripts and the Google's plugin use `afterEvaluate { }` hooks.
 * Ordering said hooks is a non-trivial operation and the result is usually quite fragile.
 * Thus, we choose to do this small piece of configuration manually.
 */
@Suppress("ConstPropertyName") // https://bit.ly/kotlin-prop-names
internal object CloudArtifactRegistry {

    private const val spineRepoLocation = "https://europe-maven.pkg.dev/spine-event-engine"

    val repository = Repository(
        name = "CloudArtifactRegistry",
        releases = "$spineRepoLocation/releases",
        snapshots = "$spineRepoLocation/snapshots",
        credentialValues = this::fetchGoogleCredentials
    )

    private fun fetchGoogleCredentials(p: Project): Credentials? {
        return try {
            val googleCreds = DefaultCredentialProvider()
            val creds = googleCreds.credential as GoogleCredentials
            creds.refreshIfExpired()
            Credentials("oauth2accesstoken", creds.accessToken.tokenValue)
        } catch (e: IOException) {
            p.logger.info("Unable to fetch credentials for Google Cloud Artifact Registry." +
                    " Reason: '${e.message}'." +
                    " The debug output may contain more details.")
            null
        }
    }
}

