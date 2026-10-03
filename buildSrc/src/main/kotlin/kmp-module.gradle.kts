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

import io.spine.dependency.boms.BomsPlugin
import io.spine.dependency.isDokka
import io.spine.dependency.lib.Jackson
import io.spine.dependency.lib.Kotlin
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
import io.spine.gradle.testing.configureLogging

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
            if (isDokka) {
                return@all
            }
            resolutionStrategy {
                val cfg = this@all
                val rs = this@resolutionStrategy
                Jackson.forceArtifacts(project, cfg, rs)
                Jackson.DataFormat.forceArtifacts(project, cfg, rs)
                force(
                    Kotlin.bom,
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
 * the possible targets.
 */
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
    sourceSets {
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(Kotest.assertions)
                implementation(Kotest.frameworkEngine)
            }
        }
        getByName("jvmTest") {
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
 *
 * The `jvmTest` task mirrors the setup made by `module-testing` for
 * the `test` task of a `jvm-module` (`module-testing` itself cannot be
 * applied here because it brings `java-library`, which conflicts with
 * the Kotlin Multiplatform plugin). Unlike `module-testing`, no engine
 * filter is imposed: `jvmTest` dependencies include the Kotest runner,
 * which is a JUnit Platform engine of its own.
 */
tasks {
    withType<JavaCompile>().configureEach {
        configureJavac()
    }
    named<Test>("jvmTest") {
        useJUnitPlatform()
        configureLogging()
    }
}

/**
 * Overrides the default location of Kotlin sources.
 *
 * The default configuration of Detekt assumes the presence of Kotlin sources
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
