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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@DisplayName("`Cli` should")
internal class CliSpec {

    @TempDir
    lateinit var workingFolder: File

    @Test
    fun `return the output as the command wrote it, line breaks included`() {
        // `printf` turns the `\n` sequences of its format into line breaks.
        Cli(workingFolder).execute("printf", "one\\ntwo\\n") shouldBe "one\ntwo\n"
    }

    @Test
    fun `run a command assembled as a list`() {
        Cli(workingFolder).execute(listOf("printf", "%s", "one")) shouldBe "one"
    }

    @Test
    fun `fail with the exit code and the error output of a failed command`() {
        val error = shouldThrow<IllegalStateException> {
            Cli(workingFolder).execute("sh", "-c", "echo 'went wrong' >&2; exit 3")
        }

        error.message shouldContain "exit code 3"
        error.message shouldContain "went wrong"
    }
}
