/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.entity;

import com.google.common.testing.SerializableTester;
import com.google.protobuf.FieldMask;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.given.organizations.Organization;
import io.spine.server.given.organizations.OrganizationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.server.entity.DefaultConverter.forAllFields;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("`DefaultConverter` should")
class DefaultConverterTest {

    private StorageConverter<OrganizationId, TestEntity, Organization> converter;

    @BeforeEach
    void setUp() {
        var context = BoundedContextBuilder.assumingTests().build();
        RecordBasedRepository<OrganizationId, TestEntity, Organization> repo = new TestRepository();
        context.internalAccess()
               .register(repo);

        var stateType = repo.entityModelClass().stateTypeUrl();
        converter = forAllFields(stateType, repo.entityFactory());
    }

    @Test
    @DisplayName("create instance for all fields")
    void createForAllFields() {
        assertEquals(FieldMask.getDefaultInstance(), converter.fieldMask());
    }

    @Test
    @DisplayName("create instance with `FieldMask`")
    void createWithFieldMask() {
        var fieldMask = FieldMask.newBuilder()
                .addPaths("foo.bar")
                .build();

        var withMasks = converter.withFieldMask(fieldMask);

        assertEquals(fieldMask, withMasks.fieldMask());
    }

    private static TestEntity createEntity(OrganizationId id, Organization state) {
        var result = new TestEntity(id);
        result.setState(state);
        return result;
    }

    @Test
    @DisplayName("convert forward and backward")
    void convertForwardAndBackward() {
        var id = OrganizationId.generate();
        var entityState = Organization.newBuilder()
                .setName("back and forth")
                .setId(id)
                .build();
        var entity = createEntity(id, entityState);

        var out = converter.convert(entity);
        var back = converter.reverse().convert(out);
        assertEquals(entity, back);
    }

    @Test
    @DisplayName("be serializable")
    void beSerializable() {
        SerializableTester.reserialize(converter);
    }

    /**
     * A test entity class which is not versionable.
     */
    private static class TestEntity extends AbstractEntity<OrganizationId, Organization> {

        private TestEntity(OrganizationId id) {
            super(id);
        }
    }

    /**
     * A test repository.
     */
    private static class TestRepository
            extends DefaultRecordBasedRepository<OrganizationId, TestEntity, Organization> {
    }
}
