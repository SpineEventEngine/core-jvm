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

import java.io.File
import java.util.Properties
import org.gradle.api.Project

/**
 * A Maven repository.
 *
 * @param name The human-readable name that is also used in the publishing task names
 *   for identifying the target repository.
 *   The name must match the [regex].
 * @param releases The URL for publishing release versions of artifacts.
 * @param snapshots The URL for publishing [snapshot][io.spine.gradle.isSnapshot] versions.
 * @param credentialsFile The path to the file that contains the credentials for the registry.
 * @param credentialValues The function to obtain an instance of [Credentials] from
 *   a Gradle [Project], if [credentialsFile] is not specified.
 */
data class Repository(
    private val name: String,
    private val releases: String,
    private val snapshots: String,
    private val credentialsFile: String? = null,
    private val credentialValues: ((Project) -> Credentials?)? = null
) {

    companion object {
        val regex = Regex("[A-Za-z0-9_\\-.]+")
    }

    init {
        require(regex.matches(name)) {
            "The repository name `$name` does not match the regex `$regex`."
        }
    }

    /**
     * Obtains the name of the repository.
     *
     * The name will be primarily used in the publishing tasks.
     *
     * @param snapshots If `true` this repository is used for publishing snapshots,
     *  and the suffix `-snapshots` will be added to the value of the [name] property.
     *  Otherwise, the function returns just [name].
     */
    fun name(snapshots: Boolean): String = name + if (snapshots) "-snapshots" else ""

    /**
     * Obtains the target URL of the repository for publishing.
     */
    fun target(snapshots: Boolean): String = if (snapshots) this.snapshots else releases

    /**
     * Tells if release and snapshot versions are published to the same destination
     * of this repository.
     */
    fun hasOneTarget() = snapshots == releases

    /**
     * Obtains the publishing password credentials to this repository.
     *
     * If the credentials are represented by a `.properties` file, reads the file and parses
     * the credentials. The file must have properties `user.name` and `user.password`, which store
     * the username and the password for the Maven repository auth.
     */
    fun credentials(project: Project): Credentials? = when {
        credentialValues != null -> credentialValues.invoke(project)
        credentialsFile != null -> credsFromFile(credentialsFile, project)
        else -> throw IllegalArgumentException(
            "Credentials file or a supplier function should be passed."
        )
    }

    private fun credsFromFile(fileName: String, project: Project): Credentials? {
        val file = project.rootProject.file(fileName)
        if (file.exists().not()) {
            return null
        }

        val log = project.logger
        log.info("Using credentials from `$fileName`.")
        val creds = file.parseCredentials()
        log.info("Publishing build as `${creds.username}`.")
        return creds
    }

    private fun File.parseCredentials(): Credentials {
        val properties = Properties().apply { load(inputStream()) }
        val username = properties.getProperty("user.name")
        val password = properties.getProperty("user.password")
        return Credentials(username, password)
    }

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is Repository -> false
        else -> name == other.name &&
           releases == other.releases &&
           snapshots == other.snapshots
}

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + releases.hashCode()
        result = 31 * result + snapshots.hashCode()
        return result
    }

    override fun toString(): String {
        return name
    }
}
