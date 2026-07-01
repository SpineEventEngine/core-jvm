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

package io.spine.gradle.fs

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`LazyTempPath` should")
class LazyTempPathSpec {

    @Test
    fun `create the directory on the first use`() {
        val directory = LazyTempPath("created").toFile()

        directory.exists() shouldBe true
        directory.isDirectory shouldBe true
    }

    @Test
    fun `create the directory under the system temporary directory`() {
        val path = LazyTempPath("under-tmp").toString()

        path shouldContain systemTempDir()
    }

    @Test
    fun `create the directory under a folder named after its package`() {
        val path = LazyTempPath("under-base").toString()

        path shouldContain LazyTempPath::class.java.packageName
    }

    @Test
    fun `place all instances under the same base directory`() {
        val first = LazyTempPath("first").toFile()
        val second = LazyTempPath("second").toFile()

        first.parentFile shouldBe second.parentFile
        first.parentFile.toString() shouldBe SpineTempDir.path.toString()
    }
}

private fun systemTempDir(): String = System.getProperty("java.io.tmpdir")
