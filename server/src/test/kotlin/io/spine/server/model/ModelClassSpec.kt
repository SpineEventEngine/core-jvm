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

import com.google.common.testing.NullPointerTester.Visibility.PROTECTED
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.spine.server.aggregate.given.klasse.EngineAggregate
import io.spine.server.aggregate.model.AggregateClass.asAggregateClass
import io.spine.server.entity.model.EntityClass
import io.spine.server.entity.model.EntityClass.asEntityClass
import io.spine.server.event.EventReactor
import io.spine.server.event.model.EventReactorClass.asReactorClass
import io.spine.server.model.given.ModelTestEnv.FaultyCommander
import io.spine.server.procman.model.ProcessManagerClass.asProcessManagerClass
import io.spine.test.reflect.command.RefCreateProject
import io.spine.testing.ClassTest
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`ModelClass` should")
internal class ModelClassSpec : ClassTest<ModelClass<*>>(ModelClass::class.java, PROTECTED) {

    @Test
    fun `give the same instance of a model class on every request`() {
        asAggregateClass(EngineAggregate::class.java) shouldBeSameInstanceAs
                asAggregateClass(EngineAggregate::class.java)
    }

    @Test
    fun `give a plain entity class of an aggregate class, even after its aggregate class`() {
        val aggregateClass = asAggregateClass(EngineAggregate::class.java)

        val entityClass = asEntityClass(EngineAggregate::class.java)

        entityClass.javaClass shouldBe EntityClass::class.java
        entityClass shouldNotBeSameInstanceAs aggregateClass
        entityClass shouldBe aggregateClass
        asAggregateClass(EngineAggregate::class.java) shouldBeSameInstanceAs aggregateClass
    }

    @Test
    fun `give one instance to the threads racing on the first request`() {
        val threads = 8
        val barrier = CyclicBarrier(threads)
        val executor = Executors.newFixedThreadPool(threads)
        try {
            val requests = List(threads) {
                executor.submit(Callable {
                    barrier.await()
                    asReactorClass(RacingReactor::class.java)
                })
            }

            val modelClasses = requests.map { it.get(10, SECONDS) }

            modelClasses.forEach { it shouldBeSameInstanceAs modelClasses.first() }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `not keep a model class which fails to be created`() {
        repeat(2) {
            val error = shouldThrow<ExternalCommandReceiverMethodError> {
                asProcessManagerClass(FaultyCommander::class.java)
            }
            error.message shouldContain RefCreateProject::class.java.name
        }
    }
}

/**
 * A class of event reactors for which only this spec obtains a model class.
 *
 * So, the threads that request its model class make the first request.
 */
private abstract class RacingReactor : EventReactor
