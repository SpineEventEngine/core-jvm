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

package io.spine.client

import com.google.protobuf.fieldMask
import io.kotest.matchers.shouldBe
import io.spine.base.Identifier.newUuid
import io.spine.client.EntityQueryToProto.transformWith
import io.spine.test.client.TestEntity
import io.spine.testing.core.given.GivenUserId
import io.spine.time.ZoneIds
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`EntityQueryToProto` should")
internal class EntityQueryToProtoSpec {

    private val factory = ActorRequestFactory.newBuilder()
        .setZoneId(ZoneIds.systemDefault())
        .setActor(GivenUserId.of(newUuid()))
        .build()
        .query()

    @Test
    fun `not transfer the field mask of an entity query`() {
        val query = TestEntity.query()
            .withMask(fieldMask { paths += "first_field" })
            .build(transformWith(factory))

        query.format shouldBe ResponseFormat.getDefaultInstance()
    }
}
