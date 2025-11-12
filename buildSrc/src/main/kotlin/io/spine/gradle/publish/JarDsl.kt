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

package io.spine.gradle.publish

/**
 * A DSL element of [SpinePublishing] extension which configures publishing of
 * [dokkaKotlinJar] artifact.
 *
 * This artifact contains Dokka-generated documentation. By default, it is not published.
 *
 * Take a look at the [SpinePublishing.dokkaJar] for a usage example.
 *
 * @see [artifacts]
 */
class DokkaJar {
    /**
     * Enables publishing `JAR`s with Dokka-generated documentation for all published modules.
     */
    @Suppress("unused")
    @Deprecated("Please use `kotlin` and `java` flags instead.")
    var enabled = false

    /**
     * Controls whether [dokkaKotlinJar] artifact should be published.
     * The default value is `true`.
     */
    var kotlin = true

    /**
     * Controls whether [dokkaJavaJar] artifact should be published.
     * The default value is `false`.
     */
    var java = false
}

/**
 * A DSL element of [SpinePublishing] extension which allows enabling publishing
 * of [testJar] artifact.
 *
 * This artifact contains compilation output of `test` source set. By default, it is not published.
 *
 * Take a look on [SpinePublishing.testJar] for a usage example.

 * @see [artifacts]
 */
class TestJar {

    /**
     * Set of modules, for which a test JAR will be published.
     */
    var inclusions: Set<String> = emptySet()

    /**
     * Enables test JAR publishing for all published modules.
     */
    var enabled = false
}

/**
 * A DSL element of [SpinePublishing] extension which allows disabling publishing
 * of [protoJar] artifact.
 *
 * This artifact contains all the `.proto` definitions from `sourceSets.main.proto`. By default,
 * it is published.
 *
 * Take a look on [SpinePublishing.protoJar] for a usage example.
 *
 * @see [artifacts]
 */
class ProtoJar {

    /**
     * Set of modules, for which a proto JAR will not be published.
     */
    var exclusions: Set<String> = emptySet()

    /**
     * Disables proto JAR publishing for all published modules.
     */
    var disabled = false
}

/**
 * Flags for turning optional JAR artifacts in a project.
 */
internal data class JarFlags(

    /**
     * Tells whether [sourcesJar] artifact should be published.
     *
     * Default value is `true`.
     */
    val sourcesJar: Boolean = true,

    /**
     * Tells whether [javadocJar] artifact should be published.
     *
     * Default value is `true`.
     */
    val javadocJar: Boolean = true,
    
    /**
     * Tells whether [protoJar] artifact should be published.
     */
    val publishProtoJar: Boolean,

    /**
     * Tells whether [testJar] artifact should be published.
     */
    val publishTestJar: Boolean,

    /**
     * Tells whether [dokkaKotlinJar] artifact should be published.
     */
    val publishDokkaKotlinJar: Boolean,

    /**
     * Tells whether [dokkaJavaJar] artifact should be published.
     */
    val publishDokkaJavaJar: Boolean
) {
    internal companion object {
        /**
         * Creates an instance of [JarFlags] for the project with the given name,
         * taking the setup parameters from JAR DSL elements.
         */
        fun create(
            projectName: String,
            protoJar: ProtoJar,
            testJar: TestJar,
            dokkaJar: DokkaJar
        ): JarFlags {
            val addProtoJar = (protoJar.exclusions.contains(projectName) || protoJar.disabled).not()
            val addTestJar = testJar.inclusions.contains(projectName) || testJar.enabled
            return JarFlags(
                sourcesJar = true,
                javadocJar = true,
                addProtoJar, addTestJar,
                dokkaJar.kotlin, dokkaJar.java
            )
        }
    }
}
