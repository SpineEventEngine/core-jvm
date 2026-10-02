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

package io.spine.dependency.boms

import io.gitlab.arturbosch.detekt.getSupportedKotlinVersion
import io.spine.dependency.DependencyWithBom
import io.spine.dependency.diagSuffix
import io.spine.dependency.isDokka
import io.spine.dependency.kotlinx.Coroutines
import io.spine.dependency.lib.Kotlin
import io.spine.dependency.test.JUnit
import io.spine.gradle.log
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer

/**
 * The plugin that forces versions of platforms declared in the [Boms] object.
 *
 * Versions are enforced via the
 * [org.gradle.api.artifacts.dsl.DependencyHandler.enforcedPlatform] call
 * for configurations of the project to which the plugin is applied.
 *
 * The configurations are selected by the "kind" of BOM.
 *
 * [Boms.core] are applied to:
 *  1. Production configurations, such as `api` or `implementation`.
 *  2. Compilation configurations.
 *  3. All `ksp` configurations.
 *
 *  [Boms.testing] are applied to all testing configurations.
 *
 *  In addition to forcing BOM-based dependencies,
 *  the plugin [forces][org.gradle.api.artifacts.ResolutionStrategy.force] the versions
 *  of [Kotlin.StdLib.artifacts] for all configurations because even though Kotlin
 *  artifacts are forced with BOM, the `variants` in the dependencies cannot be
 *  picked by Gradle.
 *
 *  Run Gradle with the [INFO][org.gradle.api.logging.Logger.isInfoEnabled] logging level
 *  to see the dependencies forced by this plugin.
 */
class BomsPlugin : Plugin<Project>  {

    private val productionConfigs = listOf(
        "api",
        "implementation",
        "compileOnly",
        "runtimeOnly"
    )

    override fun apply(project: Project) = with(project) {

        configurations.run {
            matching { isCompilationConfig(it.name) }.all {
                applyBoms(project, Boms.core)
            }
            matching { isKspConfig(it.name) }.all {
                applyBoms(project, Boms.core)
            }
            matching { it.name in productionConfigs }.all {
                applyBoms(project, Boms.core)
            }
            matching { isTestConfig(it.name) }.all {
                applyBoms(project, Boms.core + Boms.testing)
            }

            matching { !supportsBom(it.name) && !it.isDokka }.all {
                resolutionStrategy.eachDependency {
                    if (requested.group == Kotlin.group) {
                        val kotlinVersion = Kotlin.runtimeVersion
                        useVersion(kotlinVersion)
                        val suffix = this@all.diagSuffix(project)
                        log { "Forced Kotlin version `$kotlinVersion` in $suffix" }
                    }
                }
            }

            selectKotlinCompilerForDetekt()
            project.forceArtifacts()
        }
    }
}

private fun Configuration.applyBoms(project: Project, deps: List<DependencyWithBom>) {
    deps.forEach { dep ->
        withDependencies {
            val platform = project.dependencies.platform(dep.bom)
            addLater(project.provider { platform })
            project.log {
                "Applied BOM: `${dep.bom}` to the configuration: `${this@applyBoms.name}`."
            }
        }
    }
}

private val Configuration.isDetekt: Boolean
    get() = name.contains("detekt", ignoreCase = true)

@Suppress("UnstableApiUsage") // `io.gitlab.arturbosch.detekt.getSupportedKotlinVersion`
private fun ConfigurationContainer.selectKotlinCompilerForDetekt() =
    matching { it.isDetekt }
        .configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == Kotlin.group) {
                    val supportedVersion = getSupportedKotlinVersion()
                    useVersion(supportedVersion)
                    because("Force Kotlin version $supportedVersion in Detekt configurations.")
                }
            }
        }

private fun isCompilationConfig(name: String) =
    name.contains("compile", ignoreCase = true) &&
            // `compileProtoPath` or `compileTestProtoPath`.
            !name.contains("ProtoPath", ignoreCase = true)

private fun isKspConfig(name: String) =
    name.startsWith("ksp", ignoreCase = true)

private fun isTestConfig(name: String) =
    name.startsWith("test", ignoreCase = true)

/**
 * Tells if the configuration with the given [name] supports forcing
 * versions via the BOM mechanism.
 *
 * Not all configurations support forcing via BOM. E.g., the configurations created
 * by Protobuf Gradle Plugin such as `compileProtoPath` or `extractIncludeProto` do
 * not pick up versions of dependencies set via `enforcedPlatform(myBom)`.
 */
private fun supportsBom(name: String) =
    (isCompilationConfig(name) || isKspConfig(name) || isTestConfig(name))

/**
 * Forces the versions of the artifacts that are even being correctly selected by BOMs
 * are not guaranteed to be handled correctly when Gradle picks up a `variant`.
 *
 * The function forces the versions for all configurations but [detekt][isDetekt], because
 * it requires a compatible version of the Kotlin compiler.
 *
 * @see Kotlin.artifacts
 * @see Kotlin.StdLib.artifacts
 * @see Coroutines.artifacts
 * @see selectKotlinCompilerForDetekt
 */
private fun Project.forceArtifacts() =
    configurations.all {
        resolutionStrategy {
            if (!isDetekt && !isDokka) {
                val rs = this@resolutionStrategy
                val project = this@forceArtifacts
                val cfg = this@all
                Kotlin.forceArtifacts(project, cfg, rs)
                Kotlin.StdLib.forceArtifacts(project, cfg, rs)
                Coroutines.forceArtifacts(project, cfg, rs)
                JUnit.Jupiter.forceArtifacts(project, cfg, rs) /*
                    for configurations like `testFixturesCompileProtoPath`.
                 */
            }
        }
    }
