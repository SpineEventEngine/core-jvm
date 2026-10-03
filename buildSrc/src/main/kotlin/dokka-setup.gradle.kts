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

import org.jetbrains.dokka.gradle.tasks.DokkaBaseTask

plugins {
    id("org.jetbrains.dokka") // Cannot use `Dokka` dependency object here yet.
    id("org.jetbrains.dokka-javadoc")
}

dependencies {
    useDokkaWithSpineExtensions()
}

tasks.withType<DokkaBaseTask>().configureEach {
    onlyIf {
        isInPublishingGraph()
    }
}

// The Dokka Javadoc format does not support Kotlin Multiplatform source sets, so its
// publication task fails for KMP modules ("No source set found for <module>/jvmMain").
// KMP modules publish HTML documentation, so skip the Javadoc publication for them.
plugins.withId("org.jetbrains.kotlin.multiplatform") {
    tasks.matching { it.name == "dokkaGeneratePublicationJavadoc" }.configureEach {
        enabled = false
    }
}

afterEvaluate {
    dokka {
        configureForKotlin(
            project,
            DocumentationSettings.SourceLink.url(project)
        )
    }
    val kspKotlin = tasks.findByName("kspKotlin")
    kspKotlin?.let {
        tasks.withType<DokkaBaseTask>().configureEach {
            dependsOn(kspKotlin)
        }
    }
}
