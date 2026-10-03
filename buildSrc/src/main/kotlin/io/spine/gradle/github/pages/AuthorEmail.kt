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

/**
 * An author of updates to GitHub pages.
 */
class AuthorEmail(val value: String) {

    companion object {

        /**
         * The name of the environment variable that contains the email to use for authoring
         * the commits to the GitHub Pages branch.
         */
        @Suppress("MemberVisibilityCanBePrivate") // for documentation purposes.
        const val environmentVariable = "FORMAL_GIT_HUB_PAGES_AUTHOR"

        /**
         * Obtains the author from the system [environment variable][environmentVariable].
         */
        fun fromVar() : AuthorEmail {
            val envValue = System.getenv(environmentVariable)
            check(envValue != null && envValue.isNotBlank()) {
                "Unable to obtain an author from `${environmentVariable}`."
            }
            return AuthorEmail(envValue)
        }
    }

    override fun toString(): String = value
}
