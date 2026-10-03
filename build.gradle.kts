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

@file:Suppress("RemoveRedundantQualifierName")

import io.spine.dependency.boms.BomsPlugin
import io.spine.gradle.publish.PublishingRepos
import io.spine.gradle.publish.spinePublishing
import io.spine.gradle.repo.standardToSpineSdk
import io.spine.gradle.report.coverage.KoverConfig
import io.spine.gradle.report.license.LicenseReporter
import io.spine.gradle.report.pom.PomGenerator

buildscript {
    standardSpineSdkRepositories()
    doForceVersions(configurations)
    configurations {
        all {
            exclude(group = "io.spine", module = "spine-flogger-api")
            exclude(group = "io.spine", module = "spine-logging-backend")
            resolutionStrategy {
                val cfg = this@all
                val rs = this@resolutionStrategy
                io.spine.dependency.lib.Jackson.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.Jackson.DataFormat.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.Jackson.DataType.forceArtifacts(project, cfg, rs)

                io.spine.dependency.lib.JacksonV2.Core.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.JacksonV2.DataType.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.JacksonV2.Junior.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.JacksonV2.Module.forceArtifacts(project, cfg, rs)
                io.spine.dependency.lib.JacksonV2.DataFormat.forceArtifacts(project, cfg, rs)

                val logging = io.spine.dependency.local.Logging
                force(
                    io.spine.dependency.lib.Jackson.annotations,
                    io.spine.dependency.lib.Caffeine.lib,
                    // Floor artifacts request the pre-refresh versions;
                    // the Protobuf runtime must never be older than the
                    // refreshed gencode.
                    io.spine.dependency.lib.Jackson.bom,
                    io.spine.dependency.lib.JacksonV2.bom,
                    io.spine.dependency.kotlinx.Coroutines.bom,
                    io.spine.dependency.kotlinx.AtomicFu.lib,
                    io.spine.dependency.lib.Protobuf.javaLib,
                    io.spine.dependency.lib.Guava.lib,
                    io.spine.dependency.lib.Kotlin.bom,
                    io.spine.dependency.local.Base.annotations,
                    io.spine.dependency.local.Base.lib,
                    io.spine.dependency.local.Base.environment,
                    io.spine.dependency.local.Base.format,
                    io.spine.dependency.local.Time.lib,
                    io.spine.dependency.local.Time.javaExtensions,
                    io.spine.dependency.local.Compiler.api,
                    io.spine.dependency.local.Compiler.pluginLib,
                    io.spine.dependency.local.Compiler.gradleApi,
                    io.spine.dependency.local.Compiler.params,
                    io.spine.dependency.local.CoreJvm.server,
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
        classpath(spineCompiler.pluginLib)
        classpath(coreJvmCompiler.gradlePlugin)
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
    artifactPrefix = "spine-"
    toolArtifactPrefix = "NONE"
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
}

allprojects {
    apply {
        plugin("idea")
        plugin("project-report")
    }

    apply(from = "$rootDir/version.gradle.kts")
    group = "io.spine"
    version = extra["versionToPublish"]!!
}

KoverConfig.applyTo(rootProject)

gradle.projectsEvaluated {
    PomGenerator.applyTo(project)
    LicenseReporter.mergeAllReports(project)
}
