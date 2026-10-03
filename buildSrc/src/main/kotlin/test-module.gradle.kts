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
