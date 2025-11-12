/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.dependency.lib

import io.spine.dependency.Dependency
import io.spine.dependency.DependencyWithBom

// https://github.com/FasterXML/jackson/wiki/Jackson-Releases
@Suppress("unused")
object Jackson : DependencyWithBom() {
    override val group = "com.fasterxml.jackson"
    override val version = "2.20.0"

    // https://github.com/FasterXML/jackson-annotations?tab=readme-ov-file#release-notes
    const val annotationsVersion = "2.20"

    // https://github.com/FasterXML/jackson-bom
    override val bom = "$group:jackson-bom:$version"

    private val groupPrefix = group
    private val coreGroup = "$groupPrefix.core"
    private val moduleGroup = "$groupPrefix.module"

    // Constants coming below without `$version` are covered by the BOM.

    // https://github.com/FasterXML/jackson-core
    val core = "$coreGroup:jackson-core"

    // https://github.com/FasterXML/jackson-databind
    val databind = "$coreGroup:jackson-databind"

    // https://github.com/FasterXML/jackson-annotations
    val annotations = "$coreGroup:jackson-annotations:$annotationsVersion"

    // https://github.com/FasterXML/jackson-module-kotlin/releases
    val moduleKotlin = "$moduleGroup:jackson-module-kotlin"

    // https://github.com/FasterXML/jackson-modules-java8
    val moduleParameterNames = "$moduleGroup:jackson-module-parameter-names"

    override val modules = listOf(
        core,
        databind,
        moduleKotlin,
        moduleParameterNames
    )

    object DataFormat : Dependency() {
        override val version = Jackson.version
        override val group = "$groupPrefix.dataformat"

        private const val infix = "jackson-dataformat"

        // https://github.com/FasterXML/jackson-dataformat-xml/releases
        val xml = "$group:$infix-xml"

        // https://github.com/FasterXML/jackson-dataformats-text/releases
        val yaml = "$group:$infix-yaml"

        val xmlArtifact = "$xml:$version"
        val yamlArtifact = "$yaml:$version"

        override val modules = listOf(xml, yaml)
    }

    object DataType : Dependency() {
        override val version = Jackson.version
        override val group = "$groupPrefix.datatype"

        private const val infix = "jackson-datatype"

        // https://github.com/FasterXML/jackson-modules-java8
        val jdk8 = "$group:$infix-jdk8"

        // https://github.com/FasterXML/jackson-modules-java8/tree/2.19/datetime
        val dateTime = "$group:$infix-jsr310"

        // https://github.com/FasterXML/jackson-datatypes-collections/blob/2.19/guava
        val guava = "$group:$infix-guava"

        // https://github.com/FasterXML/jackson-dataformats-binary/tree/2.19/protobuf
        val protobuf = "$group:$infix-protobuf"

        // https://github.com/FasterXML/jackson-datatypes-misc/tree/2.19/javax-money
        val javaXMoney = "$group:$infix-javax-money"

        // https://github.com/FasterXML/jackson-datatypes-misc/tree/2.19/moneta
        val moneta = "$group:jackson-datatype-moneta"

        override val modules = listOf(
            jdk8,
            dateTime,
            guava,
            protobuf,
            javaXMoney,
            moneta
        )
    }

    // https://github.com/FasterXML/jackson-jr
    object Junior : Dependency() {
        override val version = Jackson.version
        override val group = "com.fasterxml.jackson.jr"

        val objects = "$group:jackson-jr-objects"

        override val modules = listOf(objects)
    }
}
