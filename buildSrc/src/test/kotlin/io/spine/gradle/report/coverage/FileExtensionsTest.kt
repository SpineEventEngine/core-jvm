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

package io.spine.gradle.report.coverage

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.io.File
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@DisplayName("`File.classNamesIn` should")
class FileExtensionsTest {

    @TempDir
    lateinit var sourceRoot: File

    @Nested
    inner class `for Java sources` {

        @Test
        fun `return a single FQN`() {
            val file = sourceRoot.touch("io/spine/example/Foo.java")

            file.classNamesIn(sourceRoot) shouldBe listOf("io.spine.example.Foo")
        }

        @Test
        fun `handle files placed directly under the source root`() {
            val file = sourceRoot.touch("Top.java")

            file.classNamesIn(sourceRoot) shouldBe listOf("Top")
        }
    }

    @Nested
    inner class `for Kotlin sources` {

        @Test
        fun `return both the declared class and the synthetic file class`() {
            val file = sourceRoot.touch("io/spine/example/Foo.kt")

            file.classNamesIn(sourceRoot) shouldContainExactlyInAnyOrder listOf(
                "io.spine.example.Foo",
                "io.spine.example.FooKt"
            )
        }

        @Test
        fun `handle the 'Kt'-suffixed file names emitted by 'protoc-gen-kotlin'`() {
            val file = sourceRoot.touch("io/spine/example/ValidationErrorKt.kt")

            file.classNamesIn(sourceRoot) shouldContainExactlyInAnyOrder listOf(
                "io.spine.example.ValidationErrorKt",
                "io.spine.example.ValidationErrorKtKt"
            )
        }
    }

    @Nested
    inner class `for proto-file-scoped Kotlin sources` {

        @Test
        fun `strip the two-part 'proto-kt' suffix`() {
            val file = sourceRoot.touch("io/spine/example/ValidationErrorProtoKt.proto.kt")

            file.classNamesIn(sourceRoot) shouldContainExactlyInAnyOrder listOf(
                "io.spine.example.ValidationErrorProtoKt",
                "io.spine.example.ValidationErrorProtoKtKt"
            )
        }
    }

    @Nested
    inner class `for unsupported inputs` {

        @Test
        fun `return an empty list for non-source files`() {
            val file = sourceRoot.touch("io/spine/example/notes.txt")

            file.classNamesIn(sourceRoot) shouldBe emptyList()
        }

        @Test
        fun `return an empty list for files outside the source root`() {
            val outsideRoot = File(sourceRoot.parentFile, "outside-${System.nanoTime()}")
            try {
                val file = outsideRoot.touch("io/spine/example/Foo.java")

                file.classNamesIn(sourceRoot) shouldBe emptyList()
            } finally {
                outsideRoot.deleteRecursively()
            }
        }
    }
}

private fun File.touch(relativePath: String): File {
    val file = this.resolve(relativePath)
    file.parentFile.mkdirs()
    file.createNewFile()
    return file
}
