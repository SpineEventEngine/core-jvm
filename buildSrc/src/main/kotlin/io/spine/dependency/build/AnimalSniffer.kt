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

package io.spine.dependency.build

// https://www.mojohaus.org/animal-sniffer/animal-sniffer-maven-plugin/
@Suppress("unused", "ConstPropertyName")
object AnimalSniffer {
    private const val version = "1.27"
    const val lib = "org.codehaus.mojo:animal-sniffer-annotations:$version"
}
