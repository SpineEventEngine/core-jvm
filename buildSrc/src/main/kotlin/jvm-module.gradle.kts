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
import io.spine.dependency.build.CheckerFramework
import io.spine.dependency.build.Dokka
import io.spine.dependency.build.ErrorProne
import io.spine.dependency.build.JSpecify
import io.spine.dependency.isDokka
import io.spine.dependency.lib.Guava
import io.spine.dependency.lib.Jackson
import io.spine.dependency.lib.Kotlin
import io.spine.dependency.lib.Protobuf
import io.spine.dependency.local.Reflect
import io.spine.dependency.test.Jacoco
import io.spine.gradle.SpineTaskGroup
import io.spine.gradle.checkstyle.CheckStyleConfig
import io.spine.gradle.github.pages.publishApiDocs
import io.spine.gradle.javac.configureErrorProne
import io.spine.gradle.javac.configureJavac
import io.spine.gradle.javadoc.JavadocConfig
import io.spine.gradle.kotlin.setFreeCompilerArgs
import io.spine.gradle.report.license.LicenseReporter

plugins {
    `java-library`
    id("net.ltgt.errorprone")
    id("pmd-settings")
    id("project-report")
    kotlin("jvm")
    id("detekt-code-analysis")
    id("dokka-setup")
    id("org.jetbrains.kotlinx.kover")
    id("module-testing")
}
apply<BomsPlugin>()
LicenseReporter.generateReportIn(project)
JavadocConfig.applyTo(project)
CheckStyleConfig.applyTo(project)

project.run {
    configureJava()
    configureKotlin()
    addDependencies()
    forceConfigurations()

    val generatedDir = "$projectDir/generated"
    setTaskDependencies(generatedDir)

    configureApiDocs()
}

typealias Module = Project

fun Module.configureJava() {
    java {
        sourceCompatibility = BuildSettings.javaVersionCompat
        targetCompatibility = BuildSettings.javaVersionCompat
    }

    tasks {
        withType<JavaCompile>().configureEach {
            configureJavac()
            configureErrorProne()
        }
    }
}

fun Module.configureKotlin() {
    kotlin {
        explicitApi()
        compilerOptions {
            jvmTarget.set(BuildSettings.jvmTarget)
            setFreeCompilerArgs()
        }
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
}

/**
 * These dependencies are applied to all subprojects and do not have to
 * be included explicitly.
 *
 * We expose production code dependencies as API because they are used
 * by the framework parts that depend on `base`.
 */
fun Module.addDependencies() = dependencies {
    errorprone(ErrorProne.core)

    Protobuf.libs.forEach { api(it) }
    api(Guava.lib)

    compileOnlyApi(CheckerFramework.annotations)
    api(JSpecify.annotations)
    ErrorProne.annotations.forEach { compileOnlyApi(it) }
}

fun Module.forceConfigurations() {
    with(configurations) {
        forceVersions()
        excludeProtobufLite()
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
                    Jackson.annotations,
                    Kotlin.bom,
                    Dokka.BasePlugin.lib,
                    Reflect.lib,
                )
            }
        }
    }
}

fun Module.setTaskDependencies(generatedDir: String) {
    tasks {
        val cleanGenerated = register<Delete>("cleanGenerated") {
            group = SpineTaskGroup.name
            description = "Deletes the directory with generated sources"
            delete(generatedDir)
        }
        clean.configure {
            dependsOn(cleanGenerated)
        }
    }
    afterEvaluate {
        configureTaskDependencies()
    }
}

/**
 * Applies the plugin that registers the `publishApiDocs` task of this module.
 *
 * The `publish-api-docs.yml` workflow runs the task through `publishAllApiDocs` of
 * the root project, which depends on it for the published modules only. It is not
 * a part of `publish`.
 */
fun Module.configureApiDocs() {
    publishApiDocs {
        rootFolder.set(rootDir)
    }
}
