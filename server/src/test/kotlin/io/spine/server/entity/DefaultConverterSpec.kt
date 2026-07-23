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

import com.google.common.testing.EqualsTester
import com.google.protobuf.FieldMask
import io.kotest.matchers.shouldBe
import io.spine.server.BoundedContextBuilder
import io.spine.server.entity.DefaultConverter.Companion.forAllFields
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
        converter = forAllFields(stateType, repo.entityFactory())
    }

    @Test
    fun `create instance for all fields`() {
        converter.fieldMask() shouldBe FieldMask.getDefaultInstance()
    }

    @Test
    fun `create instance with 'FieldMask'`() {
        val fieldMask = FieldMask.newBuilder()
            .addPaths("foo.bar")
            .build()

        val withMasks = converter.withFieldMask(fieldMask)

        withMasks.fieldMask() shouldBe fieldMask
    }

    @Test
    fun `support equality`() {
        val sameFields = forAllFields(converter.entityStateType(), converter.entityFactory())
        val masked = converter.withFieldMask(
            FieldMask.newBuilder()
                .addPaths("foo.bar")
                .build()
        )
        EqualsTester()
            .addEqualityGroup(converter, sameFields)
            .addEqualityGroup(masked)
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
