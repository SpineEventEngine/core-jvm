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

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.spine.base.Identifier.newUuid
import io.spine.environment.Tests
import io.spine.server.BoundedContextBuilder
import io.spine.server.ServerEnvironment
import io.spine.server.delivery.Delivery
import io.spine.server.delivery.InboxStorage
import io.spine.server.entity.AbstractEntity
import io.spine.server.model.given.filter.CreateProjectCommander
import io.spine.server.model.given.filter.CreateProjectEventCommander
import io.spine.server.model.given.filter.ModSplitAggregate
import io.spine.server.model.given.filter.ProjectCreatedReactor
import io.spine.server.model.given.filter.ProjectCreatedSubscriber
import io.spine.server.model.given.filter.ProjectTasksRepository
import io.spine.server.model.given.filter.ProjectTasksSubscriber
import io.spine.server.model.given.storage.ModelTestStorageFactory
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.server.under
import io.spine.test.model.modProjectCreated
import io.spine.testing.server.blackbox.BlackBox
import io.spine.testing.server.model.ModelTests.dropAllModels
import java.lang.reflect.InvocationTargetException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Handler methods with field filters should")
internal class FilterSpec {

    @AfterEach
    fun clearModel() {
        dropAllModels()
    }

    @Nested inner class
    `be OK on a` {

        @Test
        fun `'@Subscribe'-r`() {
            assertValid(ProjectCreatedSubscriber::class.java)
        }

        @Test
        fun `'@React'-or`() {
            assertValid(ProjectCreatedReactor::class.java)
        }

        @Test
        fun `event-accepting '@Command'-er`() {
            assertValid(CreateProjectEventCommander::class.java)
        }
    }

    @Nested inner class
    `be rejected in a` {

        @Test
        fun `'@Assign'-ed command-handling method`() {
            assertInvalid(ModSplitAggregate::class.java)
        }

        @Test
        fun `command-accepting '@Command'-er`() {
            assertInvalid(CreateProjectCommander::class.java)
        }

        @Test
        fun `state '@Subscribe'-r method`() {
            assertInvalid(ProjectTasksSubscriber::class.java)
        }
    }

    @Nested inner class
    `filter out events before they hit inbox` {

        private lateinit var storageFactory: StorageFactory
        private lateinit var inboxStorage: InboxStorage

        @BeforeEach
        fun prepareEnv() {
            storageFactory = ModelTestStorageFactory(InMemoryStorageFactory.newInstance())
            inboxStorage = storageFactory.createInboxStorage(false)
            val delivery = Delivery.newBuilder()
                .setInboxStorage(inboxStorage)
                .build()
            under<Tests> {
                use(delivery)
            }
        }

        @AfterEach
        fun closeEnv() {
            storageFactory.close()
            ServerEnvironment.instance().reset()
            dropAllModels()
        }

        @Test
        fun beforeDelivery() {
            val context = BoundedContextBuilder
                .assumingTests()
                .add(ProjectTasksRepository())
            val event = modProjectCreated {
                // Not the value expected in `ProjectTasksProjection`.
                id = newUuid()
            }
            BlackBox.from(context).use {
                it.receivesEvent(event)
                inboxStorage.index().hasNext() shouldBe false
            }
        }
    }

    private fun assertValid(classWithHandler: Class<*>) {
        shouldNotThrowAny {
            triggerModelConstruction(classWithHandler)
        }
    }

    private fun assertInvalid(classWithHandler: Class<*>) {
        shouldThrow<ModelError> {
            triggerModelConstruction(classWithHandler)
        }
    }

    private fun triggerModelConstruction(modelClass: Class<*>) {
        val instance = try {
            modelClass.getDeclaredConstructor().newInstance()
        } catch (e: InvocationTargetException) {
            // The model error thrown by the fixture constructor must surface as-is.
            throw e.cause ?: e
        }
        if (instance is AbstractEntity<*, *>) {
            instance.modelClass()
        }
    }
}
