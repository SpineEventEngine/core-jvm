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

import io.spine.dependency.Dependency
import io.spine.dependency.DependencyWithBom

/**
 * Jackson 2.x dependencies.
 *
 * Jackson 2.x artifacts keep the `com.fasterxml.jackson.*` group IDs, unlike
 * Jackson 3.x, which moved to `tools.jackson`
 * ([JSTEP-1](https://github.com/FasterXML/jackson-future-ideas/wiki/JSTEP-1)).
 *
 * We declare the 2.x line to align the versions of the artifacts pulled transitively by third-party
 * dependencies, while our own code uses Jackson 3.x declared by [Jackson].
 *
 * The `jackson-annotations` artifact, although it belongs to the 2.x line, is
 * declared by [Jackson.annotations] because Jackson 3.x keeps consuming it.
 *
 * See:
 *  - [Jackson Releases](https://github.com/FasterXML/jackson/wiki/Jackson-Releases)
 *
 * @see Jackson
 */
@Suppress("unused")
object JacksonV2 : DependencyWithBom() {
    override val group = "com.fasterxml.jackson"
    override val version = "2.22.2"

    // https://github.com/FasterXML/jackson-bom
    override val bom = "$group:jackson-bom:$version"

    private val groupPrefix = group

    /**
     * All Jackson 2.x modules we use are declared by the nested objects,
     * such as [Core] or [DataType].
     */
    override val modules = emptyList<String>()

    object Core : Dependency() {
        override val version = JacksonV2.version
        override val group = "$groupPrefix.core"

        @Suppress("MemberNameEqualsClassName")
        val core = "$group:jackson-core"

        /** Returns the coordinates of [core] with the [version]. */
        @Suppress("MemberNameEqualsClassName")
        fun core(): String = artifact(core)

        val databind = "$group:jackson-databind"

        /** Returns the coordinates of [databind] with the [version]. */
        fun databind(): String = artifact(databind)

        override val modules = listOf(core, databind)
    }

    object DataType : Dependency() {
        override val version = JacksonV2.version
        override val group = "$groupPrefix.datatype"

        val jdk8 = "$group:jackson-datatype-jdk8"

        /** Returns the coordinates of [jdk8] with the [version]. */
        fun jdk8(): String = artifact(jdk8)

        val jsr310 = "$group:jackson-datatype-jsr310"

        /** Returns the coordinates of [jsr310] with the [version]. */
        fun jsr310(): String = artifact(jsr310)

        val guava = "$group:jackson-datatype-guava"

        /** Returns the coordinates of [guava] with the [version]. */
        fun guava(): String = artifact(guava)

        override val modules = listOf(jdk8, jsr310, guava)
    }

    object DataFormat : Dependency() {
        override val version = JacksonV2.version
        override val group = "$groupPrefix.dataformat"

        val xml = "$group:jackson-dataformat-xml"

        /** Returns the coordinates of [xml] with the [version]. */
        fun xml(): String = artifact(xml)

        val yaml = "$group:jackson-dataformat-yaml"

        /** Returns the coordinates of [yaml] with the [version]. */
        fun yaml(): String = artifact(yaml)

        val protobuf = "$group:jackson-dataformat-protobuf"

        /** Returns the coordinates of [protobuf] with the [version]. */
        fun protobuf(): String = artifact(protobuf)

        override val modules = listOf(xml, yaml, protobuf)
    }

    object Module : Dependency() {
        override val version = JacksonV2.version
        override val group = "$groupPrefix.module"

        val parameterNames = "$group:jackson-module-parameter-names"

        /** Returns the coordinates of [parameterNames] with the [version]. */
        fun parameterNames(): String = artifact(parameterNames)

        // https://github.com/FasterXML/jackson-module-kotlin/releases
        val kotlin = "$group:jackson-module-kotlin"

        /** Returns the coordinates of [kotlin] with the [version]. */
        fun kotlin(): String = artifact(kotlin)

        override val modules = listOf(parameterNames, kotlin)
    }

    // https://github.com/FasterXML/jackson-jr/tree/2.x
    object Junior : Dependency() {
        override val version = JacksonV2.version
        override val group = "$groupPrefix.jr"

        val objects = "$group:jackson-jr-objects"

        override val modules = listOf(objects)
    }
}
