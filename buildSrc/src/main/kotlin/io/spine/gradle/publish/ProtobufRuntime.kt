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

package io.spine.gradle.publish

import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import groovy.util.Node
import io.spine.dependency.lib.Protobuf
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.named

/**
 * Leaves the Protobuf runtime ([Protobuf.libs]) out of the fat JAR built by
 * the `shadowJar` task of this project, declaring it as a `runtime` dependency of
 * the published artifacts instead.
 *
 * A bundled copy of the runtime cannot be relocated, as the runtime depends on
 * the names of its classes. Left unrelocated, it shadows the Protobuf runtime that
 * a consumer resolves for its own classpath, and no `force(...)` can replace it:
 * the copy is bytecode inside another artifact, not a dependency. Should the copy
 * be older than the code generated for the consumer, loading that code fails with
 * `ProtobufRuntimeVersionException`. Declared as a dependency, the runtime takes part
 * in the dependency resolution of the consumer, so the version it resolves wins.
 *
 * The libraries are declared:
 *  1. in the `protobufRuntime` configuration, which the `shadowRuntimeElements`
 *     configuration of the Shadow plugin extends, so that the Gradle module metadata
 *     of a publication of the shadowed variant, such as the publication of a Gradle
 *     plugin, lists them;
 *  2. in the POM of the given [publication], which publishes the fat JAR with
 *     `artifact(...)` and so receives no dependencies from Gradle.
 *
 * All the libraries of [Protobuf.libs] are declared, whichever of them the fat JAR
 * used to bundle. They are declared in the `runtime` scope, so they no longer reach
 * the compile classpath of the consumers of the fat JAR, which suits the tools and
 * plugins published as fat JARs.
 *
 * The `shadow` configuration is not used for this purpose: Shadow writes the files of
 * that configuration into the `Class-Path` attribute of the JAR manifest. Such entries
 * are relative to the JAR and carry the Protobuf version the build resolved, so in
 * a directory holding the JAR along with other Protobuf JARs, they would put that
 * version first again.
 *
 * Each library is declared non-transitively, so only the libraries themselves are
 * declared, `protobuf-java` among them. The rest of their dependencies stays where
 * it was: bundled into the fat JAR (Guava, Gson), or provided where the JAR runs,
 * as Gradle provides the Kotlin standard library to plugins. So consumers get
 * the same classes as before, except that those of the Protobuf runtime come from
 * genuine artifacts.
 *
 * The libraries are excluded by the dependency filter of the `shadowJar` task rather
 * than by the paths of their entries, so that everything they contribute, classes and
 * resources alike, is left out, and the SBOM published with the fat JAR does not
 * describe them as [bundled][SbomContent.bundled].
 *
 * Call this function after applying the Shadow and `maven-publish` plugins and
 * creating the publication of the fat JAR, and after any code writing dependencies
 * into the POM of the publication, so that the POM keeps a single `dependencies`
 * element:
 * ```kotlin
 * unbundleProtobufRuntime()                           // The `fatJar` of `uber-jar-module`.
 * unbundleProtobufRuntime(publication = "cliFatJar")  // A publication of its own.
 * ```
 * A project publishing its fat JAR in several publications calls the function
 * once for each of them.
 *
 * @param publication The name of the publication of the fat JAR. Defaults to
 *   `fatJar`, the publication created by `uber-jar-module`.
 */
fun Project.unbundleProtobufRuntime(publication: String = "fatJar") {
    if (configurations.findByName(PROTOBUF_RUNTIME) == null) {
        declareProtobufRuntime()
    }
    publications.named<MavenPublication>(publication) {
        declareRuntimeDependencies(Protobuf.libs)
    }
}

/**
 * Declares the Protobuf runtime in the `protobufRuntime` configuration, which
 * the `shadowRuntimeElements` configuration extends, and leaves it out of
 * the fat JAR built by the `shadowJar` task.
 */
private fun Project.declareProtobufRuntime() {
    val protobufRuntime = configurations.create(PROTOBUF_RUNTIME) {
        description = "The Protobuf runtime, which the published artifacts declare" +
                " rather than bundle."
        isCanBeConsumed = false
        isCanBeResolved = false
    }
    Protobuf.libs.forEach {
        dependencies.add(protobufRuntime.name, it) {
            isTransitive = false
        }
    }
    configurations.named(ShadowJavaPlugin.SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME) {
        extendsFrom(protobufRuntime)
    }
    tasks.named<ShadowJar>(SHADOW_JAR_TASK_NAME) {
        dependencies {
            exclude { "${it.moduleGroup}:${it.moduleName}" in protobufRuntimeModules }
        }
    }
}

/**
 * The name of the configuration holding the Protobuf runtime, which the published
 * artifacts declare rather than bundle.
 */
private const val PROTOBUF_RUNTIME = "protobufRuntime"

/**
 * The `group:name` coordinates of the Protobuf runtime libraries.
 */
private val protobufRuntimeModules: Set<String> =
    Protobuf.libs.mapTo(mutableSetOf()) { it.substringBeforeLast(':') }

/**
 * Declares the dependencies on the given `group:name:version` [coordinates]
 * in the POM of this publication, in the `runtime` scope and without
 * transitive dependencies.
 *
 * The dependencies are added to the `dependencies` element of the POM,
 * which is created if the POM has none.
 */
private fun MavenPublication.declareRuntimeDependencies(coordinates: List<String>) {
    pom.withXml {
        val root = asNode()
        val dependencies = root.children()
            .filterIsInstance<Node>()
            .firstOrNull { it.localName == "dependencies" }
            ?: root.appendNode("dependencies")
        coordinates.forEach {
            // Prefixed with `lib`, as plain `name` and `version` would hide those
            // of the publication.
            val (libGroup, libName, libVersion) = it.split(':')
            with(dependencies.appendNode("dependency")) {
                appendNode("groupId", libGroup)
                appendNode("artifactId", libName)
                appendNode("version", libVersion)
                appendNode("scope", "runtime")
                with(appendNode("exclusions").appendNode("exclusion")) {
                    appendNode("groupId", "*")
                    appendNode("artifactId", "*")
                }
            }
        }
    }
}

/**
 * The name of this node without its namespace.
 *
 * The name is a `QName` for a node parsed with namespaces, as the nodes of a POM are.
 * It renders as `{namespace}localName`.
 */
private val Node.localName: String
    get() = name().toString().substringAfterLast('}')
