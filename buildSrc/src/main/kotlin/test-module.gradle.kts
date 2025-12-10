/*
 * Copyright 2024, TeamDev. All rights reserved.
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

import io.spine.dependency.local.Base
import io.spine.dependency.local.Validation
import io.spine.gradle.report.license.LicenseReporter

plugins {
    java
    `java-test-fixtures`
    id("module-testing")
}
LicenseReporter.generateReportIn(project)

dependencies {
    arrayOf(
        Base.lib,
        Validation.runtime
    ).forEach {
        testFixturesImplementation(it)?.because(
            """
            We do not apply CoreJvm Compiler Gradle plugin which adds
            the `implementation` dependency on Validation runtime automatically 
            (see `Project.configureValidation()` function in `CompilerConfigPlugin.kt`).
            
            In a test module we use vanilla `protoc` (via ProtoTap) and then run codegen
            using the Spine Compiler `Pipeline` and the plugins of the module under the test.

            Because of this we need to add the dependencies above explicitly for the
            generated code of test fixtures to compile.                
            """.trimIndent()
        )
    }
}
