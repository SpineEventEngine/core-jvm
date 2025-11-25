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

package io.spine.gradle.github.pages

import io.spine.gradle.git.Branch
import io.spine.gradle.git.Repository
import io.spine.gradle.git.UserInfo
import io.spine.gradle.repo.RepoSlug
import org.gradle.api.logging.Logger

/**
 * Clones the current project repository with the branch dedicated to publishing
 * documentation to GitHub Pages checked out.
 *
 * The repository's GitHub SSH URL is derived from the `REPO_SLUG` environment
 * variable. The [branch][Branch.documentation] dedicated to publishing documentation
 * is automatically checked out in this repository. Also, the username and the email
 * of the git user are automatically configured.
 *
 * The username is set to `"UpdateGitHubPages Plugin"`, and the email is derived from
 * the `FORMAL_GIT_HUB_PAGES_AUTHOR` environment variable.
 *
 * @throws org.gradle.api.GradleException if any of the environment variables described above
 *         is not set.
 */
internal fun Repository.Factory.forPublishingDocumentation(logger: Logger): Repository {
    val host = RepoSlug.fromVar().gitHost()

    val username = "UpdateGitHubPages Plugin"
    val userEmail = AuthorEmail.fromVar().toString()
    val user = UserInfo(username, userEmail)

    val branch = Branch.documentation

    return clone(host, user, branch, logger)
}
