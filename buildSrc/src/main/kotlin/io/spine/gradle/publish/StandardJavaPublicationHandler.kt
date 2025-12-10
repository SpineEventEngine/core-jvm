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

package io.spine.gradle.publish

import io.spine.gradle.repo.Repository
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.create

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
     *  dependencies. Presence of ".pom" file is mandatory for publication to be consumed by
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
        }

        /*
           Other artifacts are represented by `Jar` tasks. Those artifacts do not bring any other
           metadata in comparison with `Component` (such as the `dependencies` notation).
         */
        jars.forEach {
            artifact(it)
        }
    }
}
