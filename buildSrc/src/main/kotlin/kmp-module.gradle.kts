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

import io.spine.dependency.boms.BomsPlugin
import io.spine.dependency.local.Reflect
import io.spine.dependency.local.TestLib
import io.spine.dependency.test.JUnit
import io.spine.dependency.test.Jacoco
import io.spine.dependency.test.Kotest
import io.spine.gradle.checkstyle.CheckStyleConfig
import io.spine.gradle.javac.configureJavac
import io.spine.gradle.kotlin.setFreeCompilerArgs
import io.spine.gradle.publish.IncrementGuard
import io.spine.gradle.report.license.LicenseReporter

/**
 * Configures this [Project] as a Kotlin Multiplatform module.
 *
 * By its nature, this script plugin is similar to `jvm-module`. It performs
 * the basic module configuration.
 *
 * `jvm-module` is based on a mix of Java and Kotlin Gradle plugins. It allows
 * usage of Kotlin and Java in a single module that is built for JVM.
 * Whereas `kmp-module` is based on a Kotlin Multiplatform plugin. This plugin
 * supports different compilation targets within a single module: JVM, IOS,
 * Desktop, JS, etc. Also, it allows having some common sources in Kotlin
 * that can be shared with target-specific code. They are located in
 * `commonMain` and `commonTest` source sets. Each concrete target implicitly
 * depends on them.
 *
 * As for now, this script configures only JVM target, but other targets
 * will be added further.
 *
 * ### JVM target
 *
 * Sources for this target are placed in `jvmMain` and `jvmTest` directories.
 * Java is allowed to be used in `jvm` sources, but Kotlin is a preference.
 * Use Java only as a fall-back option where Kotlin is insufficient.
 * Due to this, Java linters are not even configured by `kmp-module`.
 *
 * @see <a href="https://kotlinlang.org/docs/multiplatform.html">Kotlin Multiplatform docs</a>
 */
@Suppress("unused")
val about = ""

plugins {
    kotlin("multiplatform")
    id("detekt-code-analysis")
    id("io.kotest.multiplatform")
    id("org.jetbrains.kotlinx.kover")
    `project-report`
}
apply<BomsPlugin>()
apply<IncrementGuard>()

project.forceConfigurations()

fun Project.forceConfigurations() {
    with(configurations) {
        forceVersions()
        all {
            resolutionStrategy {
                force(
                    Reflect.lib
                )
            }
        }
    }
}

/**
 * Configures Kotlin Multiplatform plugin.
 *
 * Please note, this extension DOES NOT configure Kotlin for JVM.
 * It configures KMP, in which Kotlin for JVM is only one of
 * possible targets.
 */
@Suppress("UNUSED_VARIABLE") // Avoid warnings for source set vars.
kotlin {
    // Enables explicit API mode for any Kotlin sources within the module.
    explicitApi()

    compilerOptions {
        setFreeCompilerArgs()
    }

    // Enables and configures JVM target.
    jvm {
        compilerOptions {
            jvmTarget.set(BuildSettings.jvmTarget)
        }
    }

    // Dependencies are specified per-target.
    // Please note, common sources are implicitly available in all targets.
    @Suppress("unused") // source set `val`s are used implicitly.
    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(Kotest.assertions)
                implementation(Kotest.frameworkEngine)
                implementation(Kotest.datatest)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(dependencies.enforcedPlatform(JUnit.bom))
                implementation(TestLib.lib)
                implementation(JUnit.Jupiter.engine)
                implementation(Kotest.runnerJUnit5Jvm)
            }
        }
    }
}

java {
    sourceCompatibility = BuildSettings.javaVersionCompat
    targetCompatibility = BuildSettings.javaVersionCompat
}

/**
 * Performs the standard task's configuration.
 *
 * Here's no difference with `jvm-module`, which does the same.
 *
 * Kotlin here is configured for both common and JVM-specific sources.
 * Java is for JVM only.
 *
 * Also, Kotlin and Java share the same test executor (JUnit), so tests
 * configuration is for both.
 */
tasks {
    withType<JavaCompile>().configureEach {
        configureJavac()
    }
}

/**
 * Overrides the default location of Kotlin sources.
 *
 * The default configuration of Detekt assumes presence of Kotlin sources
 * in `src/main/kotlin`, which is not the case for KMP.
 */
detekt {
    source.setFrom(
        "src/commonMain",
        "src/jvmMain"
    )
}

kover {
    useJacoco(version = Jacoco.version)
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

LicenseReporter.generateReportIn(project)
CheckStyleConfig.applyTo(project)
