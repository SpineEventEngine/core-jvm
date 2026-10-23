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

// https://www.bouncycastle.org/java.html
@Suppress("unused", "ConstPropertyName")
object BouncyCastle {

    /**
     * The PKIX library, pinned to `bcpkix-jdk15on:1.70`, the last release of
     * the `-jdk15on` artifacts.
     *
     * Newer releases are published as `bcpkix-jdk18on`. They carry the same
     * `org.bouncycastle` classes, yet Gradle sees them as a different module
     * and resolves no conflict between the two.
     *
     * The [IntelliJ Platform][IntelliJ.version] stays at `213.7172.53` for now.
     * Its `ide-impl` module depends on the `-jdk15on` artifacts through two paths:
     *
     *  - `marketplace-zip-signer:0.1.3` requires `bcpkix-jdk15on:1.64`.
     *  - `remote-dev-util` requires `bcpg-jdk15on:1.69`, which requires
     *    `bcprov-jdk15on:1.69`.
     *
     * Staying on `-jdk15on` lets Gradle resolve `bcpkix-jdk15on` and
     * `bcprov-jdk15on` to `1.70`, the version of this library. With
     * `bcpkix-jdk18on` instead, a project that also has the IntelliJ Platform
     * on its classpath would get two copies of the BouncyCastle classes.
     *
     * The two paths also disagree on the version. That is why `tool-base`
     * leaves BouncyCastle out of the POM of its IntelliJ Platform uber JAR.
     *
     * Revisit this pin when [IntelliJ.version] changes.
     */
    const val libPkcsJdk15 = "org.bouncycastle:bcpkix-jdk15on:1.70"
}
