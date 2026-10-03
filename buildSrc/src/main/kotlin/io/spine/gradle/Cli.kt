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
import java.util.*

/**
 * Executes a process from Gradle code.
 *
 * Uses the passed [workingFolder] as the directory in which the commands are executed.
 */
class Cli(private val workingFolder: File) {

    /**
     * Executes the given terminal command and retrieves the command output.
     *
     * [Executes][Runtime.exec] the given `String` array as a CLI command.
     *
     * If the execution is successful, returns the command output.
     * Throws an [IllegalStateException] otherwise.
     *
     * @param command the command to execute.
     * @return the command line output.
     * @throws IllegalStateException if the execution fails.
     */
    fun execute(vararg command: String): String {
        val outWriter = StringWriter()
        val errWriter = StringWriter()

        val process = ProcessBuilder(*command).apply {
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
                    "$nl$errWriter" +
                    "$nl$outWriter."
            throw IllegalStateException(errorMsg)
        }
    }
}

/**
 * Starts a background thread that reads all lines from this [InputStream] and
 * appends them to [dest], returning the thread so the caller can [join][Thread.join]
 * it once the process has exited, ensuring the buffer holds the complete output.
 */
private fun InputStream.pourTo(dest: StringWriter): Thread =
    Thread {
        val sc = Scanner(this)
        while (sc.hasNextLine()) {
            dest.append(sc.nextLine())
        }
    }.also { it.start() }
