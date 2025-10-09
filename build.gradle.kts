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

@file:Suppress("RemoveRedundantQualifierName")

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
import io.spine.dependency.local.ProtoData
import io.spine.dependency.local.Reflect
import io.spine.dependency.local.TestLib
import io.spine.dependency.local.Time
import io.spine.dependency.local.ToolBase
import io.spine.dependency.local.Validation
import io.spine.dependency.test.JUnit
import io.spine.gradle.VersionWriter
import io.spine.gradle.checkstyle.CheckStyleConfig
import io.spine.gradle.github.pages.updateGitHubPages
import io.spine.gradle.javac.configureErrorProne
import io.spine.gradle.javac.configureJavac
import io.spine.gradle.javadoc.JavadocConfig
import io.spine.gradle.kotlin.setFreeCompilerArgs
import io.spine.gradle.publish.IncrementGuard
import io.spine.gradle.publish.PublishingRepos
import io.spine.gradle.publish.spinePublishing
import io.spine.gradle.repo.standardToSpineSdk
import io.spine.gradle.report.coverage.JacocoConfig
import io.spine.gradle.report.license.LicenseReporter
import io.spine.gradle.report.pom.PomGenerator
import org.gradle.jvm.tasks.Jar

buildscript {
    standardSpineSdkRepositories()
    doForceVersions(configurations)
    configurations {
        all {
            exclude(group = "io.spine", module = "spine-flogger-api")
            exclude(group = "io.spine", module = "spine-logging-backend")
            resolutionStrategy {
                val logging = io.spine.dependency.local.Logging
                force(
                    io.spine.dependency.lib.Jackson.bom,
                    io.spine.dependency.lib.Guava.lib,
                    io.spine.dependency.local.Base.lib,
                    io.spine.dependency.local.ToolBase.lib,
                    io.spine.dependency.local.CoreJava.server,
                    io.spine.dependency.local.Reflect.lib,
                    logging.lib,
                    logging.libJvm,
                    logging.grpcContext,
                    io.spine.dependency.local.Validation.runtime,
                )
            }
        }
    }

    dependencies {
        classpath(enforcedPlatform(io.spine.dependency.lib.Grpc.bom))
        classpath(enforcedPlatform(io.spine.dependency.kotlinx.Coroutines.bom))
        classpath(coreJvmCompiler.pluginLib)
    }
}

plugins {
    `java-library`
    kotlin("jvm")
    idea
    protobuf
    errorprone
    `gradle-doctor`
}
apply<BomsPlugin>()

repositories.standardToSpineSdk()

spinePublishing {
    modules = productionModules.map { it.name }.toSet()

    destinations = with(PublishingRepos) {
        setOf(
            gitHub("core-jvm"),
            cloudArtifactRegistry
        )
    }

    testJar {
        inclusions = setOf("server")
    }

    dokkaJar {
        kotlin = true
        java = true
    }
}

allprojects {
    apply {
        plugin("jacoco")
        plugin("idea")
        plugin("project-report")
    }

    apply(from = "$rootDir/version.gradle.kts")
    group = "io.spine"
    version = extra["versionToPublish"]!!
}

subprojects {
    repositories.standardToSpineSdk()
    applyPlugins()

    setupJava(BuildSettings.javaVersion)
    setupKotlin()

    defineDependencies()
    forceConfigurations()

    val generated = "$projectDir/generated"
    applyGeneratedDirectories(generated)
    setupPublishing()
    configureTaskDependencies()
}

JacocoConfig.applyTo(project)
PomGenerator.applyTo(project)
LicenseReporter.mergeAllReports(project)

/**
 * The alias for typed extensions functions related to subprojects.
 */
typealias Subproject = Project

/**
 * Applies plugins common to all modules to this subproject.
 */
fun Subproject.applyPlugins() {
    apply {
        plugin("java-library")
        plugin("jacoco")
        plugin("com.google.protobuf")
        plugin("net.ltgt.errorprone")
        plugin("kotlin")
        plugin("pmd")
        plugin("maven-publish")
        plugin("pmd-settings")
        plugin("dokka-for-java")
        plugin("io.spine.core-jvm")
        plugin("module-testing")
    }
    apply<BomsPlugin>()
    apply<IncrementGuard>()
    apply<VersionWriter>()

    LicenseReporter.generateReportIn(project)
    JavadocConfig.applyTo(project)
    CheckStyleConfig.applyTo(project)
}

/**
 * Configures Java tasks in this project.
 */
fun Subproject.setupJava(javaVersion: JavaLanguageVersion) {
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
fun Subproject.setupKotlin() {
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
fun Subproject.defineDependencies() {
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
fun Subproject.applyGeneratedDirectories(generatedDir: String) {
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
fun Subproject.forceConfigurations() {
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
                    Jackson.bom,
                    Jackson.annotations,
                    JUnit.bom,
                    Kotlin.bom,
                    Guava.lib,
                    KotlinPoet.lib,
                    Base.lib,
                    Validation.runtime,
                    Time.lib,
                    Logging.lib,
                    Logging.libJvm,
                    Logging.grpcContext,
                    BaseTypes.lib,
                    Compiler.api,
                    Change.lib,
                    Reflect.lib,
                    TestLib.lib,
                    ToolBase.lib,
                    ToolBase.jvmTools,
                    ToolBase.gradlePluginApi,
                    ToolBase.psiJava,
                    ToolBase.pluginBase,
                    ToolBase.intellijPlatform,
                    ToolBase.intellijPlatformJava,
                    CoreJava.server,
                    ProtoData.api,
                )
            }
        }
    }
}

/**
 * Configures publishing for this subproject.
 */
fun Subproject.setupPublishing() {
    updateGitHubPages(project.version.toString()) {
        allowInternalJavadoc.set(true)
        rootFolder.set(rootDir)
    }

    tasks.named("publish") {
        dependsOn("${project.path}:updateGitHubPages")
    }
}
