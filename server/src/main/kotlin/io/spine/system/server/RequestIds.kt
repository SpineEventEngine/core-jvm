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

import io.spine.client.Query
import io.spine.client.Subscription
import io.spine.client.Topic
import io.spine.core.Command
import io.spine.core.MessageId
import io.spine.protobuf.AnyPacker
import io.spine.validation.NonValidated

/**
 * Obtains the IDs of client requests, by which diagnostic events refer to the requests.
 *
 * The ID tells the type of the request message too. A request may be invalid,
 * so the IDs are not validated.
 */
internal object RequestIds {

    /**
     * Obtains the ID of the given command and the type URL of its message.
     *
     * The command may be invalid, so the result is not validated.
     */
    @JvmStatic
    fun of(command: Command): @NonValidated MessageId =
        messageId(AnyPacker.pack(command.id), command.message.typeUrl)

    /**
     * Obtains the ID of the given query and the type of its target.
     *
     * The query may be invalid, so the result is not validated.
     */
    @JvmStatic
    fun of(query: Query): @NonValidated MessageId =
        messageId(AnyPacker.pack(query.id), query.target.type)

    /**
     * Obtains the ID of the given topic and the type of its target.
     *
     * The topic may be invalid, so the result is not validated.
     */
    @JvmStatic
    fun of(topic: Topic): @NonValidated MessageId =
        messageId(AnyPacker.pack(topic.id), topic.target.type)

    /**
     * Obtains the ID of the given subscription and the type of the target of its topic.
     *
     * The subscription may be invalid, so the result is not validated.
     */
    @JvmStatic
    fun of(subscription: Subscription): @NonValidated MessageId =
        messageId(AnyPacker.pack(subscription.id), subscription.topic.target.type)

    private fun messageId(id: com.google.protobuf.Any, typeUrl: String): @NonValidated MessageId =
        MessageId.newBuilder()
            .setId(id)
            .setTypeUrl(typeUrl)
            .buildPartial()
}
