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

package io.spine.gradle.report.pom

import groovy.xml.MarkupBuilder
import java.io.StringWriter
import kotlin.reflect.KProperty
import org.gradle.api.Project
import org.gradle.kotlin.dsl.PropertyDelegate
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.withGroovyBuilder

/**
 * Information about the Gradle project.
 *
 * Includes group ID, artifact name, and the version.
 */
@Suppress("MemberVisibilityCanBePrivate") /* Property values accessed via `KProperty`. */
internal class ProjectMetadata
internal constructor(
    internal val project: Project,
    internal val groupId: String,
    internal val artifactId: String,
    internal val version: String
) {

    /**
     * Returns an XML string containing the project metadata.
     *
     * The XML format is compatible with the one defined for Maven's `pom.xml`.
     */
    override fun toString(): String {
        val writer = StringWriter()
        MarkupBuilder(writer).tagsFor(::groupId, ::artifactId, ::version)
        return writer.toString()
    }

    private fun MarkupBuilder.tagsFor(vararg property: KProperty<*>) {
        property.forEach {
            this.withGroovyBuilder {
                it.name { this@tagsFor.text(it.call()) }
            }
        }
    }
}

/**
 * Creates a new instance of [ProjectMetadata].
 *
 * The required information is first retrieved from the project.
 * And if a property is missing from the `project`, it is taken from the `extra` extension
 * of project's root project.
 */
internal fun Project.metadata(): ProjectMetadata {
    val groupId: String by nonEmptyValue(group)
    val artifactId: String by nonEmptyValue(name)
    val version: String by project.nonEmptyValue(this.version)
    return ProjectMetadata(project, groupId, artifactId, version)
}

private fun Project.nonEmptyValue(prop: Any): NonEmptyValue {
    return NonEmptyValue(prop.toString(), this)
}

private class NonEmptyValue(
    private val defaultValue: String,
    private val project: Project
) : PropertyDelegate {

    @Suppress("UNCHECKED_CAST")
    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T {
        if (defaultValue.isNotEmpty()) {
            return defaultValue as T
        }
        val result = project.rootProject.extra[property.name]
        return result as T
    }
}
