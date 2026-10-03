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

package io.spine.gradle

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem

/**
 * A Gradle task that runs another Gradle build.
 *
 * Launches Gradle wrapper under a given [directory] with the specified [taskNames] names.
 * The `clean` task is also run if the current build includes a `clean` task.
 *
 * The build writes verbose log into `$directory/build/debug-out.txt`.
 * The error output is written into `$directory/build/error-out.txt`.
 */
@Suppress("unused")
open class RunGradle : DefaultTask() {

    companion object {

        /**
         * Default Gradle build timeout.
         */
        private const val BUILD_TIMEOUT_MINUTES: Long = 10
    }

    /**
     * Path to the directory that contains a Gradle wrapper script.
     */
    @Internal
    lateinit var directory: String

    /**
     * The names of the tasks to be passed to the Gradle Wrapper script.
     */
    private lateinit var taskNames: List<String>

    /**
     * For how many minutes to wait for the Gradle build to complete.
     */
    @Internal
    var maxDurationMins: Long = BUILD_TIMEOUT_MINUTES

    /**
     * Names of Gradle properties to copy into the launched build.
     *
     * The properties are looked up in the root project. If a property is not found, it is ignored.
     *
     * See [Gradle doc](https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties)
     * for more info about Gradle properties.
     */
    @Internal
    var includeGradleProperties: MutableSet<String> = mutableSetOf()

    /**
     * Specifies task names to be passed to the Gradle Wrapper script.
     */
    fun task(vararg tasks: String) {
        taskNames = tasks.asList()
    }

    /**
     * Sets the maximum time to wait until the build completion in minutes
     * and specifies task names to be passed to the Gradle Wrapper script.
     */
    fun task(maxDurationMins: Long, vararg tasks: String) {
        taskNames = tasks.asList()
        this.maxDurationMins = maxDurationMins
    }

    @TaskAction
    public fun execute() {
        // Ensure build error output log.
        // Since we're executing this task in another process, we redirect error output to
        // the file under the `_out` directory. Using the `build` directory for this purpose
        // proved to cause problems under Windows when executing the `clean` command, which
        // fails because another process holds files.
        val buildDir = File(directory, "_out")
        if (!buildDir.exists()) {
            buildDir.mkdir()
        }
        val errorOut = File(buildDir, "error-out.txt")
        errorOut.truncate()
        val debugOut = File(buildDir, "debug-out.txt")
        debugOut.truncate()

        val command = buildCommand()
        val process = startProcess(command, errorOut, debugOut)

        /*  The timeout is set because of Gradle process execution under Windows.
            See the following locations for details:
              https://github.com/gradle/gradle/pull/8467#issuecomment-498374289
              https://github.com/gradle/gradle/issues/3987
              https://discuss.gradle.org/t/weirdness-in-gradle-exec-on-windows/13660/6
         */
        val completed = process.waitFor(maxDurationMins, TimeUnit.MINUTES)
        val exitCode = process.exitValue()
        if (!completed || exitCode != 0) {
            val errorOutExists = errorOut.exists()
            if (errorOutExists) {
                logger.error(errorOut.readText())
            }
            throw GradleException("Child build process FAILED." +
                    " Exit code: $exitCode." +
                    if (errorOutExists) " See $errorOut for details."
                    else " $errorOut file was not created."
            )
        }
    }

    private fun buildCommand(): List<String> {
        val script = buildScript()
        val command = mutableListOf<String>()
        command.add("${project.rootDir}/$script")
        val shouldClean = project.gradle
            .taskGraph
            .hasTask(":clean")
        if (shouldClean) {
            command.add("clean")
        }
        command.addAll(taskNames)
        command.add("--console=plain")
        command.add("--debug")
        command.add("--stacktrace")
        command.add("--no-daemon")
        addProperties(command)
        return command
    }

    private fun addProperties(command: MutableList<String>) {
        val rootProject = project.rootProject
        includeGradleProperties
            .filter { rootProject.hasProperty(it) }
            .map { name -> name to rootProject.property(name).toString() }
            .forEach { (name, value) -> command.add("-P$name=$value") }
    }

    private fun buildScript(): String {
        val runsOnWindows = OperatingSystem.current().isWindows
        return if (runsOnWindows) "gradlew.bat" else "gradlew"
    }

    private fun startProcess(command: List<String>, errorOut: File, debugOut: File) =
        ProcessBuilder()
            .command(command)
            .directory(project.file(directory))
            .redirectError(errorOut)
            .redirectOutput(debugOut)
            .start()
}

private fun File.truncate() {
    val stream = FileOutputStream(this)
    stream.use {
        it.channel.truncate(0)
    }
}
