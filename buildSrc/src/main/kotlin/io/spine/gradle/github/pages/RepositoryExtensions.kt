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

package io.spine.gradle.github.pages

import io.spine.gradle.git.Branch
import io.spine.gradle.git.Repository
import io.spine.gradle.git.UserInfo
import io.spine.gradle.repo.RepoSlug
import org.gradle.api.Project

/**
 * Clones the branch of the current project repository dedicated to publishing
 * documentation to GitHub Pages.
 *
 * The repository's GitHub SSH URL is derived from the `REPO_SLUG` environment
 * variable. The [branch][Branch.documentation] dedicated to publishing documentation
 * is cloned [sparsely][Repository.Factory.cloneSparsely]: its tip holds the documentation
 * of every published version of every module, while a publication touches the folders
 * of one module only; the caller [checks them out][Repository.checkOutOnly]. The branch
 * is created if it does not exist yet. A freshly created branch is seeded with a `CNAME`
 * file so that GitHub Pages serves the documentation under the `spine.io` custom domain.
 * Also, the username and the email of the Git user are automatically configured.
 *
 * The username is set to `"PublishApiDocs Plugin"`, and the email is derived from
 * the `FORMAL_GIT_HUB_PAGES_AUTHOR` environment variable.
 *
 * @throws org.gradle.api.GradleException if any of the environment variables described above
 *         is not set.
 */
internal fun Repository.Factory.forPublishingDocumentation(project: Project): Repository {
    val host = RepoSlug.fromVar().gitHost()

    val username = "PublishApiDocs Plugin"
    val userEmail = AuthorEmail.fromVar().toString()
    val user = UserInfo(username, userEmail)

    val branch = Branch.documentation

    // When the `gh-pages` branch is created from scratch, seed it with a `CNAME`
    // file so that GitHub Pages serves the documentation under the `spine.io`
    // custom domain.
    val initialFiles = mapOf("CNAME" to "spine.io\n")

    return cloneSparsely(
        project,
        sshUrl = host,
        user = user,
        branch = branch,
        initialFiles = initialFiles
    )
}
