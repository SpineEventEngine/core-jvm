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

package io.spine.system.server

import com.google.protobuf.StringValue
import io.kotest.matchers.shouldBe
import io.spine.client.Query
import io.spine.client.QueryId
import io.spine.client.Subscription
import io.spine.client.SubscriptionId
import io.spine.client.Target
import io.spine.client.Topic
import io.spine.client.TopicId
import io.spine.core.Command
import io.spine.core.CommandId
import io.spine.protobuf.AnyPacker.pack
import io.spine.protobuf.AnyPacker.unpack
import io.spine.validation.NonValidated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`RequestIds` should")
internal class RequestIdsSpec {

    @Test
    fun `obtain the ID of a command and the type of its message`() {
        val command = command()

        val id = RequestIds.of(command)

        unpack(id.id, CommandId::class.java) shouldBe command.id
        id.typeUrl shouldBe command.message.typeUrl
    }

    @Test
    fun `obtain the ID of a query and the type of its target`() {
        val query = query(target())

        val id = RequestIds.of(query)

        unpack(id.id, QueryId::class.java) shouldBe query.id
        id.typeUrl shouldBe TARGET_TYPE
    }

    @Test
    fun `obtain the ID of a topic and the type of its target`() {
        val topic = topic()

        val id = RequestIds.of(topic)

        unpack(id.id, TopicId::class.java) shouldBe topic.id
        id.typeUrl shouldBe TARGET_TYPE
    }

    @Test
    fun `obtain the ID of a subscription and the type of the target of its topic`() {
        val subscription = subscription()

        val id = RequestIds.of(subscription)

        unpack(id.id, SubscriptionId::class.java) shouldBe subscription.id
        id.typeUrl shouldBe TARGET_TYPE
    }

    @Test
    fun `obtain the ID of an invalid query without failing`() {
        val query = query(target = null)

        val id = RequestIds.of(query)

        unpack(id.id, QueryId::class.java) shouldBe query.id
        id.typeUrl shouldBe ""
    }

    private companion object {

        const val TARGET_TYPE = "type.spine.io/spine.test.system.server.Company"

        fun target(): @NonValidated Target = Target.newBuilder()
            .setType(TARGET_TYPE)
            .buildPartial()

        fun command(): @NonValidated Command = Command.newBuilder()
            .setId(CommandId.generate())
            .setMessage(pack(StringValue.of("Command message.")))
            .buildPartial()

        fun query(target: Target?): @NonValidated Query {
            val builder = Query.newBuilder()
                .setId(QueryId.newBuilder().setValue("q-1"))
            target?.let { builder.setTarget(it) }
            return builder.buildPartial()
        }

        fun topic(): @NonValidated Topic = Topic.newBuilder()
            .setId(TopicId.newBuilder().setValue("t-1"))
            .setTarget(target())
            .buildPartial()

        fun subscription(): @NonValidated Subscription = Subscription.newBuilder()
            .setId(SubscriptionId.newBuilder().setValue("s-1"))
            .setTopic(topic())
            .buildPartial()
    }
}
