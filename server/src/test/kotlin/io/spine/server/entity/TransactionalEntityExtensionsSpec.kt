/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.entity.given.Given
import io.spine.server.entity.given.tryalter.StockKeeper
import io.spine.server.entity.given.tryalter.StockKeeperRepo
import io.spine.server.test.shared.StringEntity
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.EventEnvelope
import io.spine.test.tryalter.Stock
import io.spine.test.tryalter.command.replenishStock
import io.spine.test.tryalter.event.bulkOrderPlaced
import io.spine.test.tryalter.stock
import io.spine.testing.TestValues.randomString
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.server.TestEventFactory
import io.spine.validation.NonValidated
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Kotlin extensions of `TransactionalEntity` should")
internal class TransactionalEntityExtensionsSpec {

    @Test
    fun `add 'update' block handler for passing properties to 'builder'`() {
        val entity = createEntity()
        val prevVersion = entity.version
        val prevId = entity.id
        val str = randomString()

        entity.doUpdate(str)

        entity.value() shouldBe str
        entity.version.isIncrement(prevVersion) shouldBe true
        entity.id shouldBe prevId
    }

    @Test
    fun `add 'alter' block handler for passing properties to 'builder'`() {
        val entity = createEntity()
        val prevVersion = entity.version
        val prevId = entity.id
        val str = randomString()

        entity.doAlter(str)

        entity.value() shouldBe str
        entity.version.isIncrement(prevVersion) shouldBe true
        entity.id shouldBe prevId
    }

    @Nested inner class
    `add 'tryAlter' block that should` {

        @Test
        fun `apply a valid change to the live builder`() {
            val pm = stockKeeper(amount = 10)

            val violations = pm.tryAlter { inStock = 15 }

            violations.shouldBeEmpty()
            pm.liveState().inStock shouldBe 15
        }

        @Test
        fun `return violations, leaving the live builder untouched`() {
            val pm = stockKeeper(amount = 10)

            val violations = pm.tryAlter { inStock = -1 }

            violations shouldHaveSize 1
            violations[0].fieldPath.getFieldName(0) shouldBe "in_stock"
            pm.liveState().inStock shouldBe 10
        }

        @Test
        fun `fold setter-thrown violations into the returned list`() {
            val initialVendor = "Acme Corp."
            val pm = stockKeeper(vendorName = initialVendor)

            // The `(set_once)` option makes the generated setter throw
            // a `ValidationException` when the field is assigned again.
            val violations = pm.tryAlter { vendor = "Widgets Inc." }

            violations shouldHaveSize 1
            violations[0].fieldPath.getFieldName(0) shouldBe "vendor"
            pm.liveState().vendor shouldBe initialVendor
        }

        @Test
        fun `compose consecutive calls, validating the cumulative state`() {
            val pm = stockKeeper(amount = 0)

            pm.tryAlter { inStock = 5 }.shouldBeEmpty()
            pm.tryAlter { inStock -= 10 }.shouldNotBeEmpty()
            pm.tryAlter { inStock -= 3 }.shouldBeEmpty()

            pm.liveState().inStock shouldBe 2
        }

        @Test
        fun `propagate exceptions other than validation ones`() {
            val pm = stockKeeper(amount = 10)

            shouldThrow<IllegalStateException> {
                pm.tryAlter {
                    inStock = 3
                    error("Simulated failure.")
                }
            }

            pm.liveState().inStock shouldBe 10
        }

        @Test
        fun `leave no trace of a failed attempt after the transaction commits`() {
            val pm = stockKeeper(amount = 10)
            val initialState = pm.state()
            val initialVersion = pm.version()

            pm.tryAlter { inStock = -100 }.shouldNotBeEmpty()

            val tx = pm.transaction()
            tx.shouldNotBeNull()
            tx.commit()

            // `changed()` is the condition on which the framework stores an entity.
            pm.changed() shouldBe false
            pm.state() shouldBe initialState
            pm.version() shouldBe initialVersion
        }
    }

    @Nested inner class
    `when used in a receptor of a process manager` {

        private val requestFactory = TestActorRequestFactory(javaClass)
        private val eventFactory = TestEventFactory.newInstance(javaClass)
        private lateinit var context: BoundedContext
        private lateinit var repo: StockKeeperRepo

        @BeforeEach
        fun registerRepository() {
            repo = StockKeeperRepo()
            context = BoundedContextBuilder.assumingTests().build()
            context.internalAccess().register(repo)
        }

        @AfterEach
        fun closeContext() {
            context.close()
        }

        @Test
        fun `store the state changed by a clean call`() {
            replenish(10)
            placeBulkOrder(3)

            val pm = repo.find(STOCK_ID).orElseThrow()
            pm.state().inStock shouldBe 7
        }

        @Test
        fun `not store a process manager when the only change is withheld`() {
            replenish(10)
            val before = repo.find(STOCK_ID).orElseThrow()
            val stateBefore = before.state()
            val versionBefore = before.version()

            // The order exceeds the stock, so the reactor withholds the update
            // and emits nothing.
            placeBulkOrder(25)

            val after = repo.find(STOCK_ID).orElseThrow()
            after.state() shouldBe stateBefore
            after.version() shouldBe versionBefore
        }

        private fun replenish(quantity: Int) {
            val command = replenishStock {
                stock = STOCK_ID
                this.quantity = quantity
            }
            repo.dispatchCommand(CommandEnvelope.of(requestFactory.command().create(command)))
        }

        private fun placeBulkOrder(quantity: Int) {
            val order = bulkOrderPlaced {
                stock = STOCK_ID
                this.quantity = quantity
            }
            repo.dispatch(EventEnvelope.of(eventFactory.createEvent(order)))
        }
    }
}

private const val STOCK_ID = "stock-under-test"

/**
 * Creates the test fixture entity and injects a stub transaction so
 * that [TransactionalEntity.builder] is available.
 */
private fun createEntity() : Fixture {
    val entity = Fixture()
    val tx: Transaction<String, Fixture, StringEntity, StringEntity.Builder> =
        StubTransaction(entity, true, true)
    entity.injectTransaction(tx)
    return entity
}

/**
 * Creates a [StockKeeper] with the given state and injects a stub transaction
 * so that its live builder is available to [tryAlter].
 */
private fun stockKeeper(amount: Int = 0, vendorName: String = ""): StockKeeper {
    val pm = Given.processManagerOfClass(StockKeeper::class.java)
        .withId(STOCK_ID)
        .withState(
            stock {
                id = STOCK_ID
                inStock = amount
                vendor = vendorName
            }
        )
        .build()
    val tx: Transaction<String, StockKeeper, Stock, Stock.Builder> =
        StubTransaction(pm, /* active = */ true, /* stateChanged = */ false)
    pm.injectTransaction(tx)
    return pm
}

/**
 * Obtains the current content of the live builder of this process manager.
 */
private fun StockKeeper.liveState(): @NonValidated Stock {
    val tx = requireNotNull(transaction()) {
        "The process manager under test must have a transaction injected."
    }
    return tx.builder().buildPartial()
}

/**
 * An entity that uses the [TransactionalEntity] extension functions in its [doUpdate]
 * and [doAlter] methods.
 */
private class Fixture : TransactionalEntity<String, StringEntity, StringEntity.Builder>() {

    init {
        setId(randomString())
    }

    fun doUpdate(s: String) {
        val builder = update {
            value = s
        }
        setState(builder.build())
        incrementVersion()
    }

    fun doAlter(s: String) {
        alter {
            value = s
        }
        setState(builder().build())
        incrementVersion()
    }

    fun value(): String = state.value
}
