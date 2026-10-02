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

import io.gitlab.arturbosch.detekt.Detekt

/**
 * This script-plugin sets up Kotlin code analyzing with Detekt.
 *
 * After applying, Detekt is configured to use the `${rootDir}/buildSrc/quality/detekt-config.yml` file.
 * Projects can append their own config files to override some parts of the default one or drop
 * it at all in favor of their own one.
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
 * Also, it's possible to suppress Detekt findings using a [baseline](https://detekt.dev/docs/introduction/baseline/)
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
