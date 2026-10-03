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

package io.spine.server.entity

import com.google.common.testing.EqualsTester
import com.google.protobuf.FieldMask
import com.google.protobuf.fieldMask
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.server.BoundedContextBuilder
import io.spine.server.given.organizations.Organization
import io.spine.server.given.organizations.OrganizationId
import io.spine.server.given.organizations.organization
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`DefaultConverter` should")
internal class DefaultConverterSpec {

    private lateinit var converter: StorageConverter<OrganizationId, TestEntity, Organization>

    @BeforeEach
    fun setUp() {
        val context = BoundedContextBuilder.assumingTests().build()
        val repo: RecordBasedRepository<OrganizationId, TestEntity, Organization> =
            TestRepository()
        context.internalAccess()
            .register(repo)

        val stateType = repo.entityModelClass().stateTypeUrl()
        converter = DefaultConverter(stateType, repo.entityFactory())
    }

    @Test
    @Suppress("DEPRECATION") // Reason: verifies the deprecated no-op; delete with the API.
    fun `have the default 'FieldMask'`() {
        converter.fieldMask() shouldBe FieldMask.getDefaultInstance()
    }

    @Test
    @Suppress("DEPRECATION") // Reason: verifies the deprecated no-op; delete with the API.
    fun `return itself when given a 'FieldMask'`() {
        val mask = fieldMask { paths += "foo.bar" }

        converter.withFieldMask(mask) shouldBeSameInstanceAs converter
    }

    @Test
    @Suppress("DEPRECATION") // Reason: verifies the deprecated no-op; delete with the API.
    fun `ignore the 'FieldMask' passed to the deprecated constructor`() {
        val legacy = object : StorageConverter<OrganizationId, TestEntity, Organization>(
            converter.entityStateType(),
            converter.entityFactory(),
            fieldMask { paths += "foo.bar" }
        ) {
            override fun updateBuilder(builder: EntityRecord.Builder, entity: TestEntity) = Unit

            override fun injectState(
                entity: TestEntity,
                state: Organization,
                entityRecord: EntityRecord
            ) = Unit
        }

        legacy.fieldMask() shouldBe FieldMask.getDefaultInstance()
        legacy shouldBe converter
    }

    @Test
    fun `support equality`() {
        val sameFields = DefaultConverter<OrganizationId, TestEntity, Organization>(
            converter.entityStateType(),
            converter.entityFactory()
        )
        val repoFactory = converter.entityFactory()
        val otherFactory = DefaultConverter<OrganizationId, TestEntity, Organization>(
            converter.entityStateType(),
            object : EntityFactory<TestEntity> by repoFactory {}
        )
        EqualsTester()
            .addEqualityGroup(converter, sameFields)
            .addEqualityGroup(otherFactory)
            .testEquals()
    }

    @Test
    fun `convert forward and backward`() {
        val orgId = OrganizationId.generate()
        val entityState = organization {
            name = "back and forth"
            id = orgId
        }
        val entity = createEntity(orgId, entityState)

        val out = converter.convert(entity)
        val back = converter.reverse().convert(out)
        back shouldBe entity
    }

    private fun createEntity(id: OrganizationId, state: Organization): TestEntity {
        val result = TestEntity(id)
        result.setState(state)
        return result
    }

    /**
     * A test entity class that is not versionable.
     */
    private class TestEntity(id: OrganizationId) :
        AbstractEntity<OrganizationId, Organization>(id)

    /**
     * A test repository.
     */
    private class TestRepository :
        AbstractEntityRepository<OrganizationId, TestEntity, Organization>()
}
