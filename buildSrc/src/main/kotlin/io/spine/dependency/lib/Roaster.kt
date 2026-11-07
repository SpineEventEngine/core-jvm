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

package io.spine.dependency.lib

/**
 * Roaster, a library for parsing and generating Java source code.
 *
 * The Spine SDK no longer uses Roaster. Its tools parse Java code with IntelliJ PSI.
 *
 * `classic-codegen`, the only `tool-base` artifact that still depended on Roaster,
 * dropped it in `2.0.0-SNAPSHOT.441`. A build that excludes the Roaster [group]
 * from `tool-base` artifacts can drop the exclusion once they are at that version or later.
 *
 * @see <a href="https://github.com/forge/roaster">Roaster on GitHub</a>
 * @deprecated Remove the usage. To parse Java code, use
 *   [ToolBase.psiJava][io.spine.dependency.local.ToolBase.psiJava].
 */
@Deprecated(
    "The Spine SDK no longer uses Roaster and parses Java code with IntelliJ PSI" +
            " via `ToolBase.psiJava`. Remove the usage: this object will be removed."
)
@Suppress("unused", "ConstPropertyName")
object Roaster {

    private const val version = "2.31.1.Final"

    const val group = "org.jboss.forge.roaster"
    const val api = "$group:roaster-api:$version"
    const val jdt = "$group:roaster-jdt:$version"
}
