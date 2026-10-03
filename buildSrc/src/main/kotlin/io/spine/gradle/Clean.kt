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

package io.spine.gradle

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Cleans the folder and all of its content.
 */
fun cleanFolder(folder: File) {
    if(!folder.exists()) {
        return
    }
    if(!folder.isDirectory) {
        throw IllegalArgumentException("A folder to clean " +
                "must be supplied: `${folder.absolutePath}`.")
    }
    Files.walk(folder.toPath())
        .sorted(Comparator.reverseOrder())
        .map(Path::toFile)
        .forEach(File::delete)
}
