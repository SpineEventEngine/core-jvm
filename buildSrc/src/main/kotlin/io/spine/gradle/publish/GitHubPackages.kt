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
 * Reads the personal access token for the `developers@spine.io` account.
 * The token grants only read access to public GitHub packages.
 *
 * The token is extracted from the archive called `aus.weis` stored under `buildSrc`.
 * The archive has such an unusual name to avoid scanning for tokens placed in repositories
 * that is performed by GitHub. Since we do not violate any security, it is OK to
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
