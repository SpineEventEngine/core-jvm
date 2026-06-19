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

package io.spine.gradle.report.pom

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.StringWriter
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`DependencyWriter` should")
internal class DependencyWriterSpec {

    private val rootProject: Project = ProjectBuilder.builder().withName("root").build()

    /**
     * Creates a subproject of the [rootProject] with the given name.
     *
     * The names of the subprojects in the tests below are chosen so that
     * a module using a dependency in a lower-ranked scope — as defined by
     * [ScopedDependency.dependencyPriority] — sorts first, and is thus
     * encountered first when the dependencies are collected. This way,
     * the tests prove that the merged scope does not depend on the order
     * in which project modules are traversed.
     */
    private fun subproject(name: String): Project =
        ProjectBuilder.builder().withParent(rootProject).withName(name).build()

    /**
     * Declares a dependency with the given [notation] in the named [configuration],
     * creating it if it does not exist.
     */
    private fun Project.declare(configuration: String, notation: String) {
        configurations.maybeCreate(configuration)
        dependencies.add(configuration, notation)
    }

    @Nested inner class
    `merge an artifact duplicated across modules` {

        @Test
        fun `preferring the 'compile' scope over the 'test' one`() {
            subproject("a-tests").declare("testImplementation", SPINE_BASE)
            subproject("b-lib").declare("api", SPINE_BASE)

            val dependency = rootProject.dependencies().single()

            dependency.scopeName() shouldBe DependencyScope.compile.name
        }

        @Test
        fun `preferring the 'runtime' scope over the 'test' one`() {
            subproject("a-tests").declare("testImplementation", SPINE_BASE)
            subproject("b-lib").declare("runtimeOnly", SPINE_BASE)

            val dependency = rootProject.dependencies().single()

            dependency.scopeName() shouldBe DependencyScope.runtime.name
        }

        @Test
        fun `preferring the 'compile' scope over the 'runtime' one`() {
            subproject("a-run").declare("runtimeOnly", SPINE_BASE)
            subproject("b-lib").declare("implementation", SPINE_BASE)

            val dependency = rootProject.dependencies().single()

            dependency.scopeName() shouldBe DependencyScope.compile.name
        }

        @Test
        fun `preferring the 'provided' scope over the 'test' one`() {
            subproject("a-tests").declare("testImplementation", SPINE_BASE)
            subproject("b-lib").declare("compileOnly", SPINE_BASE)

            val dependency = rootProject.dependencies().single()

            dependency.scopeName() shouldBe DependencyScope.provided.name
        }

        @Test
        fun `reporting 'annotationProcessor' and 'testAnnotationProcessor' usages as 'provided'`() {
            subproject("a-tests").declare("testAnnotationProcessor", SPINE_BASE)
            subproject("b-codegen").declare("annotationProcessor", SPINE_BASE)

            val dependency = rootProject.dependencies().single()

            dependency.scopeName() shouldBe DependencyScope.provided.name
        }

        @Test
        fun `preferring the 'compile' scope over the 'provided' one`() {
            subproject("a-tools").declare("compileOnly", SPINE_BASE)
            subproject("b-lib").declare("implementation", SPINE_BASE)

            val dependency = rootProject.dependencies().single()

            dependency.scopeName() shouldBe DependencyScope.compile.name
        }

        @Test
        fun `preferring the 'provided' scope over the 'runtime' one`() {
            subproject("a-run").declare("runtimeOnly", SPINE_BASE)
            subproject("b-tools").declare("compileOnly", SPINE_BASE)

            val dependency = rootProject.dependencies().single()

            dependency.scopeName() shouldBe DependencyScope.provided.name
        }

        @Test
        fun `retaining the newest version with the widest of its scopes`() {
            subproject("a-tests").declare("testImplementation", SPINE_BASE_NEWER)
            subproject("b-lib").declare("api", SPINE_BASE_NEWER)
            subproject("c-old").declare("api", SPINE_BASE)

            val dependency = rootProject.dependencies().single()

            dependency.dependency().version shouldBe "2.0.1"
            dependency.scopeName() shouldBe DependencyScope.compile.name
        }

        @Test
        fun `comparing versions semantically rather than as text`() {
            subproject("a-lib").declare("api", "io.spine:spine-base:9.2.0")
            subproject("b-lib").declare("api", "io.spine:spine-base:10.0.0")

            val dependency = rootProject.dependencies().single()

            dependency.dependency().version shouldBe "10.0.0"
        }

        @Test
        fun `ordering pre-release increments numerically`() {
            subproject("a-old").declare("api", "io.spine:spine-base:2.0.0-SNAPSHOT.99")
            subproject("b-new").declare("api", "io.spine:spine-base:2.0.0-SNAPSHOT.100")

            val dependency = rootProject.dependencies().single()

            dependency.dependency().version shouldBe "2.0.0-SNAPSHOT.100"
        }

        @Test
        fun `preferring a release over its pre-release`() {
            subproject("a-snapshot").declare("api", "io.spine:spine-base:2.0.0-SNAPSHOT.100")
            subproject("b-release").declare("api", SPINE_BASE)

            val dependency = rootProject.dependencies().single()

            dependency.dependency().version shouldBe "2.0.0"
        }

        /**
         * The `api` usage of the older `9.2.0` must affect neither the version
         * nor the scope: both come from the usages of the newest `10.0.0`,
         * which would lose to `9.2.0` in a plain text comparison.
         */
        @Test
        fun `taking the widest scope from the usages of the numerically newest version`() {
            subproject("a-lib").declare("api", "io.spine:spine-base:9.2.0")
            subproject("b-tests").declare("testImplementation", "io.spine:spine-base:10.0.0")
            subproject("c-run").declare("runtimeOnly", "io.spine:spine-base:10.0.0")

            val dependency = rootProject.dependencies().single()

            dependency.dependency().version shouldBe "10.0.0"
            dependency.scopeName() shouldBe DependencyScope.runtime.name
        }

        /**
         * When the newest version of an artifact occurs only in test configurations,
         * the `test` scope is reported even if an older version is a production
         * dependency: the report describes the retained version as it is used.
         */
        @Test
        fun `taking the scope only from the usages of the newest version`() {
            subproject("a-tests").declare("testImplementation", SPINE_BASE_NEWER)
            subproject("b-lib").declare("api", SPINE_BASE)

            val dependency = rootProject.dependencies().single()

            dependency.dependency().version shouldBe "2.0.1"
            dependency.scopeName() shouldBe DependencyScope.test.name
        }

        @Test
        fun `keeping the 'test' scope for an artifact used only in tests`() {
            subproject("a-tests").declare("testImplementation", SPINE_BASE)
            subproject("b-tests").declare("testRuntimeOnly", SPINE_BASE)

            val dependency = rootProject.dependencies().single()

            dependency.scopeName() shouldBe DependencyScope.test.name
        }

        @Test
        fun `preferring a known scope over that of an unknown configuration`() {
            subproject("a-tools").declare("spineCompiler", SPINE_BASE)
            subproject("b-tests").declare("testImplementation", SPINE_BASE)

            val dependency = rootProject.dependencies().single()

            dependency.hasDefinedScope() shouldBe true
            dependency.scopeName() shouldBe DependencyScope.test.name
        }

        @Test
        fun `preferring the 'provided' scope over that of an unknown configuration`() {
            subproject("a-tools").declare("spineCompiler", SPINE_BASE)
            subproject("b-lib").declare("compileOnly", SPINE_BASE)

            val dependency = rootProject.dependencies().single()

            dependency.hasDefinedScope() shouldBe true
            dependency.scopeName() shouldBe DependencyScope.provided.name
        }
    }

    @Test
    fun `omit the scope of a dependency coming only from an unknown configuration`() {
        subproject("lib").declare("spineCompiler", SPINE_BASE)

        val dependency = rootProject.dependencies().single()

        dependency.hasDefinedScope() shouldBe false
    }

    @Test
    fun `omit the version of a dependency that declares none`() {
        subproject("a-bom").declare("api", "io.grpc:grpc-stub")
        subproject("b-lib").declare("api", SPINE_BASE)

        val out = StringWriter()
        DependencyWriter.of(rootProject).writeXmlTo(out)
        val xml = out.toString()

        xml shouldContain "<artifactId>grpc-stub</artifactId>"
        xml shouldNotContain "<version>null</version>"
        xml shouldContain "<version>2.0.0</version>"
    }

    @Test
    fun `write a production dependency as 'compile' even when it is also used in tests`() {
        subproject("a-tests").declare("testImplementation", SPINE_BASE)
        subproject("b-lib").declare("api", SPINE_BASE)

        val out = StringWriter()
        DependencyWriter.of(rootProject).writeXmlTo(out)
        val xml = out.toString()

        xml shouldContain "<artifactId>spine-base</artifactId>"
        xml shouldContain "<scope>compile</scope>"
        xml shouldNotContain "<scope>test</scope>"
    }

    @Test
    fun `lay out dependencies in the conventional Maven scope order`() {
        subproject("a-tests").declare("testImplementation", "io.spine:spine-testlib:2.0.0")
        subproject("b-run").declare("runtimeOnly", "io.spine:spine-logging:2.0.0")
        subproject("c-tools").declare("annotationProcessor", "io.spine:spine-validate:2.0.0")
        subproject("d-lib").declare("api", SPINE_BASE)

        val out = StringWriter()
        DependencyWriter.of(rootProject).writeXmlTo(out)
        val xml = out.toString()

        val compileAt = xml.indexOf("<scope>compile</scope>")
        val providedAt = xml.indexOf("<scope>provided</scope>")
        val runtimeAt = xml.indexOf("<scope>runtime</scope>")
        val testAt = xml.indexOf("<scope>test</scope>")

        compileAt shouldBeGreaterThan -1
        compileAt shouldBeLessThan providedAt
        providedAt shouldBeLessThan runtimeAt
        runtimeAt shouldBeLessThan testAt
    }

    private companion object {
        const val SPINE_BASE = "io.spine:spine-base:2.0.0"
        const val SPINE_BASE_NEWER = "io.spine:spine-base:2.0.1"
    }
}
