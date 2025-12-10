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
import io.spine.dependency.build.ErrorProne
import io.spine.dependency.lib.Grpc
import io.spine.dependency.lib.Guava
import io.spine.dependency.lib.Jackson
import io.spine.dependency.lib.Kotlin
import io.spine.dependency.lib.KotlinPoet
import io.spine.dependency.local.Base
import io.spine.dependency.local.BaseTypes
import io.spine.dependency.local.Change
import io.spine.dependency.local.Compiler
import io.spine.dependency.local.CoreJava
import io.spine.dependency.local.Logging
import io.spine.dependency.local.Reflect
import io.spine.dependency.local.TestLib
import io.spine.dependency.local.Time
import io.spine.dependency.local.ToolBase
import io.spine.dependency.local.Validation
import io.spine.dependency.test.JUnit
import io.spine.gradle.checkstyle.CheckStyleConfig
import io.spine.gradle.github.pages.updateGitHubPages
import io.spine.gradle.javac.configureErrorProne
import io.spine.gradle.javac.configureJavac
import io.spine.gradle.javadoc.JavadocConfig
import io.spine.gradle.kotlin.setFreeCompilerArgs
import io.spine.gradle.publish.IncrementGuard
import io.spine.gradle.report.license.LicenseReporter
import io.spine.gradle.repo.standardToSpineSdk
import org.gradle.jvm.tasks.Jar

plugins {
    `java-library`
    jacoco
    id("com.google.protobuf")
    id("net.ltgt.errorprone")
    kotlin("jvm")
    pmd
    `maven-publish`
    id("pmd-settings")
    id("dokka-setup")
    id("module-testing")
    id("detekt-code-analysis")
    idea
}
apply<BomsPlugin>()
apply<IncrementGuard>()

repositories.standardToSpineSdk()

LicenseReporter.generateReportIn(project)
JavadocConfig.applyTo(project)
CheckStyleConfig.applyTo(project)

project.run {
    configureJava(BuildSettings.javaVersion)
    configureKotlin()
    addDependencies()
    forceConfigurations()

    val generatedDir = "$projectDir/generated"
    applyGeneratedDirectories(generatedDir)
    setupPublishing()
    configureTaskDependencies()
}

typealias Module = Project

/**
 * Configures Java tasks in this project.
 */
fun Module.configureJava(javaVersion: JavaLanguageVersion) {
    java {
        toolchain.languageVersion.set(javaVersion)
    }
    tasks {
        withType<JavaCompile>().configureEach {
            configureJavac()
            configureErrorProne()
        }
        withType<Jar>().configureEach {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }
}

/**
 * Configures Kotlin tasks in this project.
 */
fun Module.configureKotlin() {
    kotlin {
        explicitApi()
        compilerOptions {
            jvmTarget.set(BuildSettings.jvmTarget)
            setFreeCompilerArgs()
        }
    }
}

/**
 * Defines dependencies of this subproject.
 */
fun Module.addDependencies() {
    dependencies {
        ErrorProne.apply {
            errorprone(core)
        }
        implementation(Validation.runtime)
    }
}

/**
 * Adds directories with the generated source code to source sets of the project and
 * to IntelliJ IDEA module settings.
 *
 * @param generatedDir
 *          the name of the root directory with the generated code
 */
fun Module.applyGeneratedDirectories(generatedDir: String) {
    val generatedMain = "$generatedDir/main"
    val generatedJava = "$generatedMain/java"
    val generatedKotlin = "$generatedMain/kotlin"
    val generatedGrpc = "$generatedMain/grpc"
    val generatedSpine = "$generatedMain/spine"

    val generatedTest = "$generatedDir/test"
    val generatedTestJava = "$generatedTest/java"
    val generatedTestKotlin = "$generatedTest/kotlin"
    val generatedTestGrpc = "$generatedTest/grpc"
    val generatedTestSpine = "$generatedTest/spine"

    sourceSets {
        main {
            java.srcDirs(
                generatedJava,
                generatedGrpc,
                generatedSpine,
            )
            kotlin.srcDirs(
                generatedKotlin,
            )
        }
        test {
            java.srcDirs(
                generatedTestJava,
                generatedTestGrpc,
                generatedTestSpine,
            )
            kotlin.srcDirs(
                generatedTestKotlin,
            )
        }
    }

    idea {
        module {
            generatedSourceDirs.addAll(files(
                    generatedJava,
                    generatedKotlin,
                    generatedGrpc,
                    generatedSpine,
            ))
            testSources.from(
                generatedTestJava,
                generatedTestKotlin,
                generatedTestGrpc,
                generatedTestSpine,
            )
            isDownloadJavadoc = true
            isDownloadSources = true
        }
    }
}

/**
 * Forces dependencies of this project.
 */
fun Module.forceConfigurations() {
    configurations {
        forceVersions()
        excludeProtobufLite()

        all {
            exclude(group = "io.spine", module = "spine-flogger-api")
            exclude(group = "io.spine", module = "spine-logging-backend")
            exclude(group = "io.spine", module = "spine-validate")

            resolutionStrategy {
                /* Force the version of gRPC used by the `:client` module over the one
                   set by `mc-java` in the `:core` module when specifying compiler artifact
                   for the gRPC plugin.
                   See `io.spine.tools.mc.java.gradle.plugins.JavaProtocConfigurationPlugin
                   .configureProtocPlugins()` method which sets the version from resources. */
                Grpc.forceArtifacts(project, this@all, this@resolutionStrategy)
                force(Grpc.ProtocPlugin.artifact)

                // Substitute the legacy artifact coordinates with the new `ToolBase.lib` alias.
                dependencySubstitution {
                    substitute(module("io.spine.tools:spine-tool-base")).using(module(ToolBase.lib))
                }

                Jackson.forceArtifacts(project, this@all, this@resolutionStrategy)
                Jackson.DataFormat.forceArtifacts(project, this@all, this@resolutionStrategy)
                Jackson.DataType.forceArtifacts(project, this@all, this@resolutionStrategy)

                force(
                    Base.annotations,
                    Base.lib,
                    BaseTypes.lib,
                    Change.lib,
                    Compiler.api,
                    CoreJava.server,
                    Grpc.bom,
                    Guava.lib,
                    JUnit.bom,
                    Jackson.annotations,
                    Jackson.bom,
                    Kotlin.bom,
                    KotlinPoet.lib,
                    Logging.grpcContext,
                    Logging.lib,
                    Logging.libJvm,
                    Reflect.lib,
                    TestLib.lib,
                    Time.lib,
                    Time.javaExtensions,
                    ToolBase.gradlePluginApi,
                    ToolBase.intellijPlatform,
                    ToolBase.intellijPlatformJava,
                    ToolBase.jvmTools,
                    ToolBase.lib,
                    ToolBase.pluginBase,
                    ToolBase.protobufSetupPlugins,
                    ToolBase.psiJava,
                    Validation.runtime,
                    "io.spine.validation:spine-validation-java-runtime:2.0.0-SNAPSHOT.354"
                )
            }
        }
    }
}

/**
 * Configures publishing for this subproject.
 */
fun Module.setupPublishing() {
    updateGitHubPages {
        rootFolder.set(rootDir)
    }

    tasks.named("publish") {
        dependsOn("${project.path}:updateGitHubPages")
    }
}
