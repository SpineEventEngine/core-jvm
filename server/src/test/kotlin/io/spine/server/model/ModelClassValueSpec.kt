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

package io.spine.server.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.spine.server.aggregate.given.klasse.EngineAggregate
import io.spine.server.aggregate.model.AggregateClass.asAggregateClass
import io.spine.server.entity.model.EntityClass
import io.spine.server.entity.model.EntityClass.asEntityClass
import io.spine.server.model.given.ModelTestEnv.FaultyCommander
import io.spine.server.procman.model.ProcessManagerClass.asProcessManagerClass
import io.spine.test.reflect.command.RefCreateProject
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`ModelClassValue` should")
internal class ModelClassValueSpec {

    @Test
    fun `give the same model class of a Java class on every request`() {
        val classValue = ModelClassValue(::StubModelClass)

        classValue.get(Subject::class.java) shouldBeSameInstanceAs
                classValue.get(Subject::class.java)
    }

    @Test
    fun `give each Java class a model class of its own`() {
        val classValue = ModelClassValue(::StubModelClass)

        classValue.get(Subject::class.java).rawClass() shouldBe Subject::class.java
        classValue.get(OtherSubject::class.java).rawClass() shouldBe OtherSubject::class.java
    }

    @Test
    fun `give one model class to the threads racing on the first request`() {
        val classValue = ModelClassValue(::StubModelClass)
        val threads = 8
        val barrier = CyclicBarrier(threads)
        val executor = Executors.newFixedThreadPool(threads)
        try {
            val requests = List(threads) {
                executor.submit(Callable {
                    barrier.await()
                    classValue.get(Subject::class.java)
                })
            }

            val modelClasses = requests.map { it.get(10, SECONDS) }

            modelClasses.forEach { it shouldBeSameInstanceAs modelClasses.first() }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `keep nothing when a model class fails to be created`() {
        var attempts = 0
        val classValue = ModelClassValue { cls ->
            attempts++
            check(attempts > 1) { "The first attempt fails." }
            StubModelClass(cls)
        }

        shouldThrow<IllegalStateException> { classValue.get(Subject::class.java) }

        classValue.get(Subject::class.java).rawClass() shouldBe Subject::class.java
        attempts shouldBe 2
    }

    @Nested inner class
    `serve the kinds of model classes, so that` {

        @Test
        fun `a kind gives the same model class on every request`() {
            asAggregateClass(EngineAggregate::class.java) shouldBeSameInstanceAs
                    asAggregateClass(EngineAggregate::class.java)
        }

        @Test
        fun `an aggregate class also has a plain entity class`() {
            val aggregateClass = asAggregateClass(EngineAggregate::class.java)

            val entityClass = asEntityClass(EngineAggregate::class.java)

            entityClass.javaClass shouldBe EntityClass::class.java
            entityClass shouldNotBeSameInstanceAs aggregateClass
            entityClass shouldBe aggregateClass
            asAggregateClass(EngineAggregate::class.java) shouldBeSameInstanceAs aggregateClass
        }

        @Test
        fun `a model class which fails to be created fails on every request`() {
            repeat(2) {
                val error = shouldThrow<ExternalCommandReceiverMethodError> {
                    asProcessManagerClass(FaultyCommander::class.java)
                }
                error.message shouldContain RefCreateProject::class.java.name
            }
        }
    }

    /** A Java class for which the tests obtain model classes. */
    private class Subject

    /** Another Java class for which the tests obtain model classes. */
    private class OtherSubject

    /** The simplest model class. */
    private class StubModelClass(cls: Class<*>) : ModelClass<Any>(cls)
}
