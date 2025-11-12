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

/**
 * A handler for custom publications, which are declared under the [publications]
 * section of a module.
 *
 * Such publications should be treated differently than [StandardJavaPublicationHandler],
 * which is <em>created</em> for a module. Instead, since the publications are already declared,
 * this class only [assigns Maven coordinates][copyProjectAttributes].
 *
 * A module which declares custom publications must be specified in
 * the [SpinePublishing.modulesWithCustomPublishing] property.
 *
 * If a module with [publications] declared locally is not specified as one with custom publishing,
 * it may cause a name clash between an artifact produced by
 * the [standard][org.gradle.api.publish.maven.MavenPublication] publication, and custom ones.
 * To have both standard and custom publications, please specify custom artifact IDs or
 * classifiers for each custom publication.
 *
 * @see StandardJavaPublicationHandler
 */
internal class CustomPublicationHandler private constructor(
    project: Project,
    destinations: Set<Repository>
) : PublicationHandler(project, destinations) {

    override fun handlePublications() {
        project.publications.forEach {
            (it as MavenPublication).copyProjectAttributes()
        }
    }

    companion object : HandlerFactory<CustomPublicationHandler>() {
        override fun create(
            project: Project,
            destinations: Set<Repository>,
            vararg params: Any
        ): CustomPublicationHandler = CustomPublicationHandler(project, destinations)
    }
}
