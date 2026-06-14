/*
 * Copyright 2026, TeamDev. All rights reserved.
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
