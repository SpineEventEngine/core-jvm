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
