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
                throw GradleException("`REPO_SLUG` environment variable is not set.")
            }
            return RepoSlug(envValue)
        }
    }

    override fun toString(): String = value

    /**
     * Returns the GitHub URL to the project repository.
     */
    fun gitHost(): String {
        return "git@github.com-publish:${value}.git"
    }
}
