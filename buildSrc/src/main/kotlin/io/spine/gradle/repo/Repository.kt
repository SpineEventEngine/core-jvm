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

import java.io.File
import java.util.Properties
import org.gradle.api.Project

/**
 * A Maven repository.
 *
 * @param name The human-readable name which is also used in the publishing task names
 *   for identifying the target repository.
 *   The name must match the [regex].
 * @param releases The URL for publishing release versions of artifacts.
 * @param snapshots The URL for publishing [snapshot][io.spine.gradle.isSnapshot] versions.
 * @param credentialsFile The path to the file which contains the credentials for the registry.
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
