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

package io.spine.dependency.lib

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Protobuf` should")
internal class ProtobufSpec {

    @Test
    fun `declare the coordinates of its Java and Kotlin libraries`() {
        assertSoftly(Protobuf) {
            javaLib shouldBe "$group:protobuf-java:$version"
            javaUtil shouldBe "$group:protobuf-java-util:$version"
            kotlin shouldBe "$group:protobuf-kotlin:$version"
        }
    }

    @Test
    fun `list its Java and Kotlin libraries`() {
        with(Protobuf) {
            libs shouldContainExactly listOf(javaLib, javaUtil, kotlin)
        }
    }
}
