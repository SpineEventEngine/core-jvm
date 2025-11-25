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

import io.spine.gradle.Cli
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

/**
 * Registers the SSH key for further operations with GitHub Pages.
 *
 * @property rootProjectFolder The folder of the project for which we build the documentation.
 * @property logger The logger for placing diagnostic messages of this class.
 */
internal class SshKey(
    private val rootProjectFolder: File,
    private val logger: Logger
) {

    private fun log(message: () -> String) {
        if (logger.isInfoEnabled) {
            logger.info("[SshKey] " + message())
        }
    }

    /**
     * Creates an SSH key with the credentials and registers it by invoking the
     * `register-ssh-key.sh` script.
     */
    fun register() {
        log { "Registering using ${rootProjectFolder.absolutePath}." }
        val gitHubAccessKey = gitHubKey()
        log { "Obtained the key file at ${gitHubAccessKey.absolutePath}." }
        val sshConfigFile = sshConfigFile()
        log { "Located the SSH key file at ${sshConfigFile.absolutePath}." }
        sshConfigFile.appendPublisher(gitHubAccessKey)
        log { "SSH config file appended." }

        execute(
            "${rootProjectFolder.absolutePath}/config/scripts/register-ssh-key.sh",
            gitHubAccessKey.absolutePath
        )
        log { "The SSH key registered." }
    }

    /**
     * Locates `deploy_key_rsa` in the [rootProjectFolder] and returns it as a [File].
     *
     * A CI instance comes with an RSA key. However, of course, the default key has
     * no privileges in Spine repositories. Thus, we add our own RSA key —
     * `deploy_rsa_key`. It must have `write` rights in the associated repository.
     * Also, we don't want that key to be used for anything else but GitHub Pages
     * publishing.
     *
     * Thus, we configure the SSH agent to use the `deploy_rsa_key` only for specific
     * references, namely in `github-publish`.
     *
     * @throws GradleException if `deploy_key_rsa` is not found.
     */
    private fun gitHubKey(): File {
        val gitHubAccessKey = File("${rootProjectFolder.absolutePath}/deploy_key_rsa")

        if (!gitHubAccessKey.exists()) {
            throw GradleException(
                "File $gitHubAccessKey does not exist. It should be encrypted" +
                        " in the repository and decrypted on CI."
            )
        }
        return gitHubAccessKey
    }

    private fun sshConfigFile(): File {
        val sshConfigFile = File("${System.getProperty("user.home")}/.ssh/config")

        if (!sshConfigFile.exists()) {
            val parentDir = sshConfigFile.canonicalFile.parentFile
            parentDir.mkdirs()
            sshConfigFile.createNewFile()
        }

        return sshConfigFile
    }

    private fun File.appendPublisher(privateKey: File) {
        val nl = System.lineSeparator()
        this.appendText(
            nl +
                    "Host github-publish" + nl +
                    "   HostName github.com" + nl +
                    "   User git" + nl +
                    "   IdentityFile ${privateKey.absolutePath}" + nl
        )
    }

    /**
     * Executes a command in the project [rootProjectFolder].
     */
    private fun execute(vararg command: String): String = Cli(rootProjectFolder).execute(*command)
}
