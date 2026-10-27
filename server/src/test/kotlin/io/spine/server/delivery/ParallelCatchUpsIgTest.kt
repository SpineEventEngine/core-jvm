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
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.spine.base.Time.currentTime
import io.spine.core.TenantId
import io.spine.server.BoundedContext
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.DeliveryStrategy.newIndex
import io.spine.server.delivery.event.CatchUpCompleted
import io.spine.server.delivery.event.CatchUpStarted
import io.spine.server.delivery.event.FinalizationStarted
import io.spine.server.delivery.event.HistoryEventsRecalled
import io.spine.server.delivery.event.HistoryFullyRecalled
import io.spine.server.delivery.given.HoldingExecutor
import io.spine.server.delivery.given.TallyPair
import io.spine.server.delivery.given.TallyRepository
import io.spine.server.delivery.given.TallyWeight
import io.spine.server.delivery.given.TestTenants
import io.spine.server.delivery.given.closeAll
import io.spine.server.event.AbstractEventReactor
import io.spine.server.event.NoReaction
import io.spine.server.event.React
import io.spine.server.tenant.TenantAwareRunner
import io.spine.test.delivery.numberAdded
import io.spine.testing.SlowTest
import io.spine.testing.server.blackbox.BlackBox
import java.util.concurrent.TimeUnit.SECONDS
import java.util.stream.Stream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.Timeout.ThreadMode.SEPARATE_THREAD
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Tests the catch-up jobs that are active at the same time.
 *
 * Each case runs a [Scenario] in up to three Bounded Contexts, and in every tenant of
 * a multi-tenant context, at once. Each context has two projection types of its own, `A` and
 * `B`, and four instances of each, which get the same events.
 *
 * The test starts the first job of the scenario in each context and tenant. A [Chain] starts
 * each next job when the previous one reaches the [StartPoint] of the case. At that moment, it
 * also posts a live event to every instance of the context, in every tenant. The test delivers
 * the messages itself, one page of each shard in turn, until none is left. So the jobs of
 * all the contexts and tenants run at once, in one thread.
 *
 * The tallies apply each value at a weight, which changes from 1 to [LIVE_WEIGHT] before
 * the first job starts. So a tally that is caught up holds all its values at the new weight,
 * and a tally that is not holds its history at the old weight.
 *
 * Each case checks that:
 *
 *  1. every job is completed;
 *  2. each tally holds the values of its own tenant, each once and in order: those of the history
 *     and those of the live events, which include the ones posted while the jobs ran;
 *  3. no message is left to deliver;
 *  4. no failure is recorded;
 *  5. the jobs overlapped.
 */
@SlowTest
@Timeout(value = 60, unit = SECONDS, threadMode = SEPARATE_THREAD)
@DisplayName("Catch-up jobs, active at the same time, should")
internal class ParallelCatchUpsIgTest : AbstractCatchUpIgTest() {

    private lateinit var case: Case
    private lateinit var delivery: Delivery
    private val systemEvents = HoldingExecutor()

    private val contexts = mutableListOf<ContextUnderTest>()
    private val posted = mutableListOf<Posted>()
    private val jobs = mutableMapOf<CatchUpId, Group>()

    /**
     * The jobs to start after a job reaches the start point, by the ID of that job.
     */
    private val nextJobs = mutableMapOf<CatchUpId, List<Step>>()

    /**
     * The starts and completions of the jobs, in the order they happened.
     */
    private val activity = mutableListOf<Pair<CatchUpId, JobEvent>>()

    private val runFailures = mutableListOf<String>()
    private var lastValue = 0

    @BeforeEach
    @AfterEach
    fun restoreTallyWeight() {
        TallyWeight.reset()
    }

    @AfterEach
    fun closeContexts() {
        closeAll(contexts.map { it.box })
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `complete each job, and keep the live events of every instance`(case: Case) {
        this.case = case
        delivery = useDelivery(
            UniformAcrossAllShards.forNumber(case.shards),
            observer = { },
            onePagePerRun = true
        )
        if (case.parallelPosting) {
            systemEvents.hold()
        }
        (1..case.contexts).forEach { contexts.add(ContextUnderTest(it)) }

        val since = subtract(currentTime(), Durations.fromMinutes(1))
        contexts.forEach { context ->
            context.tenants.forEach { tenant ->
                repeat(HISTORY_PER_INSTANCE) {
                    INSTANCES.forEach { context.post(tenant, it, live = false) }
                }
            }
        }
        deliverEverything()
        waitPastTurbulence()

        TallyWeight.value = LIVE_WEIGHT
        contexts.forEach { context ->
            context.catchingUpTenants.forEach { tenant ->
                val steps = case.scenario.steps
                val first = context.start(tenant, steps.first(), since)
                nextJobs[first] = steps.drop(1)
            }
        }
        deliverEverything()

        assertSoftly {
            (failures + runFailures).shouldBeEmpty()
            withClue("The statuses of the jobs") {
                val statuses = delivery.catchUpStorage().readJobs().asSequence()
                    .map { it.status }
                    .toList()
                statuses shouldHaveSize expectedJobCount()
                statuses.forEach { it shouldBe COMPLETED }
            }
            contexts.forEach { context ->
                context.tenants.forEach { tenant ->
                    INSTANCES.forEach { instance ->
                        Type.entries.forEach { type ->
                            withClue("The tally `$type` `$instance` of context ${context.number}" +
                                    ", tenant `${tenant?.value}`") {
                                context.valuesOf(tenant, type, instance) shouldBe
                                        expectedValues(context, tenant, type, instance)
                            }
                        }
                    }
                }
            }
            withClue("The messages left to deliver") {
                undelivered().shouldBeEmpty()
            }
            jobs.values.distinct().forEach { group ->
                withClue("The most jobs active at once in $group") {
                    mostActiveAtOnce(group) shouldBeGreaterThanOrEqual 2
                }
            }
        }
    }

    /**
     * Delivers the messages of every shard, and runs the system events held, if any,
     * until no message is left to deliver.
     */
    private fun deliverEverything() {
        var rounds = 0
        do {
            var delivered = 0
            (0 until case.shards).forEach { shard ->
                try {
                    delivered += delivery.deliverMessagesFrom(newIndex(shard, case.shards))
                        .map { it.deliveredCount() }
                        .orElse(0)
                } catch (e: RuntimeException) {
                    runFailures.add("Delivery run: $e")
                }
            }
            while (systemEvents.heldCount > 0) {
                systemEvents.releaseFirst()
                delivered++
            }
            rounds++
        } while ((delivered > 0 || undelivered().isNotEmpty()) && rounds < MAX_ROUNDS)
    }

    private fun expectedJobCount(): Int =
        contexts.sumOf { it.catchingUpTenants.size } * case.scenario.steps.size

    /**
     * Returns the values that the given tally must hold.
     *
     * A tally that a job catches up holds all its values at the new weight, in the order they
     * were posted. A tally that no job catches up holds its history at the old weight, and its
     * live events at the new one.
     */
    private fun expectedValues(
        context: ContextUnderTest,
        tenant: TenantId?,
        type: Type,
        instance: String
    ): List<Int> {
        val caughtUp = tenant in context.catchingUpTenants &&
                case.scenario.steps.any { it.type == type && it.includes(instance) }
        return posted
            .filter { it.context == context.number && it.tenant == tenant }
            .filter { it.instance == instance }
            .map { it.value * (if (caughtUp || it.live) LIVE_WEIGHT else 1) }
    }

    /**
     * Returns the largest number of jobs of the given group that were active at once.
     */
    private fun mostActiveAtOnce(group: Group): Int {
        val active = mutableSetOf<CatchUpId>()
        var most = 0
        activity.filter { (job, _) -> jobs[job] == group }
            .forEach { (job, event) ->
                when (event) {
                    JobEvent.STARTED -> active.add(job)
                    JobEvent.COMPLETED -> active.remove(job)
                }
                most = maxOf(most, active.size)
            }
        return most
    }

    /**
     * Starts the next job of the scenario, if the given job has reached the start point
     * of the case. Posts a live event to every instance of the context of the job then.
     */
    private fun reach(job: CatchUpId, point: StartPoint) {
        if (point != case.startPoint) {
            return
        }
        val steps = nextJobs.remove(job) ?: return
        val group = jobs.getValue(job)
        val context = contexts.first { it.number == group.context }
        context.postLiveEvents()
        if (steps.isNotEmpty()) {
            val since = subtract(currentTime(), Durations.fromMinutes(1))
            val next = context.start(group.tenant, steps.first(), since)
            nextJobs[next] = steps.drop(1)
        }
    }

    /**
     * A Bounded Context with the `A` and `B` tallies, and the [Chain] of its jobs.
     */
    private inner class ContextUnderTest(val number: Int) {

        private val tallies = TallyPair.ofContext(number)

        /**
         * The tenants of the context, or one `null` tenant if it is single-tenant.
         */
        val tenants: List<TenantId?> = when (case.tenancy) {
            Tenancy.SINGLE -> listOf(null)
            Tenancy.MULTI, Tenancy.MULTI_ONLY_X -> listOf(TestTenants.X, TestTenants.Y)
        }

        /**
         * The tenants that run the scenario.
         */
        val catchingUpTenants: List<TenantId?> = when (case.tenancy) {
            Tenancy.SINGLE -> listOf(null)
            Tenancy.MULTI -> listOf(TestTenants.X, TestTenants.Y)
            Tenancy.MULTI_ONLY_X -> listOf(TestTenants.X)
        }

        val box: BlackBox

        init {
            val name = "Tallies$number"
            val builder = if (case.tenancy == Tenancy.SINGLE) {
                BoundedContext.singleTenant(name)
            } else {
                BoundedContext.multitenant(name)
            }
            builder.add(tallies.a)
                .add(tallies.b)
                .addEventDispatcher(Chain())
            if (case.parallelPosting) {
                builder.systemSettings()
                    .enableParallelPosting()
                    .useCustomPostingExecutor(systemEvents)
            }
            box = BlackBox.from(builder)
        }

        /**
         * Posts an event with a new value to the given instance in the given tenant.
         */
        fun post(tenant: TenantId?, instance: String, live: Boolean) {
            lastValue++
            val newValue = lastValue
            val event = numberAdded {
                calculatorId = instance
                value = newValue
            }
            val target = if (tenant == null) box else box.withTenant(tenant)
            target.receivesEvent(event)
            posted.add(
                Posted(
                    context = number,
                    tenant = tenant,
                    instance = instance,
                    value = newValue,
                    live = live
                )
            )
        }

        /**
         * Posts a live event to every instance, in every tenant.
         */
        fun postLiveEvents() {
            tenants.forEach { tenant ->
                INSTANCES.forEach { post(tenant, it, live = true) }
            }
        }

        /**
         * Starts the job of the given step in the given tenant.
         */
        fun start(tenant: TenantId?, step: Step, since: Timestamp): CatchUpId {
            val repository = repositoryOf(step.type)
            val job = inTenant(tenant) { repository.catchUp(since, step.targets) }
            jobs[job] = Group(number, tenant)
            return job
        }

        /**
         * Returns the values of the given tally, or an empty list if it is not stored.
         */
        fun valuesOf(tenant: TenantId?, type: Type, instance: String): List<Int> =
            inTenant(tenant) { repositoryOf(type).valuesOf(instance).orEmpty() }

        private fun repositoryOf(type: Type): TallyRepository<*, *> =
            if (type == Type.A) tallies.a else tallies.b
    }

    /**
     * Follows the jobs of a context, and starts the next jobs of the scenario.
     */
    private inner class Chain : AbstractEventReactor() {

        @React
        fun on(event: CatchUpStarted): NoReaction {
            activity.add(event.id to JobEvent.STARTED)
            reach(event.id, StartPoint.STARTED)
            return noReaction()
        }

        @React
        fun on(event: HistoryEventsRecalled): NoReaction {
            reach(event.id, StartPoint.IN_PROGRESS)
            return noReaction()
        }

        @React
        fun on(event: HistoryFullyRecalled): NoReaction {
            reach(event.id, StartPoint.IN_PROGRESS)
            return noReaction()
        }

        @React
        fun on(event: FinalizationStarted): NoReaction {
            reach(event.id, StartPoint.FINALIZING)
            return noReaction()
        }

        @React
        fun on(event: CatchUpCompleted): NoReaction {
            activity.add(event.id to JobEvent.COMPLETED)
            return noReaction()
        }
    }

    /**
     * What the jobs of a case catch up, and in which order they start.
     */
    enum class Scenario(val steps: List<Step>) {

        /**
         * Two jobs for the `A` tallies, each for its own instance.
         */
        SAME_TYPE(listOf(Step(Type.A, setOf(FIRST)), Step(Type.A, setOf(SECOND)))),

        /**
         * Jobs for some `A` tallies, active at the same time as a job for all the `B` ones.
         */
        WITH_ALL_OF_ANOTHER_TYPE(
            listOf(Step(Type.A, setOf(FIRST)), Step(Type.B, null), Step(Type.A, setOf(SECOND)))
        )
    }

    /**
     * The tenants of a case.
     */
    enum class Tenancy {

        /**
         * One single-tenant context per Bounded Context.
         */
        SINGLE,

        /**
         * Multi-tenant contexts, in which two tenants run the scenario at once.
         */
        MULTI,

        /**
         * Multi-tenant contexts, in which one tenant runs the scenario, while the other one
         * gets the live events.
         */
        MULTI_ONLY_X
    }

    /**
     * The status of a job, at which the next one starts.
     */
    enum class StartPoint { STARTED, IN_PROGRESS, FINALIZING }

    /**
     * The type of a tally.
     */
    enum class Type { A, B }

    /**
     * What happened to a job.
     */
    private enum class JobEvent { STARTED, COMPLETED }

    /**
     * A job of a scenario: the type to catch up, and the instances, or `null` for all.
     */
    data class Step(val type: Type, val targets: Set<String>?) {

        fun includes(instance: String): Boolean = targets == null || instance in targets
    }

    /**
     * A case of the test.
     */
    data class Case(
        val scenario: Scenario,
        val tenancy: Tenancy,
        val contexts: Int,
        val shards: Int,
        val startPoint: StartPoint,
        val parallelPosting: Boolean = false
    ) {
        override fun toString(): String =
            "$scenario, $tenancy, $contexts context(s), $shards shard(s)," +
                    " the next job at $startPoint" +
                    if (parallelPosting) ", system events posted in parallel" else ""
    }

    /**
     * The jobs of one tenant in one context.
     */
    private data class Group(val context: Int, val tenant: TenantId?) {

        override fun toString(): String = "context $context, tenant `${tenant?.value}`"
    }

    /**
     * An event posted to a tally instance.
     */
    private data class Posted(
        val context: Int,
        val tenant: TenantId?,
        val instance: String,
        val value: Int,
        val live: Boolean
    )

    companion object {

        const val FIRST = "first"
        const val SECOND = "second"

        /**
         * The instances of each tally type. Only [FIRST] and [SECOND] are caught up
         * by the jobs for particular instances.
         */
        val INSTANCES = listOf(FIRST, SECOND, "third", "fourth")

        const val HISTORY_PER_INSTANCE = 3
        const val LIVE_WEIGHT = 100

        /**
         * Stops the delivery of a case that would not end.
         */
        const val MAX_ROUNDS = 10_000

        /**
         * Runs the operation in the given tenant, or with no tenant set if it is `null`.
         */
        private fun <T : Any> inTenant(tenant: TenantId?, operation: () -> T): T =
            if (tenant == null) {
                operation()
            } else {
                TenantAwareRunner.with(tenant).evaluate { operation() }
            }

        @JvmStatic
        fun cases(): Stream<Case> {
            val matrix = Scenario.entries.flatMap { scenario ->
                Tenancy.entries.flatMap { tenancy ->
                    (1..3).flatMap { contexts ->
                        listOf(1, 3).flatMap { shards ->
                            StartPoint.entries.map { point ->
                                Case(
                                    scenario, tenancy,
                                    contexts = contexts, shards = shards, startPoint = point
                                )
                            }
                        }
                    }
                }
            }
            val withParallelPosting = Case(
                Scenario.WITH_ALL_OF_ANOTHER_TYPE, Tenancy.MULTI,
                contexts = 3, shards = 3, startPoint = StartPoint.IN_PROGRESS,
                parallelPosting = true
            )
            return (matrix + withParallelPosting).stream()
        }
    }
}
