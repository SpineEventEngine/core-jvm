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

import io.spine.dependency.build.Pmd

plugins {
    pmd
}

pmd {
    toolVersion = Pmd.version
    isConsoleOutput = true
    incrementalAnalysis.set(true)

    // The build is going to fail in case of violations.
    isIgnoreFailures = false

    // Disable the default rule set to use the custom rules (see below).
    ruleSets = listOf()

    // Load PMD settings.
    val pmdSettings = file("$rootDir/buildSrc/quality/pmd.xml")
    val textResource: TextResource = resources.text.fromFile(pmdSettings)
    ruleSetConfig = textResource

    reportsDir = file("build/reports/pmd")

    // Just analyze the main sources; do not analyze tests.
    val javaExtension: JavaPluginExtension =
        project.extensions.getByType(JavaPluginExtension::class.java)
    val mainSourceSet = javaExtension.sourceSets.getByName("main")
    sourceSets = listOf(mainSourceSet)
}
