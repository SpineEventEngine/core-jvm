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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider

/**
 * Describes what the SBOM published with this publication lists.
 *
 * A publication made from a software component, such as `components["java"]`, needs
 * no description: its SBOM lists the runtime dependencies of its module, as its POM
 * does. A publication of a file made with `artifact(...)` publishes what its module
 * makes of it — a POM written by hand, content packed into a JAR — and tells its SBOM
 * the same:
 *
 * ```kotlin
 * publishing {
 *     publications {
 *         create<MavenPublication>("fatJar") {
 *             artifact(tasks.shadowJar)
 *             sbom {
 *                 dependencies(fatJarDependencies)
 *                 bundled(tasks.shadowJar)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * The SBOM describes each dependency of the artifact by a `DEPENDS_ON` relationship,
 * and each component bundled into it by a `CONTAINS` one. A bundled component is not
 * a dependency of the artifact, even if it is also among its
 * [dependencies][SbomContent.dependencies].
 *
 * A later call continues the description of the earlier ones: the content it tells to be
 * [bundled][SbomContent.bundled] adds to theirs, while a configuration it gives to
 * [dependencies][SbomContent.dependencies] replaces theirs.
 *
 * @see PublicationSbom
 */
fun MavenPublication.sbom(configure: SbomContent.() -> Unit) {
    val extensions = (this as? ExtensionAware)?.extensions
        ?: error("The publication `$name` cannot hold the description of its SBOM.")
    val content = extensions.findByType(SbomContent::class.java)
        ?: SbomContent().also { extensions.add(SbomContent::class.java, EXTENSION_NAME, it) }
    content.configure()
}

/**
 * The description of the SBOM of this publication, or `null` if the publication
 * has none.
 */
internal val MavenPublication.sbomContent: SbomContent?
    get() = (this as? ExtensionAware)?.extensions?.findByType(SbomContent::class.java)

/**
 * The name under which a publication holds the description of its SBOM.
 */
private const val EXTENSION_NAME = "sbomContent"

/**
 * What the SBOM published with a publication lists, as described by
 * [MavenPublication.sbom].
 */
class SbomContent internal constructor() {

    /**
     * The configuration whose dependency graph the artifact depends on, or `null`
     * if it depends on the runtime classpath of its module.
     */
    internal var dependenciesFrom: Configuration? = null
        private set

    private val bundleList = mutableListOf<Bundle>()

    /**
     * The parts of the content of the artifact, as given to [bundled].
     */
    internal val bundles: List<Bundle>
        get() = bundleList

    /**
     * Tells that the artifact depends on the dependency graph of the given
     * [configuration], rather than on the runtime classpath of its module.
     *
     * The configuration must belong to the module of the publication, and be
     * resolvable. It holds what the POM of the publication declares, so that the POM
     * could be written from it. It needs the attributes of `runtimeClasspath`, so that
     * it resolves the same variants of the modules it holds.
     *
     * A sibling module published as a fat JAR belongs in it non-transitively, as in
     * `project(":fat") { isTransitive = false }`. Its own dependencies are bundled into
     * its artifact, and not dependencies of the artifact depending on it.
     *
     * A later call replaces the configuration an earlier one gave.
     */
    fun dependencies(configuration: Configuration) {
        dependenciesFrom = configuration
    }

    /**
     * Tells that the artifact bundles each component of the given [configuration].
     *
     * Suits a JAR packing the classes of other modules into itself, as
     * `from(zipTree(...))` does. The configuration must belong to the module of
     * the publication, and be resolvable.
     *
     * Adds to the content that earlier calls tell to be bundled.
     */
    fun bundled(configuration: Configuration) {
        bundleList.add(Bundle.Components(configuration))
    }

    /**
     * Tells that the artifact bundles what the given Shadow [task] packs from its
     * configurations: the dependencies its dependency filter leaves in.
     *
     * The SBOM learns what the task packs from those dependencies alone, so it
     * does not see:
     *  - entries the task leaves out by their path — the modules they come from
     *    are still described as bundled;
     *  - classes that `minimize()` removes;
     *  - content added with `from(...)`;
     *  - file dependencies, which are not components.
     *
     * So a module that the POM declares, rather than the JAR bundling it, is to be
     * excluded by the dependency filter of the task, and not by the paths of its
     * entries. Otherwise, the SBOM describes the module as bundled.
     *
     * Adds to the content that earlier calls tell to be bundled.
     */
    fun bundled(task: TaskProvider<out ShadowJar>) {
        bundleList.add(Bundle.Shadowed(task))
    }
}

/**
 * A part of the content of an artifact.
 */
internal sealed interface Bundle {

    /**
     * Each component of the [configuration].
     */
    class Components(val configuration: Configuration) : Bundle

    /**
     * What the Shadow [task] packs from its configurations.
     */
    class Shadowed(val task: TaskProvider<out ShadowJar>) : Bundle
}
