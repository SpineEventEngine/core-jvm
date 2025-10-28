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

import io.spine.dependency.lib.AutoService
import io.spine.dependency.lib.Grpc
import io.spine.dependency.lib.Kotlin
import io.spine.dependency.local.BaseTypes
import io.spine.dependency.local.Change
import io.spine.dependency.local.TestLib
import io.spine.dependency.local.Time
import io.spine.dependency.local.Validation
import io.spine.tools.compiler.gradle.plugin.LaunchSpineCompiler

plugins {
    `java-test-fixtures`
    `detekt-code-analysis`
    id("io.spine.core-jvm")
}

dependencies {
    api(Kotlin.reflect)
    api(Grpc.protobuf)
    api(Grpc.core)
    api(Grpc.stub)
    api(project(":client"))

    /*
     * Expose Validation API on the server side.
     *  E.g., for tuning custom validation for anti-corruption layer.
     */
    api(Validation.runtime)

    api(Change.lib)

    implementation(Grpc.inProcess)

    with(AutoService) {
        testAnnotationProcessor(processor)
        testCompileOnly(annotations)
    }
    testImplementation(Grpc.nettyShaded)

    testImplementation(TestLib.lib)
    testImplementation(BaseTypes.lib)

    testFixturesImplementation(TestLib.lib)
    testFixturesImplementation(Time.testLib)
    testFixturesImplementation(AutoService.annotations)
    val serverTestLib = project(":server-testlib")
    testFixturesImplementation(serverTestLib)

    testImplementation(Time.testLib)
    testImplementation(serverTestLib)
}

// Copies the documentation files to the Javadoc output folder.
// Inspired by https://discuss.gradle.org/t/do-doc-files-work-with-gradle-javadoc/4673
tasks.javadoc {
    doLast {
        copy {
            from("src/main/docs")
            into(layout.buildDirectory.dir("docs/javadoc"))
        }
    }
}

/**
 * Sets remote debug options for the `launchTestFixturesProtoData` task.
 *
 * @param enabled if `true` the task will be suspended.
 *
 * @see remoteDebug
 */
fun Project.testFixturesSpineCompilerDebug(enabled: Boolean = false) {
    val taskName = "launchTestFixturesSpineCompiler"
    val tasks = tasks.withType<LaunchSpineCompiler>()
    tasks.configureEach {
        if (this.name == taskName) {
            println("Configuring `$taskName` with the remote debug: $enabled.")
            this.remoteDebug(enabled)
        }
    }
}

afterEvaluate {
    testSpineCompilerRemoteDebug(false)
    testFixturesSpineCompilerDebug(false)

    tasks.named("testJar").configure { this as Jar
        from(sourceSets.testFixtures.get().output)
    }
}

spine {
    coreJvm {
        grpc {
            enabled.set(true)
        }
    }
}
