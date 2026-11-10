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

package io.spine.server.entity.storage

import com.google.common.testing.GcFinalization
import com.google.protobuf.Any as ProtoAny
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Message
import com.google.protobuf.copy
import com.google.protobuf.util.Timestamps
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.base.EntityState
import io.spine.base.Identifier
import io.spine.base.Time.currentTime
import io.spine.client.ResponseFormat
import io.spine.core.Versions
import io.spine.protobuf.AnyPacker
import io.spine.server.BoundedContextBuilder
import io.spine.server.ContextSpec
import io.spine.server.entity.AbstractEntityRepository
import io.spine.server.entity.EntityRecord
import io.spine.server.entity.TestTransaction
import io.spine.server.entity.TransactionalEntity
import io.spine.server.entity.entityRecord
import io.spine.server.entity.storage.given.EntityWithoutCustomColumns
import io.spine.server.entity.storage.given.TaskViewProjection
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.test.entity.TaskView
import io.spine.test.entity.TaskViewId
import io.spine.test.entity.taskView
import io.spine.test.entity.taskViewId
import io.spine.test.storage.StgTask
import io.spine.test.storage.StgTaskId
import io.spine.test.storage.stgTask
import io.spine.test.storage.stgTaskId
import io.spine.testing.UtilityClassTest
import io.spine.type.UnexpectedTypeException
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests the unpacking of entity states by the column getters of the specifications that
 * [SpecScanner] creates. The scanned columns are covered by the Java [SpecScannerTest].
 *
 * The tests observe the retention of states in two independent ways:
 *
 *  1. Through the garbage collector. A state that neither a test nor a storage references
 *     anymore must not stay reachable from the scanned specification.
 *
 *  2. Through the identity of the column values. A message unpacked anew has new instances
 *     of its `String` and message fields. Hence, getting the very same instance of a column
 *     value twice means that one unpacked state served both readings.
 */
@DisplayName("`SpecScanner` should")
internal class SpecScannerSpec : UtilityClassTest<SpecScanner>(SpecScanner::class.java) {

    @Nested inner class
    `create a specification which` {

        private val spec = SpecScanner.scan(TaskViewProjection::class.java)

        /**
         * Guards the reliance of `SpecScanner` on `AnyPacker` returning the message
         * that an `Any` instance has already unpacked.
         *
         * Should a new version of Base or Protobuf drop this behavior, the state of
         * a record would be unpacked once per column, and only this test would tell.
         */
        @Test
        fun `reuses the unpacked state when reading the columns of the same record`() {
            val state = taskRevision(1)
            val record = recordOf(state)

            val first = spec.valuesIn(record)
            val second = spec.valuesIn(record)

            first[nameColumn] shouldBe state.name
            first[dueDateColumn] shouldBe state.dueDate
            second[nameColumn] shouldBeSameInstanceAs first[nameColumn]
            second[dueDateColumn] shouldBeSameInstanceAs first[dueDateColumn]
        }

        @Test
        fun `does not retain the states of the records it read`() {
            val states = readColumnsOfRevisions(spec)

            assertSoftly {
                withClue("The number of packed states that are still reachable") {
                    states.packed.countReachable(keeper = spec) shouldBe 0
                }
                withClue("The number of unpacked states that are still reachable") {
                    states.unpacked.countReachable(keeper = spec) shouldBe 0
                }
            }
        }

        @Test
        fun `unpacks anew the state of a record equal to an earlier one`() {
            val states = revisions.map(::taskRevision)
            val names = states.map { nameIn(recordOf(it)) }

            val servedFromMemory = states.zip(names).count { (state, name) ->
                nameIn(recordOf(state)) === name
            }

            withClue("The number of states served from the memory of the specification") {
                servedFromMemory shouldBe 0
            }
        }

        /**
         * Makes several threads read the columns of their own records at the same time.
         *
         * A reader reads each record twice in a row, so that the second reading can be served
         * by a remembered state. Such a state could belong to another record. The records
         * are created in advance, so that the readers spend their time reading.
         */
        @Test
        fun `gives each of the concurrent readers the values of its own record`() {
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(READER_COUNT) { task ->
                thread(start = false, isDaemon = true, name = "spec-reader", block = task::run)
            }
            try {
                val mismatches = (0 until READER_COUNT).map { reader ->
                    val readings = revisions.map { revision ->
                        val state = taskRevision(reader * STATE_COUNT + revision)
                        state.name to recordOf(state)
                    }
                    pool.submit(Callable {
                        start.await()
                        (1..READING_ROUNDS).sumOf {
                            readings.count { (name, record) ->
                                val first = nameIn(record)
                                val repeated = nameIn(record)
                                first != name || repeated != name
                            }
                        }
                    })
                }
                start.countDown()

                withClue("The number of readings that got the values of another record") {
                    mismatches.sumOf { it.get(READER_TIMEOUT_SECONDS, SECONDS) } shouldBe 0
                }
            } finally {
                pool.shutdownNow()
            }
        }

        @Test
        fun `fails on a record with a state of another type`() {
            val record = newRecord(viewId, plainTaskRevision(1))

            shouldThrow<UnexpectedTypeException> {
                spec.valuesIn(record)
            }
        }

        @Test
        fun `fails on a record with a state that cannot be unpacked`() {
            // A length-delimited field whose declared length overruns the input.
            val truncated = ByteString.copyFrom(byteArrayOf(0x0A, 0x7F))
            val state = AnyPacker.pack(taskRevision(1)).copy { value = truncated }
            val record = recordWith(viewId, state)

            val exception = shouldThrow<UnexpectedTypeException> {
                spec.valuesIn(record)
            }

            exception.cause.shouldBeInstanceOf<InvalidProtocolBufferException>()
        }

        private fun nameIn(record: EntityRecord): Any =
            spec.valuesIn(record)[nameColumn].shouldNotBeNull()
    }

    /**
     * Writes many revisions of the state of one entity and checks that none of the earlier
     * ones stays in memory.
     *
     * The tests for an entity without state-based columns are the controls. Such an entity
     * needs no unpacking. Should a control fail, the states are retained by the storage,
     * by the repository, or by the test harness rather than by the specification.
     */
    @Nested inner class
    `not retain the states written earlier through` {

        private val factory = InMemoryStorageFactory.newInstance()
        private val contextSpec = ContextSpec.singleTenant("`SpecScanner` tests")
        private val context = BoundedContextBuilder.assumingTests().build()

        /**
         * Closes the context, which closes the repositories registered with it.
         */
        @AfterEach
        fun closeContext() {
            context.close()
        }

        @Test
        fun `an 'EntityRecordStorage'`() {
            val entityClass = TaskViewProjection::class.java
            factory.createEntityRecordStorage(contextSpec, entityClass).use { storage ->
                val packedStates = storage.overwrite(viewId) { recordOf(taskRevision(it)) }

                withClue(STILL_REACHABLE) {
                    packedStates.earlier().countReachable(keeper = storage) shouldBe 0
                }
            }
        }

        @Test
        fun `an 'EntityRecordStorage' of an entity without state-based columns`() {
            val entityClass = EntityWithoutCustomColumns::class.java
            factory.createEntityRecordStorage(contextSpec, entityClass).use { storage ->
                val packedStates = storage.overwrite(plainTaskId) {
                    recordOf(plainTaskRevision(it))
                }

                withClue(STILL_REACHABLE) {
                    packedStates.earlier().countReachable(keeper = storage) shouldBe 0
                }
            }
        }

        @Test
        fun `a repository`() {
            val repository = TaskViewRepository()
            context.internalAccess().register(repository)

            val packedStates = repository.storeRevisions(viewId, ::taskRevision)

            withClue(STILL_REACHABLE) {
                packedStates.earlier().countReachable(keeper = repository) shouldBe 0
            }
        }

        @Test
        fun `a repository of an entity without state-based columns`() {
            val repository = PlainTaskRepository()
            context.internalAccess().register(repository)

            val packedStates = repository.storeRevisions(plainTaskId, ::plainTaskRevision)

            withClue(STILL_REACHABLE) {
                packedStates.earlier().countReachable(keeper = repository) shouldBe 0
            }
        }
    }
}

/**
 * A plain record-based repository of entities that declare state-based columns.
 */
private class TaskViewRepository :
    AbstractEntityRepository<TaskViewId, TaskViewProjection, TaskView>()

/**
 * A plain record-based repository of entities that declare no columns.
 */
private class PlainTaskRepository :
    AbstractEntityRepository<StgTaskId, EntityWithoutCustomColumns, StgTask>()

/**
 * The number of distinct states a test passes through the code under test.
 */
private const val STATE_COUNT = 1_000

/**
 * The numbers of consecutive revisions of the state of an entity.
 */
private val revisions = 1..STATE_COUNT

/**
 * The number of threads that read the columns of records at the same time.
 */
private const val READER_COUNT = 8

/**
 * The number of times a reading thread goes through its records.
 *
 * The readers seldom collide. With 10 rounds, a racy memo of the unpacked state, written
 * on purpose, was caught in one run of five. With 100 rounds, it was caught in nine of ten.
 */
private const val READING_ROUNDS = 100

/**
 * The time a reading thread is given to finish. It only limits how long a hung test waits.
 */
private const val READER_TIMEOUT_SECONDS = 60L

private const val STILL_REACHABLE =
    "The number of packed states written earlier that are still reachable"

private val nameColumn = TaskView.Column.name().name()
private val dueDateColumn = TaskView.Column.dueDate().name()

private val viewId = taskViewId { id = 1 }
private val plainTaskId = stgTaskId { id = 1 }

/**
 * Creates the state of the task view as of the given revision.
 *
 * The states of different revisions are not equal to each other.
 */
private fun taskRevision(revision: Int): TaskView = taskView {
    id = viewId
    name = "Revision $revision"
    estimateInDays = revision
    status = TaskView.Status.STARTED
    dueDate = Timestamps.fromSeconds(revision.toLong())
}

/**
 * Creates the state of the entity without columns as of the given revision.
 */
private fun plainTaskRevision(revision: Int): StgTask = stgTask {
    taskId = plainTaskId
    title = "Revision $revision"
}

private fun recordOf(state: TaskView): EntityRecord = newRecord(state.id, state)

private fun recordOf(state: StgTask): EntityRecord = newRecord(state.taskId, state)

/**
 * Creates a record of the entity with the given identifier, packing the given state anew.
 */
private fun newRecord(id: Message, entityState: Message): EntityRecord =
    recordWith(id, AnyPacker.pack(entityState))

/**
 * Creates a record of the entity with the given identifier and the given packed state.
 */
private fun recordWith(id: Message, packedState: ProtoAny): EntityRecord = entityRecord {
    entityId = Identifier.pack(id)
    state = packedState
    version = Versions.zero()
}

/**
 * The weak references to the two forms of the states of the records whose columns
 * were read through a specification.
 *
 * @property packed The references to the packed states, as the records hold them.
 * @property unpacked The references to the values of the `due_date` column. Such a value
 *   is a part of the message into which the specification unpacked the state. Hence, it is
 *   reachable for as long as that message is.
 */
private class ReadStates(
    val packed: List<WeakReference<ProtoAny>>,
    val unpacked: List<WeakReference<Any>>
)

/**
 * Reads the column values of the records of all the [revisions] through the given
 * specification, the way it is done when the records are written.
 *
 * The records are created in the frame of this function, so that the caller holds
 * no strong references to them.
 */
private fun readColumnsOfRevisions(spec: RecordSpec<*, EntityRecord>): ReadStates {
    val (packed, unpacked) = revisions.map { revision ->
        val record = recordOf(taskRevision(revision))
        val dueDate = spec.valuesIn(record)[dueDateColumn].shouldNotBeNull()
        WeakReference(record.state) to WeakReference(dueDate)
    }.unzip()
    return ReadStates(packed, unpacked)
}

/**
 * Writes the records created for each of the [revisions] under the same identifier,
 * so that the storage itself holds only the latest of them.
 *
 * @return The weak references to the packed states of the records, in the order of writing.
 */
private fun <I : Any> EntityRecordStorage<I, *>.overwrite(
    id: I,
    recordOfRevision: (Int) -> EntityRecord
): List<WeakReference<ProtoAny>> =
    revisions.map { revision ->
        val record = recordOfRevision(revision)
        write(id, record)
        WeakReference(record.state)
    }

/**
 * Stores the entity with the given identifier once for each of the [revisions] of its state.
 *
 * Each of the revisions gets a new instance of the entity, as it happens when a repository
 * loads an entity before dispatching a message to it. Also, a state can be injected only
 * into an instance that has the zero version.
 *
 * The repository must store no other entities, so that the only record it finds
 * is the one written last. The in-memory storage returns the very record it was given,
 * and so the packed state that the specification read.
 *
 * @return The weak references to the packed states the repository wrote to its storage,
 *   in the order of writing.
 */
private fun <I : Any, E : TransactionalEntity<I, S, *>, S : EntityState<I>>
        AbstractEntityRepository<I, E, S>.storeRevisions(
    id: I,
    newState: (Int) -> S
): List<WeakReference<ProtoAny>> =
    revisions.map { revision ->
        val entity = create(id)
        val version = Versions.newVersion(revision, currentTime())
        TestTransaction.injectState(entity, newState(revision), version)
        store(entity)
        val record = findRecords(ResponseFormat.getDefaultInstance()).next()
        WeakReference(record.state)
    }

/**
 * Leaves out the latest of the written states.
 *
 * An in-memory storage holds the latest record of an entity, and so its state.
 */
private fun <T> List<T>.earlier(): List<T> = dropLast(1)

/**
 * Counts the referents that stay strongly reachable after a full garbage collection.
 *
 * @param keeper The object that must outlive the collection, so that the count tells
 *   what this object retains.
 */
private fun List<WeakReference<*>>.countReachable(keeper: Any): Int {
    GcFinalization.awaitFullGc()
    val result = count { it.get() != null }
    Reference.reachabilityFence(keeper)
    return result
}
