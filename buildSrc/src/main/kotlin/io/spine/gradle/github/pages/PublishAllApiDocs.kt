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

package io.spine.gradle.github.pages

import io.spine.gradle.SpineTaskGroup
import io.spine.gradle.getOrRegister
import io.spine.gradle.github.pages.TaskName.publishAllApiDocs
import io.spine.gradle.github.pages.TaskName.publishApiDocs
import org.gradle.api.Project

/**
 * Registers the [TaskName.publishAllApiDocs] task in the root project, which publishes
 * the API documentation of all the published projects of the build.
 *
 * Usage:
 * ```
 * PublishAllApiDocs.registerIn(project.rootProject, projectsToPublish)
 * ```
 *
 * `spinePublishing { }` makes this call, as it knows which projects are published.
 * The task is not a part of the publication to Maven repositories: the
 * `publish-api-docs.yml` workflow runs it apart from `publish`.
 *
 * The task is a lifecycle one: it has no action of its own, and publishes
 * the documentation through the [TaskName.publishApiDocs] tasks it depends on,
 * which the [PublishApiDocs] plugin registers in each documented project.
 */
internal object PublishAllApiDocs {

    /**
     * Registers the [TaskName.publishAllApiDocs] task in the [root] project.
     * The task publishes the API documentation of the [published] projects.
     *
     * The task depends on the [TaskName.publishApiDocs] task of each of
     * the [published] projects that has one, that is, applies the [PublishApiDocs]
     * plugin. This includes [root] when it is published itself, as in a single-project
     * build. A project that applies the plugin, but is not published, such as a module
     * of integration tests, is left out. The task of a project with a snapshot version
     * publishes nothing.
     *
     * The task is registered even when none of the projects has the documentation
     * to publish, and then does nothing. This lets the `publish-api-docs.yml`
     * workflow run it in any repository that publishes: Gradle fails on a task
     * that is missing from the build.
     *
     * The plugin registers the task of a project once that project is evaluated,
     * so the tasks to depend on are looked up only when Gradle resolves
     * the dependencies of this task. Repeated calls add their projects to the same
     * task, as `spinePublishing { }` may run more than once in a build.
     */
    fun registerIn(root: Project, published: Set<Project>) {
        val task = root.tasks.getOrRegister(publishAllApiDocs) {
            group = SpineTaskGroup.name
            description = "Publishes the API documentation of all the published modules"
        }
        task.configure {
            dependsOn(root.provider {
                published
                    .filter { publishApiDocs in it.tasks.names }
                    .map { it.tasks.named(publishApiDocs) }
            })
        }
    }
}
