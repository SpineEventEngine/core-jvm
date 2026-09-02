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
        val databind = "$group:jackson-databind"

        override val modules = listOf(core, databind)
    }

    object DataType : Dependency() {
        override val version = JacksonV2.version
        override val group = "$groupPrefix.datatype"

        val jdk8 = "$group:jackson-datatype-jdk8"
        val jsr310 = "$group:jackson-datatype-jsr310"
        val guava = "$group:jackson-datatype-guava"

        override val modules = listOf(jdk8, jsr310, guava)
    }

    object DataFormat : Dependency() {
        override val version = JacksonV2.version
        override val group = "$groupPrefix.dataformat"

        val xml = "$group:jackson-dataformat-xml"
        val yaml = "$group:jackson-dataformat-yaml"
        val protobuf = "$group:jackson-dataformat-protobuf"

        override val modules = listOf(xml, yaml, protobuf)
    }

    object Module : Dependency() {
        override val version = JacksonV2.version
        override val group = "$groupPrefix.module"

        val parameterNames = "$group:jackson-module-parameter-names"

        // https://github.com/FasterXML/jackson-module-kotlin/releases
        val kotlin = "$group:jackson-module-kotlin"

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
