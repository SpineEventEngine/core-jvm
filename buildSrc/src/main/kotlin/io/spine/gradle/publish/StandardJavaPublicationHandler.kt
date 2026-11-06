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

import io.spine.gradle.repo.Repository
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.withType

/**
 * A publication for a typical Java project.
 *
 * In Gradle, to publish something, one should create a publication.
 * A publication has a name and consists of one or more artifacts plus information about
 * those artifacts – the metadata.
 *
 * An instance of this class represents
 * [MavenPublication][org.gradle.api.publish.maven.MavenPublication]
 * named [`"mavenJava"`][PUBLICATION_NAME].
 * It is generally accepted that a publication with this name contains a Java project
 * published to one or more Maven repositories.
 *
 * By default, only a jar with the compilation output of `main` source set and its
 * metadata files are published. Other artifacts are specified through the
 * [constructor parameter][jarFlags].
 * Please take a look on [specifyArtifacts] for additional info.
 *
 * @param jarFlags The flags for additional JARs published along with the compilation output.
 * @param destinations Maven repositories to which the produced artifacts will be sent.
 * @see <a href="https://docs.gradle.org/current/userguide/publishing_maven.html#publishing_maven:publications">
 *   The Maven Publish Plugin | Publications</a>
 * @see CustomPublicationHandler
 */
class StandardJavaPublicationHandler private constructor(
    project: Project,
    private val jarFlags: JarFlags,
    destinations: Set<Repository>,
) : PublicationHandler(project, destinations) {

    companion object : HandlerFactory<StandardJavaPublicationHandler>() {

        /**
         * The name of the publication created by [StandardJavaPublicationHandler].
         */
        const val PUBLICATION_NAME = "mavenJava"

        /**
         * The packaging of a POM whose main artifact is a JAR,
         * and the extension of that artifact.
         */
        private const val JAR = "jar"

        override fun create(
            project: Project,
            destinations: Set<Repository>,
            vararg params: Any
        ): StandardJavaPublicationHandler {
           return StandardJavaPublicationHandler(project, params[0] as JarFlags, destinations)
        }
    }

    /**
     * Creates a new `"mavenJava"` [MavenPublication][org.gradle.api.publish.maven.MavenPublication]
     * in the [project] associated with this publication handler.
     */
    override fun handlePublications() {
        val jars = project.artifacts(jarFlags)
        val publications = project.publications
        publications.create<MavenPublication>(PUBLICATION_NAME) {
            copyProjectAttributes()
            specifyArtifacts(jars)
        }
    }

    /**
     * Specifies which artifacts this [MavenPublication] will contain.
     *
     * A typical Maven publication contains:
     *
     *  1. Jar archives. For example, compilation output, sources, javadoc, etc.
     *  2. Maven metadata file that has the ".pom" extension.
     *  3. Gradle's metadata file that has the ".module" extension.
     *
     *  Metadata files contain information about a publication itself, its artifacts, and their
     *  dependencies. The presence of ".pom" file is mandatory for publication to be consumed by
     *  `mvn` build tool itself or other build tools that understand Maven notation (Gradle, Ivy).
     *  The presence of ".module" is optional, but useful when a publication is consumed by Gradle.
     *
     * @see <a href="https://maven.apache.org/pom.html">Maven – POM Reference</a>
     * @see <a href="https://docs.gradle.org/current/userguide/publishing_gradle_module_metadata.html">
     *   Understanding Gradle Module Metadata</a>
     */
    private fun MavenPublication.specifyArtifacts(jars: Set<TaskProvider<Jar>>) {

        /*
           "java" component provides a jar with compilation output of "main" source set.
           It is NOT defined as another `Jar` task intentionally. Doing that will leave the
           publication without correct ".pom" and ".module" metadata files generated.
        */
        val javaComponent = project.components.findByName("java")
        javaComponent?.let {
            from(it)
            declareJarPackaging()
        }

        /*
           Other artifacts are represented by `Jar` tasks. Those artifacts do not bring any other
           metadata in comparison with `Component` (such as the `dependencies` notation).
         */
        jars.forEach {
            artifact(it)
        }
    }

    /**
     * Sets the `jar` packaging of the POM, which Gradle would calculate from
     * the JAR of the `java` component.
     *
     * Until the packaging is set, reading it makes Gradle calculate it from the artifacts
     * of the publication, which creates the tasks producing them. [PublicationSbom] reads
     * the packaging in every build, so that the SBOM it adds does not change it.
     *
     * Writing the POM fails if the publication turns out to have a main artifact other
     * than one JAR, so that a misleading POM is not published. The artifacts are checked
     * when the POM is written, so that the check neither creates the tasks producing them
     * nor takes the artifacts of the component into the publication before it is final.
     * The result is an input of the task, so an up-to-date task does not hide a mismatch.
     */
    private fun MavenPublication.declareJarPackaging() {
        pom.packaging = JAR
        val publication = this
        val pomTask = pomTaskName()
        project.tasks.withType<GenerateMavenPom>()
            .named { it == pomTask }
            .configureEach {
                val mismatch = project.provider { publication.jarPackagingMismatch() }
                inputs.property("jarPackagingMismatch", mismatch.orElse(""))
                doFirst {
                    mismatch.orNull?.let { throw IllegalStateException(it) }
                }
            }
    }

    /**
     * Describes how this publication contradicts the `jar` packaging, or returns `null`
     * if it does not.
     *
     * The publication contradicts it if it keeps the `jar` packaging, but does not have
     * exactly one main artifact, a JAR, the SBOM not counted. Such is the case if
     * the build gives the `jar` task a classifier, or adds an artifact without one.
     */
    private fun MavenPublication.jarPackagingMismatch(): String? {
        if (pom.packaging != JAR) {
            // The build has set a packaging of its own.
            return null
        }
        val mainArtifacts = artifacts.filter {
            it.classifier.isNullOrEmpty() && it.extension != PublicationSbom.sbomExtension
        }
        return if (mainArtifacts.singleOrNull()?.extension == JAR) {
            null
        } else {
            val found = if (mainArtifacts.isEmpty()) {
                "no main artifact"
            } else {
                "the main artifacts ${mainArtifacts.map { it.file.name }}"
            }
            "The POM of the `$name` publication of `${project.path}` declares the `jar`" +
                    " packaging, but the publication has $found. Please keep the `jar`" +
                    " task without a classifier, or publish the module with custom publishing."
        }
    }
}
