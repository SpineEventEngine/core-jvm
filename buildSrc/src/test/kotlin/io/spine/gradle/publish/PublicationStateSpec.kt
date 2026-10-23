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

package io.spine.gradle.publish

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.spine.gradle.publish.PublicationState.Finding
import io.spine.gradle.publish.PublicationState.Holding
import io.spine.gradle.publish.PublicationState.Presence
import io.spine.gradle.publish.PublicationState.Verdict
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`PublicationState` should")
internal class PublicationStateSpec {

    @Nested inner class
    `find the version unpublished` {

        @Test
        fun `when there is no publication to look up`() {
            Verdict.of(emptyList(), commit) shouldBe Verdict.UNPUBLISHED
        }

        @Test
        fun `when no publication is published anywhere`() {
            Verdict.of(listOf(absent(api), absent(backend)), commit) shouldBe
                    Verdict.UNPUBLISHED
        }
    }

    @Test
    fun `find the version published when every SBOM names the commit being published`() {
        val findings = listOf(complete(api, commit), complete(backend, commit), marker())

        Verdict.of(findings, commit) shouldBe Verdict.PUBLISHED
    }

    @Nested inner class
    `find the version partially published` {

        @Test
        fun `when some publications are not published`() {
            Verdict.of(listOf(complete(api, commit), absent(backend)), commit) shouldBe
                    Verdict.PARTIAL
        }

        @Test
        fun `when a publication is partially published`() {
            Verdict.of(listOf(complete(api, commit), partial(backend)), commit) shouldBe
                    Verdict.PARTIAL
        }

        @Test
        fun `even when the published ones name another commit`() {
            val findings = listOf(complete(api, otherCommit), absent(backend))

            Verdict.of(findings, commit) shouldBe Verdict.PARTIAL
        }

        @Test
        fun `even when the commit being published is unknown`() {
            val findings = listOf(complete(api, commit), partial(backend))

            Verdict.of(findings, commit = null) shouldBe Verdict.PARTIAL
        }
    }

    @Nested inner class
    `find a collision` {

        @Test
        fun `when an SBOM names another commit`() {
            Verdict.of(listOf(complete(api, otherCommit)), commit) shouldBe Verdict.COLLISION
        }

        @Test
        fun `when SBOMs name both the commit being published and another one`() {
            val findings = listOf(complete(api, commit), complete(backend, otherCommit))

            Verdict.of(findings, commit) shouldBe Verdict.COLLISION
        }

        @Test
        fun `when the copies of one SBOM in its destinations name different commits`() {
            val findings = listOf(
                Finding(
                    path = api,
                    version = version,
                    presence = Presence.COMPLETE,
                    commits = setOf(commit, otherCommit)
                )
            )

            Verdict.of(findings, commit) shouldBe Verdict.COLLISION
        }
    }

    @Nested inner class
    `leave the publisher unconfirmed` {

        @Test
        fun `when no published SBOM names a commit`() {
            Verdict.of(listOf(marker()), commit) shouldBe Verdict.UNCONFIRMED
        }

        @Test
        fun `when the commit being published is unknown`() {
            Verdict.of(listOf(complete(api, commit)), commit = null) shouldBe
                    Verdict.UNCONFIRMED
        }

        @Test
        fun `when a copy of an SBOM names no commit, beside a confirmed publication`() {
            val unknownRevision = Finding(
                path = backend,
                version = version,
                presence = Presence.COMPLETE,
                unconfirmed = true
            )
            val findings = listOf(complete(api, commit), unknownRevision)

            Verdict.of(findings, commit) shouldBe Verdict.UNCONFIRMED
        }
    }

    @Nested inner class
    `find a publication` {

        @Test
        fun `absent when no repository has anything of it`() {
            val holdings = listOf(holding(registry), holding(gitHub))

            presence(holdings) shouldBe Presence.ABSENT
        }

        @Test
        fun `complete when every repository lists it and has every file`() {
            val holdings = listOf(
                holding(registry, listed = true, present = names + module),
                holding(gitHub, listed = true, present = names + module)
            )

            presence(holdings) shouldBe Presence.COMPLETE
        }

        @Test
        fun `complete without module metadata in any repository`() {
            val holdings = listOf(
                holding(registry, listed = true, present = names),
                holding(gitHub, listed = true, present = names)
            )

            presence(holdings) shouldBe Presence.COMPLETE
        }

        @Test
        fun `partial when a repository has nothing of it`() {
            val holdings = listOf(holding(registry, listed = true, present = names), holding(gitHub))

            presence(holdings) shouldBe Presence.PARTIAL
        }

        @Test
        fun `partial when a repository lacks a file`() {
            val holdings = listOf(
                holding(registry, listed = true, present = names),
                holding(gitHub, listed = true, present = names - sources)
            )

            presence(holdings) shouldBe Presence.PARTIAL
        }

        @Test
        fun `partial when only some repositories have the module metadata`() {
            val holdings = listOf(
                holding(registry, listed = true, present = names + module),
                holding(gitHub, listed = true, present = names)
            )

            presence(holdings) shouldBe Presence.PARTIAL
        }

        @Test
        fun `partial when a repository has its files but does not list it`() {
            val holdings = listOf(holding(registry, listed = false, present = names))

            presence(holdings) shouldBe Presence.PARTIAL
        }

        @Test
        fun `partial when a repository lists it but has none of its files`() {
            val holdings = listOf(holding(registry, listed = true))

            presence(holdings) shouldBe Presence.PARTIAL
        }
    }

    @Nested inner class
    `tell what is missing from` {

        @Test
        fun `each repository that lacks anything`() {
            val holdings = listOf(
                holding(registry, listed = true, present = names + module),
                holding(gitHub, listed = false, present = names - sources)
            )

            val gaps = PublicationState.gapsOf(
                holdings = holdings,
                path = api,
                names = names,
                module = module
            )

            gaps.keys shouldContainExactly setOf(gitHub)
            gaps.getValue(gitHub) shouldContainExactly listOf(
                "the version in `$gitHub/$api/maven-metadata.xml`",
                "`$sources` in `$gitHub`",
                "`$module` in `$gitHub`"
            )
        }

        @Test
        fun `no repository when the publication has no module metadata anywhere`() {
            val holdings = listOf(
                holding(registry, listed = true, present = names),
                holding(gitHub, listed = true, present = names)
            )

            val gaps = PublicationState.gapsOf(
                holdings = holdings,
                path = api,
                names = names,
                module = module
            )

            gaps.keys.shouldBeEmpty()
        }
    }

    @Nested inner class
    `read the revisions of an SBOM` {

        @Test
        fun `of every module of the build`() {
            val text = """
                { "packages": [
                  { "sourceInfo": "git+https://github.com/o/r@$commit#core[:core]" },
                  { "sourceInfo": "git+https://github.com/o/r@$commit#client[:client]" }
                ] }
                """.trimIndent()

            PublicationState.sbomRevisions(text) shouldContainExactlyInAnyOrder setOf(commit)
        }

        @Test
        fun `from a URI with a user`() {
            val text = """"sourceInfo": "git+ssh://git@github.com/o/r@$otherCommit#m[:m]""""

            PublicationState.sbomRevisions(text) shouldContainExactlyInAnyOrder
                    setOf(otherCommit)
        }

        @Test
        fun `of SHA-256 commits`() {
            val sha256 = "ab".repeat(32)
            val text = """"sourceInfo": "git+https://github.com/o/r@$sha256#m[:m]""""

            PublicationState.sbomRevisions(text) shouldContainExactlyInAnyOrder setOf(sha256)
        }

        @Test
        fun `ignoring a revision that is not a full commit hash`() {
            val text = """
                { "sourceInfo": "git+https://github.com/o/r@unknown#m[:m]" }
                { "sourceInfo": "git+https://github.com/o/r@35054d78#m[:m]" }
                { "sourceInfo": "git+https://github.com/o/r@${commit}0#m[:m]" }
                """.trimIndent()

            PublicationState.sbomRevisions(text).shouldBeEmpty()
        }
    }

    private companion object {

        const val version = "2.0.0-SNAPSHOT.575"
        const val api = "io/spine/sample/api"
        const val backend = "io/spine/sample/backend"
        const val commit = "35054d78dfce2a0ead2ca79347ad57f74508977c"
        const val otherCommit = "2b98e1801a0b3c4d5e6f708192a3b4c5d6e7f809"

        const val registry = "https://europe-maven.pkg.dev/spine-event-engine/snapshots"
        const val gitHub = "https://maven.pkg.github.com/SpineEventEngine/sample"
        const val sources = "api-$version-sources.jar"
        const val module = "api-$version.module"
        val names = listOf("api-$version.jar", sources, "api-$version.pom")

        fun absent(path: String) =
            Finding(path = path, version = version, presence = Presence.ABSENT)

        fun partial(path: String) =
            Finding(path = path, version = version, presence = Presence.PARTIAL)

        fun complete(path: String, sbomCommit: String) =
            Finding(
                path = path,
                version = version,
                presence = Presence.COMPLETE,
                commits = setOf(sbomCommit)
            )

        /**
         * The marker of a Gradle plugin, which is published with no SBOM.
         */
        fun marker() = Finding(
            path = "io/spine/sample/io.spine.sample.gradle.plugin",
            version = version,
            presence = Presence.COMPLETE
        )

        fun holding(
            destination: String,
            listed: Boolean = false,
            present: Collection<String> = emptyList()
        ) = Holding(destination = destination, listed = listed, present = present.toSet())

        /**
         * Decides how much of the publication with the [names] and the [module] metadata
         * the given [holdings] have.
         */
        fun presence(holdings: List<Holding>) =
            PublicationState.presenceOf(holdings = holdings, names = names, module = module)
    }
}
