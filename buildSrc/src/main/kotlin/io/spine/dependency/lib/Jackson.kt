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
 * Jackson library dependencies.
 *
 * Jackson 3.x uses the `tools.jackson` group ID and the matching `tools.jackson.*`
 * packages ([JSTEP-1](https://github.com/FasterXML/jackson-future-ideas/wiki/JSTEP-1)).
 * The sole exception is `jackson-annotations`: Jackson 3.x keeps
 * consuming the 2.x artifact, so both its coordinates and its
 * `com.fasterxml.jackson.annotation` package stay unchanged.
 *
 * The Jackson 2.x artifacts, which some of our dependencies still consume,
 * are declared by [JacksonV2].
 *
 * See:
 *  - [Jackson Releases](https://github.com/FasterXML/jackson/wiki/Jackson-Releases)
 *  - [Migrating to Jackson 3](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md)
 */
@Suppress("unused", "ConstPropertyName")
object Jackson : DependencyWithBom() {
    override val group = "tools.jackson"
    override val version = "3.2.3"

    /**
     * The version of `jackson-annotations`, which Jackson 3.x deliberately keeps
     * on the 2.x line.
     *
     * Must match the `jackson.version.annotations` property declared by the [JacksonV2.bom].
     *
     * See: https://github.com/FasterXML/jackson-annotations?tab=readme-ov-file#release-notes
     */
    const val annotationsVersion = "2.22"

    // https://github.com/FasterXML/jackson-bom
    override val bom = "$group:jackson-bom:$version"

    private val groupPrefix = group
    private val coreGroup = "$groupPrefix.core"
    private val moduleGroup = "$groupPrefix.module"

    // Constants coming below without `$version` are covered by the BOM.

    // https://github.com/FasterXML/jackson-core
    val core = "$coreGroup:jackson-core"

    /** Returns the coordinates of [core] with the [version]. */
    fun core(): String = artifact(core)

    // https://github.com/FasterXML/jackson-databind
    val databind = "$coreGroup:jackson-databind"

    /** Returns the coordinates of [databind] with the [version]. */
    fun databind(): String = artifact(databind)

    // https://github.com/FasterXML/jackson-annotations
    val annotations = "com.fasterxml.jackson.core:jackson-annotations:$annotationsVersion"

    // https://github.com/FasterXML/jackson-module-kotlin/releases
    val moduleKotlin = "$moduleGroup:jackson-module-kotlin"

    /** Returns the coordinates of [moduleKotlin] with the [version]. */
    fun moduleKotlin(): String = artifact(moduleKotlin)

    @Deprecated(
        "The module was merged into `jackson-databind` in Jackson 3.0" +
                " and is no longer published.",
        ReplaceWith("Jackson.databind", "io.spine.dependency.lib.Jackson"),
        level = DeprecationLevel.ERROR
    )
    val moduleParameterNames = "$moduleGroup:jackson-module-parameter-names"

    override val modules = listOf(
        core,
        databind,
        moduleKotlin
    )

    object DataFormat : Dependency() {
        override val version = Jackson.version
        override val group = "$groupPrefix.dataformat"

        private const val infix = "jackson-dataformat"

        // https://github.com/FasterXML/jackson-dataformat-xml/releases
        val xml = "$group:$infix-xml"

        /** Returns the coordinates of [xml] with the [version]. */
        fun xml(): String = artifact(xml)

        // https://github.com/FasterXML/jackson-dataformats-text/releases
        val yaml = "$group:$infix-yaml"

        /** Returns the coordinates of [yaml] with the [version]. */
        fun yaml(): String = artifact(yaml)

        // https://github.com/FasterXML/jackson-dataformats-binary/tree/3.x/protobuf
        val protobuf = "$group:$infix-protobuf"

        /** Returns the coordinates of [protobuf] with the [version]. */
        fun protobuf(): String = artifact(protobuf)

        val xmlArtifact = "$xml:$version"
        val yamlArtifact = "$yaml:$version"

        override val modules = listOf(xml, yaml, protobuf)
    }

    object DataType : Dependency() {
        override val version = Jackson.version
        override val group = "$groupPrefix.datatype"

        private const val infix = "jackson-datatype"

        @Deprecated(
            "The module was merged into `jackson-databind` in Jackson 3.0" +
                    " and is no longer published.",
            ReplaceWith("Jackson.databind", "io.spine.dependency.lib.Jackson"),
            level = DeprecationLevel.ERROR
        )
        val jdk8 = "$group:$infix-jdk8"

        @Deprecated(
            "The module was merged into `jackson-databind` in Jackson 3.0" +
                    " and is no longer published.",
            ReplaceWith("Jackson.databind", "io.spine.dependency.lib.Jackson"),
            level = DeprecationLevel.ERROR
        )
        val dateTime = "$group:$infix-jsr310"

        // https://github.com/FasterXML/jackson-datatypes-collections/tree/3.x/guava
        val guava = "$group:$infix-guava"

        /** Returns the coordinates of [guava] with the [version]. */
        fun guava(): String = artifact(guava)

        @Deprecated(
            "Protobuf support is a data format, not a data type." +
                    " The `$infix-protobuf` artifact has never been published.",
            ReplaceWith("Jackson.DataFormat.protobuf", "io.spine.dependency.lib.Jackson"),
            level = DeprecationLevel.ERROR
        )
        val protobuf = "$group:$infix-protobuf"

        // https://github.com/FasterXML/jackson-datatypes-misc/tree/3.x/javax-money
        val javaXMoney = "$group:$infix-javax-money"

        /** Returns the coordinates of [javaXMoney] with the [version]. */
        fun javaXMoney(): String = artifact(javaXMoney)

        // https://github.com/FasterXML/jackson-datatypes-misc/tree/3.x/moneta
        val moneta = "$group:$infix-moneta"

        /** Returns the coordinates of [moneta] with the [version]. */
        fun moneta(): String = artifact(moneta)

        override val modules = listOf(
            guava,
            javaXMoney,
            moneta
        )
    }

    // https://github.com/FasterXML/jackson-jr/tree/3.x
    object Junior : Dependency() {
        override val version = Jackson.version
        override val group = "$groupPrefix.jr"

        val objects = "$group:jackson-jr-objects"

        override val modules = listOf(objects)
    }
}
