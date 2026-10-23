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

@file:Suppress("unused")

package io.spine.gradle

import java.io.File
import java.io.InputStream
import java.io.StringWriter
import java.lang.ProcessBuilder.Redirect.PIPE

/**
 * Executes a process from Gradle code.
 *
 * Uses the passed [workingFolder] as the directory in which the commands are executed.
 */
class Cli(private val workingFolder: File) {

    /**
     * Executes the given terminal command and retrieves the command output.
     *
     * Runs the given command, the program followed by its arguments, in
     * the [workingFolder].
     *
     * If the execution is successful, returns the command output.
     * Throws an [IllegalStateException] otherwise.
     *
     * @param command the command to execute.
     * @return the standard output as the command wrote it, line breaks included,
     *   decoded as UTF-8.
     * @throws IllegalStateException if the execution fails.
     */
    fun execute(vararg command: String): String = execute(command.asList())

    /**
     * Executes the given terminal command and retrieves the command output.
     *
     * Works like the `vararg` overload, for a command assembled as a list.
     *
     * @param command the command to execute, the program followed by its arguments.
     * @return the standard output as the command wrote it, line breaks included,
     *   decoded as UTF-8.
     * @throws IllegalStateException if the execution fails.
     */
    fun execute(command: List<String>): String {
        val outWriter = StringWriter()
        val errWriter = StringWriter()

        val process = ProcessBuilder(command).apply {
            directory(workingFolder)
            redirectOutput(PIPE)
            redirectError(PIPE)
        }.start()

        val outReader = process.inputStream!!.pourTo(outWriter)
        val errReader = process.errorStream!!.pourTo(errWriter)
        val exitCode = process.waitFor()
        // `waitFor()` returns on process exit but does not wait for the reader
        // threads to finish draining the pipes; join them so the buffers hold
        // the complete output before it is read below.
        outReader.join()
        errReader.join()

        if (exitCode == 0) {
            return outWriter.toString()
        } else {
            val commandLine = command.joinToString(" ")
            val nl = System.lineSeparator()
            val errorMsg = "Command `$commandLine` finished with exit code $exitCode:" +
                    "$nl${errWriter.toString().trimEnd()}" +
                    "$nl${outWriter.toString().trimEnd()}."
            throw IllegalStateException(errorMsg)
        }
    }
}

/**
 * Starts a background thread that reads all of this [InputStream] and appends it
 * to [dest], returning the thread so the caller can [join][Thread.join] it once
 * the process has exited, ensuring the buffer holds the complete output.
 *
 * The text is appended as the process wrote it, line breaks included, so that
 * a multi-line output can be split into lines.
 */
private fun InputStream.pourTo(dest: StringWriter): Thread =
    Thread {
        bufferedReader().use { dest.append(it.readText()) }
    }.also { it.start() }
