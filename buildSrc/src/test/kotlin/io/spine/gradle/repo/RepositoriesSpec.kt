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

package io.spine.gradle.repo

import io.kotest.matchers.collections.shouldContainExactly
import io.spine.gradle.publish.CloudRepo
import io.spine.gradle.publish.PublishingRepos
import java.net.URI
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`standardToSpineSdk()` should")
internal class RepositoriesSpec {

    @Test
    fun `query Spine repositories from Artifact Registry to GitHub Packages`() {
        val project = ProjectBuilder.builder().build()
        project.repositories.standardToSpineSdk()

        val registry = PublishingRepos.cloudArtifactRegistry
        @Suppress("DEPRECATION") // Still queried for earlier versions.
        val cloudRepo = CloudRepo.published
        val spineRepos = listOf(
            registry.target(snapshots = false),
            registry.target(snapshots = true),
            cloudRepo.target(snapshots = false),
            cloudRepo.target(snapshots = true),
            "https://maven.pkg.github.com/SpineEventEngine/*"
        ).map { URI(it) }

        val queried = project.repositories
            .filterIsInstance<MavenArtifactRepository>()
            .map { it.url }
            .filter { it in spineRepos }
        queried shouldContainExactly spineRepos
    }
}
