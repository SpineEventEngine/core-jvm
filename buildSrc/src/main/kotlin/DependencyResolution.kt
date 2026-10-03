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

import io.spine.dependency.build.AnimalSniffer
import io.spine.dependency.build.CheckerFramework
import io.spine.dependency.build.Dokka
import io.spine.dependency.build.ErrorProne
import io.spine.dependency.build.FindBugs
import io.spine.dependency.build.JSpecify
import io.spine.dependency.isDokka
import io.spine.dependency.lib.Asm
import io.spine.dependency.lib.AutoCommon
import io.spine.dependency.lib.AutoService
import io.spine.dependency.lib.AutoValue
import io.spine.dependency.lib.CommonsCli
import io.spine.dependency.lib.CommonsCodec
import io.spine.dependency.lib.CommonsLogging
import io.spine.dependency.lib.Gson
import io.spine.dependency.lib.Guava
import io.spine.dependency.lib.J2ObjC
import io.spine.dependency.lib.JavaDiffUtils
import io.spine.dependency.lib.Kotlin
import io.spine.dependency.lib.Okio
import io.spine.dependency.lib.Plexus
import io.spine.dependency.lib.Protobuf
import io.spine.dependency.lib.Slf4J
import io.spine.dependency.local.Base
import io.spine.dependency.local.Spine
import io.spine.dependency.test.Hamcrest
import io.spine.dependency.test.Kotest
import io.spine.dependency.test.OpenTest4J
import io.spine.dependency.test.Truth
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.kotlin.dsl.exclude

/**
 * The function to be used in `buildscript` when a fully qualified call must be made.
 */
@Suppress("unused")
fun doForceVersions(configurations: ConfigurationContainer) {
    configurations.forceVersions()
}

/**
 * Forces dependencies used in the project.
 */
fun NamedDomainObjectContainer<Configuration>.forceVersions() {
    all {
        if (isDokka) {
            return@all
        }
        resolutionStrategy {
            failOnVersionConflict()
            cacheChangingModulesFor(0, "seconds")
            forceProductionDependencies()
            forceTestDependencies()
            forceTransitiveDependencies()
        }
    }
}

private fun ResolutionStrategy.forceProductionDependencies() {
    @Suppress("DEPRECATION") // Force versions of SLF4J and Kotlin libs.
    Protobuf.libs.forEach {
        force(it)
    }
    force(
        AnimalSniffer.lib,
        AutoCommon.lib,
        AutoService.annotations,
        CheckerFramework.annotations,
        Dokka.BasePlugin.lib,
        ErrorProne.annotations,
        ErrorProne.core,
        FindBugs.annotations,
        Gson.lib,
        Guava.lib,
        JSpecify.annotations,
        Protobuf.GradlePlugin.lib,
        Protobuf.libs,
        Slf4J.lib
    )
}

private fun ResolutionStrategy.forceTestDependencies() {
    force(
        Guava.testLib,
        Truth.libs,
        Kotest.assertions,
    )
}

/**
 * Forces transitive dependencies of 3rd-party components that we don't use directly.
 */
private fun ResolutionStrategy.forceTransitiveDependencies() {
    force(
        Asm.lib,
        Asm.tree,
        Asm.analysis,
        Asm.util,
        Asm.commons,
        AutoValue.annotations,
        CommonsCli.lib,
        CommonsCodec.lib,
        CommonsLogging.lib,
        Gson.lib,
        Hamcrest.core,
        J2ObjC.annotations,
        JavaDiffUtils.lib,
        Kotlin.jetbrainsAnnotations,
        Okio.lib,
        OpenTest4J.lib,
        Plexus.utils,
    )
}

@Suppress("unused")
fun NamedDomainObjectContainer<Configuration>.excludeProtobufLite() {

    fun excludeProtoLite(configurationName: String) {
        named(configurationName).get().exclude(
            mapOf(
                "group" to "com.google.protobuf",
                "module" to "protobuf-lite"
            )
        )
    }

    excludeProtoLite("runtimeOnly")
    excludeProtoLite("testRuntimeOnly")
}

/**
 * Excludes `spine-base` from the dependencies.
 */
@Suppress("unused")
fun ModuleDependency.excludeSpineBase() {
    exclude(group = Spine.group, module = "spine-base")
}

/**
 * Forces the version of [Spine.base] in the given project.
 */
@Suppress("unused")
fun Project.forceSpineBase() {
    configurations.all {
        resolutionStrategy {
            force(Base.lib)
        }
    }
}

/**
 * Forces configurations containing `"proto"` in their names (disregarding the case) to
 * use [Spine.baseForBuildScript].
 */
@Suppress("unused")
fun Project.forceBaseInProtoTasks() {
    configurations.configureEach {
        if (name.lowercase().contains("proto")) {
            resolutionStrategy {
                force(Base.libForBuildScript)
            }
        }
    }
}
