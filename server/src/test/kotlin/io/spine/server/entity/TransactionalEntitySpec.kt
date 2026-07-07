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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.core.Versions
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.entity.given.Given
import io.spine.server.entity.given.tryalter.StockKeeper
import io.spine.server.entity.given.tryalter.StockKeeperRepo
import io.spine.server.test.shared.StringEntity
import io.spine.server.test.shared.stringEntity
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

@DisplayName("`TransactionalEntity` should")
internal class TransactionalEntitySpec {

    @Nested inner class
    `be non-changed` {

        @Test
        fun `once created`() {
            newEntity().changed() shouldBe false
        }

        @Test
        fun `if the transaction is not changed`() {
            val entity = entityWithActiveTx(stateChanged = false)

            entity.changed() shouldBe false
        }
    }

    @Nested inner class
    `become changed` {

        @Test
        fun `if the transaction state changed`() {
            val entity = entityWithActiveTx(stateChanged = true)

            entity.changed() shouldBe true
        }

        @Test
        fun `once 'lifecycleFlags' are updated`() {
            val entity = newEntity()

            entity.setLifecycleFlags(lifecycleFlags { deleted = true })

            entity.changed() shouldBe true
        }
    }

    @Test
    fun `have null transaction by default`() {
        newEntity().transaction().shouldBeNull()
    }

    @Nested inner class
    `have no transaction in progress` {

        @Test
        fun `by default`() {
            newEntity().transactionInProgress() shouldBe false
        }

        @Test
        fun `until the transaction started`() {
            val entity = newEntity(activeTx = false, stateChanged = false)

            entity.transactionInProgress() shouldBe false
        }
    }

    @Test
    fun `have transaction in progress when the transaction is active`() {
        val entity = entityWithActiveTx(stateChanged = false)

        entity.transactionInProgress() shouldBe true
    }

    @Test
    fun `allow injecting a transaction`() {
        val entity = newEntity()
        val tx = StubTransaction(entity, /* active = */ true, /* stateChanged = */ true)

        entity.injectTransaction(tx)

        entity.transaction() shouldBe tx
    }

    @Test
    fun `disallow injecting a transaction wrapped around another entity instance`() {
        val entity = newEntity(activeTx = true, stateChanged = false)
        val anotherEntity = newEntity(activeTx = true, stateChanged = false)

        shouldThrow<IllegalStateException> {
            entity.injectTransaction(anotherEntity.activeTx())
        }
    }

    @Test
    fun `add 'update' block handler for passing properties to 'builder'`() {
        val entity = newEntity(activeTx = true, stateChanged = true)
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
        val entity = newEntity(activeTx = true, stateChanged = true)
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
    `fail to archive` {

        @Test
        fun `with no transaction`() {
            shouldThrow<IllegalStateException> { newEntity().markArchived() }
        }

        @Test
        fun `with an inactive transaction`() {
            val entity = newEntity(activeTx = false, stateChanged = false)

            shouldThrow<IllegalStateException> { entity.markArchived() }
        }
    }

    @Nested inner class
    `fail to delete` {

        @Test
        fun `with no transaction`() {
            shouldThrow<IllegalStateException> { newEntity().markDeleted() }
        }

        @Test
        fun `with an inactive transaction`() {
            val entity = newEntity(activeTx = false, stateChanged = false)

            shouldThrow<IllegalStateException> { entity.markDeleted() }
        }
    }

    @Test
    fun `return transaction 'lifecycleFlags' if the transaction is active`() {
        val entity = entityWithActiveTx(stateChanged = true)

        // With an active transaction, the flags are read from the transaction,
        // so a change made within it is reflected immediately.
        entity.lifecycleFlags().deleted shouldBe false

        entity.markDeleted()

        entity.lifecycleFlags().deleted shouldBe true
    }

    @Nested inner class
    `return builder from state` {

        @Test
        fun `which is non-null`() {
            val builder = Transaction.toBuilder(newEntity())

            builder.shouldNotBeNull()
        }

        @Test
        fun `which reflects the current state`() {
            val entity = newEntity()
            val originalState = Transaction.toBuilder(entity).build()

            val newState = stringEntity {
                id = randomString()
                value = "modified"
            }
            newState shouldNotBe originalState

            TestTransaction.injectState(entity, newState, Versions.zero())
            val modifiedState = Transaction.toBuilder(entity).build()

            modifiedState shouldBe newState
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
 * Creates a [Fixture] entity with no transaction injected.
 */
private fun newEntity(): Fixture = Fixture()

/**
 * Creates a [Fixture] entity and injects a stub transaction with the given
 * activity and state-changed status, so that `TransactionalEntity.builder` and
 * the transaction-dependent operations are available.
 */
private fun newEntity(activeTx: Boolean, stateChanged: Boolean): Fixture {
    val entity = Fixture()
    entity.injectTransaction(StubTransaction(entity, activeTx, stateChanged))
    return entity
}

/**
 * Creates a [Fixture] entity with an active transaction reporting the given
 * state-changed status.
 */
private fun entityWithActiveTx(stateChanged: Boolean): Fixture =
    newEntity(activeTx = true, stateChanged = stateChanged)

/**
 * Creates a [StockKeeper] with the given state and injects a stub transaction
 * so that its live builder is available to `tryAlter`.
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
    return tx.builder.buildPartial()
}

/**
 * An entity exercising the state-modification and transaction-management members
 * of [TransactionalEntity].
 *
 * The `do*`/`mark*` methods and [activeTx]/[transactionInProgress] expose members
 * that are `protected` in [TransactionalEntity] to the test, which resides in the
 * same package but is not a subclass.
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

    /** Exposes the protected [tx] to the test. */
    fun activeTx() = tx()

    /** Exposes the protected [isTransactionInProgress] to the test. */
    fun transactionInProgress(): Boolean = isTransactionInProgress()

    /** Exposes the protected [setArchived] to the test. */
    fun markArchived() = setArchived(true)

    /** Exposes the protected [setDeleted] to the test. */
    fun markDeleted() = setDeleted(true)
}
