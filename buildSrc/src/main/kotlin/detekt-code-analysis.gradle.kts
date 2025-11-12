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

import io.gitlab.arturbosch.detekt.Detekt

/**
 * This script-plugin sets up Kotlin code analyzing with Detekt.
 *
 * After applying, Detekt is configured to use `${rootDir}/buildSrc/quality/detekt-config.yml` file.
 * Projects can append their own config files to override some parts of the default one or drop
 * it at all in a favor of their own one.
 *
 * An example of appending a custom config file to the default one:
 *
 * ```
 * detekt {
 *     config.from("config/detekt-custom-config.yml")
 * }
 * ```
 *
 * To totally substitute it, just overwrite the corresponding property:
 *
 * ```
 * detekt {
 *     config = files("config/detekt-custom-config.yml")
 * }
 * ```
 *
 * Also, it's possible to suppress Detekt findings using [baseline](https://detekt.dev/docs/introduction/baseline/)
 * file instead of suppressions in source code.
 *
 * An example of passing a baseline file:
 *
 * ```
 * detekt {
 *     baseline = file("config/detekt-baseline.yml")
 * }
 * ```
 */
@Suppress("unused")
private val about = ""

plugins {
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    buildUponDefaultConfig = true
    config.from(files("${rootDir}/buildSrc/quality/detekt-config.yml"))
}

tasks {
    withType<Detekt>().configureEach {
        reports {
            html.required.set(true) // Only HTML report is generated.
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }
}
