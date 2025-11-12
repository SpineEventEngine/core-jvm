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
