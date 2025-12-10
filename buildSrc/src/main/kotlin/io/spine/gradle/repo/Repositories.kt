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

@file:Suppress("TooManyFunctions") // Deprecated functions will be kept for a while.

package io.spine.gradle.repo

import io.spine.gradle.publish.PublishingRepos
import java.net.URI
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.kotlin.dsl.maven

/**
 * Registers the standard set of Maven repositories.
 *
 * To be used in `buildscript` clauses when a fully-qualified call must be made.
 */
@Suppress("unused")
@Deprecated(
    message = "Please use `standardSpineSdkRepositories()`.",
    replaceWith = ReplaceWith("standardSpineSdkRepositories()")
)
fun doApplyStandard(repositories: RepositoryHandler) = repositories.standardToSpineSdk()

/**
 * A scrambled version of PAT generated with the only "read:packages" scope.
 *
 * The scrambling around PAT is necessary because GitHub analyzes commits for the presence
 * of tokens and invalidates them.
 *
 * @see <a href="https://github.com/orgs/community/discussions/25629">
 *     How to make GitHub packages to the public</a>
 */
private object Pat {
    private const val shade = "_phg->8YlN->MFRA->gxIk->HVkm->eO6g->FqHJ->z8MS->H4zC->ZEPq"
    private const val separator = "->"
    private val chunks: Int = shade.split(separator).size - 1

    fun credentials(): Credentials {
        val pass = shade.replace(separator, "").splitAndReverse(chunks, "")
        return Credentials("public", pass)
    }

    /**
     * Splits this string to the chunks, reverses each chunk, and joins them
     * back to a string using the [separator].
     */
    private fun String.splitAndReverse(numChunks: Int, separator: String): String {
        check(length / numChunks >= 2) {
            "The number of chunks is too big. Must be <= ${length / 2}."
        }
        val chunks = chunked(length / numChunks)
        val reversedChunks = chunks.map { chunk -> chunk.reversed() }
        return reversedChunks.joinToString(separator)
    }
}

/**
 * Adds a read-only view to all artifacts of the SpineEventEngine
 * GitHub organization.
 */
fun RepositoryHandler.spineArtifacts(): MavenArtifactRepository = maven {
    url = URI("https://maven.pkg.github.com/SpineEventEngine/*")
    includeSpineOnly()
    val pat = Pat.credentials()
    credentials {
        username = pat.username
        password = pat.password
    }
}

val RepositoryHandler.intellijReleases: MavenArtifactRepository
    get() = maven("https://www.jetbrains.com/intellij-repository/releases")

val RepositoryHandler.intellijDependencies: MavenArtifactRepository
    get() = maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") {
        content {
            includeGroupByRegex("com\\.jetbrains\\.intellij.*")
            includeGroupByRegex("org\\.jetbrains\\.intellij.*")
            includeGroupByRegex("com\\.intellij.*")
        }
    }

/**
 * Applies repositories commonly used by Spine Event Engine projects.
 */
fun RepositoryHandler.standardToSpineSdk() {
    spineArtifacts()

    @Suppress("DEPRECATION") // Still use `CloudRepo` for earlier versions.
    val spineRepos = listOf(
        Repos.spine,
        Repos.spineSnapshots,
        Repos.artifactRegistry,
        Repos.artifactRegistrySnapshots
    )

    spineRepos
        .map { URI(it) }
        .forEach {
            maven {
                url = it
                includeSpineOnly()
            }
        }

    intellijReleases
    intellijDependencies

    maven {
        url = URI(Repos.sonatypeSnapshots)
    }

    mavenCentral()
    gradlePluginPortal()
    mavenLocal().includeSpineOnly()
}

@Deprecated(
    message = "Please use `standardToSpineSdk() instead.",
    replaceWith = ReplaceWith("standardToSpineSdk()")
)
fun RepositoryHandler.applyStandard() = this.standardToSpineSdk()

/**
 * Defines names of additional repositories commonly used in the Spine SDK projects.
 *
 * @see [applyStandard]
 */
@Suppress(
    "DEPRECATION" /* Still need to use `CloudRepo` for older versions. */,
    "ConstPropertyName" // https://bit.ly/kotlin-prop-names
)
private object Repos {
    @Deprecated(message = "Please use `cloudArtifactRegistry.releases` instead.")
    val spine = io.spine.gradle.publish.CloudRepo.published.target(snapshots = false)

    @Deprecated(message = "Please use `artifactRegistry.snapshots` instead.")
    val spineSnapshots = io.spine.gradle.publish.CloudRepo.published.target(snapshots = true)

    val artifactRegistry = PublishingRepos.cloudArtifactRegistry.target(snapshots = false)
    val artifactRegistrySnapshots = PublishingRepos.cloudArtifactRegistry.target(snapshots = true)

    const val sonatypeSnapshots = "https://oss.sonatype.org/content/repositories/snapshots"
}

/**
 * Narrows down the search for this repository to Spine-related artifact groups.
 */
private fun MavenArtifactRepository.includeSpineOnly() {
    content {
        includeGroupByRegex("io\\.spine.*")
    }
}
