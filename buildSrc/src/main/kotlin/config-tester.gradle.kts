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

import io.spine.gradle.ConfigTester
import io.spine.gradle.SpineRepos
import io.spine.gradle.cleanFolder
import java.nio.file.Path
import java.nio.file.Paths

// A reference to `config` to use along with the `ConfigTester`.
val config: Path = Paths.get("./")

// A temp folder to use to check out the sources of other repositories with the `ConfigTester`.
val tempFolder = File("./tmp")

// Creates a Gradle task which checks out and builds the selected Spine repositories
// with the local version of `config` and `config/buildSrc`.
ConfigTester(config, tasks, tempFolder)
    .addRepo(SpineRepos.baseTypes)  // Builds `base-types` at `master`.
    .addRepo(SpineRepos.base)       // Builds `base` at `master`.
    .addRepo(SpineRepos.coreJava)   // Builds `core-java` at `master`.

    // This is how one builds a specific branch of some repository:
    // .addRepo(SpineRepos.coreJava, Branch("grpc-concurrency-fixes"))

    // Register the produced task under the selected name to invoke manually upon need.
    .registerUnder("buildDependants")

// Cleans the temp folder used to check out the sources from Git.
tasks.register("clean") {
    doLast {
        cleanFolder(tempFolder)
    }
}
