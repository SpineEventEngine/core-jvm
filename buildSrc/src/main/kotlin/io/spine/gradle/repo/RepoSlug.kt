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

import org.gradle.api.GradleException

/**
 * A name of a repository.
 */
@Suppress("unused")
class RepoSlug(val value: String) {

    companion object {

        /**
         * The name of the environment variable containing the repository slug, for which
         * the Gradle build is performed.
         */
        private const val environmentVariable = "REPO_SLUG"

        /**
         * Reads `REPO_SLUG` environment variable and returns its value.
         *
         * In case it is not set, a [org.gradle.api.GradleException] is thrown.
         */
        fun fromVar(): RepoSlug {
            val envValue = System.getenv(environmentVariable)
            if (envValue.isNullOrEmpty()) {
                throw GradleException("`$environmentVariable` environment variable is not set.")
            }
            return RepoSlug(envValue)
        }
    }

    override fun toString(): String = value

    /**
     * Returns the GitHub URL to the project repository.
     */
    fun gitHost(): String {
        return "git@github-publish:${value}.git"
    }
}
