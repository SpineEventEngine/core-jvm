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

import io.spine.gradle.ConfigTester
import io.spine.gradle.SpineRepos
import io.spine.gradle.SpineTaskGroup
import io.spine.gradle.cleanFolder
import java.nio.file.Path
import java.nio.file.Paths

// A reference to `config` to use along with the `ConfigTester`.
val config: Path = Paths.get("./")

// A temp folder to use to check out the sources of other repositories with the `ConfigTester`.
val tempFolder = File("./tmp")

// Creates a Gradle task that checks out and builds the selected Spine repositories
// with the local version of `config` and `config/buildSrc`.
ConfigTester(config, tasks, tempFolder)
    .addRepo(SpineRepos.baseTypes)  // Builds `base-types` at `master`.
    .addRepo(SpineRepos.base)       // Builds `base` at `master`.
    .addRepo(SpineRepos.coreJvm)    // Builds `core-jvm` at `master`.

    // This is how one builds a specific branch of some repository:
    // .addRepo(SpineRepos.coreJvm, Branch("grpc-concurrency-fixes"))

    // Register the produced task under the selected name to invoke manually upon need.
    .registerUnder("buildDependants")

// Cleans the temp folder used to check out the sources from Git.
tasks.register("clean") {
    group = SpineTaskGroup.name
    description = "Removes the temp folder used by `ConfigTester` to check out external sources"
    doLast {
        cleanFolder(tempFolder)
    }
}
