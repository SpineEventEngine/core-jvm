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

package io.spine.gradle.git

/**
 * Contains information about a Git user.
 *
 * Determines the author and committer fields of a commit.
 *
 * @constructor throws an [IllegalArgumentException] if the name or the email
 *              is an empty string.
 */
data class UserInfo(val name: String, val email: String) {
    init {
        require(name.isNotBlank()) { "Name cannot be an empty string." }
        require(email.isNotBlank()) { "Email cannot be an empty string." }
    }
}
