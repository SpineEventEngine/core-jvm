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

package io.spine.server.delivery

import com.google.protobuf.Timestamp
import com.google.protobuf.util.Durations
import com.google.protobuf.util.Timestamps.subtract
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.spine.base.Time.currentTime
import io.spine.protobuf.AnyPacker.unpack
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.CatchUpStatus.IN_PROGRESS
import io.spine.server.delivery.given.TestCatchUpJobs.catchUpJob
import io.spine.server.delivery.given.TestCatchUpJobs.writeJob
import io.spine.server.entity.EntityRecord
import io.spine.test.delivery.TallyA1
import io.spine.test.delivery.TallyB1
import io.spine.type.TypeUrl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * An abstract base for the tests of the storage of catch-up jobs.
 *
 * The tests write the jobs as the records of catch-up processes. They read the jobs back
 * the way the framework does: all of them, one by its ID, and those of a projection type.
 * They also query the jobs by each of their columns: `projection_type`, `status`, and
 * `when_last_read`. A storage that does not store a column fails the tests that query by it.
 *
 * A storage library runs these tests on its own storage by extending this class and creating
 * the storage in [newStorage].
 */
@Suppress("FunctionNaming" /* Test names, which detekt allows only in `src/test`. */)
abstract class CatchUpStorageTest {

    private lateinit var storage: CatchUpStorage

    /**
     * Creates the storage under test.
     */
    protected abstract fun newStorage(): CatchUpStorage

    @BeforeEach
    fun createStorage() {
        storage = newStorage()
        storage.clear()
    }

    @Test
    fun `declare the columns of a catch-up job`() {
        CatchUp.Column.definitions().map { it.name().value() } shouldContainExactlyInAnyOrder
                listOf("projection_type", "status", "when_last_read")
    }

    @Test
    fun `find the jobs by the projection type`() {
        val ofTallyA = write(job(TALLY_A), job(TALLY_A, COMPLETED))
        write(job(TALLY_B))

        val query = CatchUp.query()
            .projectionType().`is`(TALLY_A.value())
            .build()
        jobsOf(storage.findAll(query)) shouldContainExactlyInAnyOrder ofTallyA
    }

    @Test
    fun `find the jobs by the status`() {
        val completed = write(job(TALLY_A, COMPLETED), job(TALLY_B, COMPLETED))
        write(job(TALLY_A))

        val query = CatchUp.query()
            .status().`is`(COMPLETED)
            .build()
        jobsOf(storage.findAll(query)) shouldContainExactlyInAnyOrder completed
    }

    @Test
    fun `find the jobs by the time of the last read`() {
        val readEarlier = write(job(TALLY_A).lastRead(minutesAgo(3)))
        write(job(TALLY_A).lastRead(minutesAgo(1)))

        val query = CatchUp.query()
            .whenLastRead().isLessThan(minutesAgo(2))
            .build()
        jobsOf(storage.findAll(query)) shouldContainExactlyInAnyOrder readEarlier
    }

    @Test
    fun `read the jobs of a projection type`() {
        val ofTallyA = write(job(TALLY_A), job(TALLY_A, COMPLETED))
        write(job(TALLY_B))

        storage.readByType(TALLY_A).asSequence().toList() shouldContainExactlyInAnyOrder ofTallyA
    }

    @Test
    fun `read all the jobs`() {
        val all = write(job(TALLY_A), job(TALLY_B, COMPLETED))

        storage.readJobs().asSequence().toList() shouldContainExactlyInAnyOrder all
    }

    @Test
    fun `read a job by its ID`() {
        val job = write(job(TALLY_A)).single()

        storage.readJob(job.id) shouldBePresent { it shouldBe job }
    }

    @Test
    fun `read no job by an ID not stored`() {
        write(job(TALLY_A))

        storage.readJob(job(TALLY_A).id).shouldBeEmpty()
    }

    /**
     * Writes the given jobs to the storage under test.
     *
     * @return The jobs written.
     */
    private fun write(vararg jobs: CatchUp): List<CatchUp> {
        jobs.forEach { writeJob(storage, it) }
        return jobs.toList()
    }

    private companion object {

        val TALLY_A: TypeUrl = TypeUrl.of(TallyA1::class.java)
        val TALLY_B: TypeUrl = TypeUrl.of(TallyB1::class.java)

        /**
         * Creates a job of all the instances of the given projection type.
         */
        fun job(type: TypeUrl, status: CatchUpStatus = IN_PROGRESS): CatchUp =
            catchUpJob(type, status, minutesAgo(3), null)

        fun CatchUp.lastRead(time: Timestamp): CatchUp = copy { whenLastRead = time }

        fun minutesAgo(minutes: Long): Timestamp =
            subtract(currentTime(), Durations.fromMinutes(minutes))

        fun jobsOf(records: Iterator<EntityRecord>): List<CatchUp> =
            records.asSequence()
                .map { unpack(it.state, CatchUp::class.java) }
                .toList()
    }
}
