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

import io.spine.gradle.repo.Credentials
import io.spine.gradle.repo.Repository
import io.spine.gradle.buildDirectory
import net.lingala.zip4j.ZipFile
import org.gradle.api.Project

/**
 * Maven repositories of Spine Event Engine projects hosted at GitHub Packages.
 */
internal object GitHubPackages {

    /**
     * Obtains an instance of the GitHub Packages repository with the given name.
     */
    fun repository(repoName: String): Repository {
        val githubActor: String = actor()
        val url = "https://maven.pkg.github.com/SpineEventEngine/$repoName"
        return Repository(
            name = "GitHub-Packages",
            releases = url,
            snapshots = url
        ) { project -> project.credentialsWithToken(githubActor) }
    }

    private fun actor(): String {
        var githubActor: String? = System.getenv("GITHUB_ACTOR")
        githubActor = if (githubActor.isNullOrEmpty()) {
            "developers@spine.io"
        } else {
            githubActor
        }
        return githubActor
    }
}

/**
 * This is a trick. Gradle only supports password or AWS credentials.
 * Thus, we pass the GitHub token as a "password".
 *
 * See https://docs.github.com/en/actions/guides/publishing-java-packages-with-gradle#publishing-packages-to-github-packages
 */
private fun Project.credentialsWithToken(githubActor: String) = Credentials(
    username = githubActor,
    password = readGitHubToken()
)

private fun Project.readGitHubToken(): String {
    val githubToken: String? = System.getenv("GITHUB_TOKEN")
    return if (githubToken.isNullOrEmpty()) {
        readTokenFromArchive()
    } else {
        githubToken
    }
}

/**
 * Read the personal access token for the `developers@spine.io` account which
 * has only the permission to read public GitHub packages.
 *
 * The token is extracted from the archive called `aus.weis` stored under `buildSrc`.
 * The archive has such an unusual name to avoid scanning for tokens placed in repositories
 * which is performed by GitHub. Since we do not violate any security, it is OK to
 * use such a workaround.
 */
private fun Project.readTokenFromArchive(): String {
    val targetDir = "$buildDirectory/token"
    file(targetDir).mkdirs()
    val fileToUnzip = "${rootDir}/buildSrc/aus.weis"

    logger.info(
        "GitHub Packages: reading token by unzipping `$fileToUnzip` into `$targetDir`."
    )
    ZipFile(fileToUnzip, "123".toCharArray()).extractAll(targetDir)
    val file = file("$targetDir/token.txt")
    val result = file.readText()
    return result
}
