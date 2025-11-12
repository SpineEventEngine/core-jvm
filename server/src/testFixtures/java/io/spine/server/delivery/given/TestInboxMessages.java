/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.server.delivery.given;

import com.google.protobuf.Timestamp;
import io.spine.base.Identifier;
import io.spine.base.Time;
import io.spine.client.EntityId;
import io.spine.core.Command;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.InboxId;
import io.spine.server.delivery.InboxLabel;
import io.spine.server.delivery.InboxMessage;
import io.spine.server.delivery.InboxMessageId;
import io.spine.server.delivery.InboxMessageMixin;
import io.spine.server.delivery.InboxMessageStatus;
import io.spine.server.delivery.InboxSignalId;
import io.spine.test.delivery.AddNumber;
import io.spine.test.delivery.DTask;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.type.TypeUrl;

import static io.spine.server.delivery.InboxMessageStatus.DELIVERED;
import static io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP;
import static io.spine.server.delivery.InboxMessageStatus.TO_DELIVER;

/**
 * Provides the instances of {@link InboxMessage}s to use as a data in tests.
 */
public final class TestInboxMessages {

    private static final TestActorRequestFactory factory =
            new TestActorRequestFactory(TestInboxMessages.class);

    /**
     * Does not allow to instantiate this utility class.
     */
    private TestInboxMessages() {
    }

    /**
     * Copies the original {@code InboxMessage} but generates a new {@link InboxMessageId}
     * for the copy.
     */
    public static InboxMessage copyWithNewId(InboxMessage original) {
        return original.toBuilder()
                .setId(InboxMessageMixin.generateIdWith(original.shardIndex()))
                .build();
    }

    /**
     * Copies the original {@code InboxMessage} but sets the specified status to the copy.
     */
    public static InboxMessage copyWithStatus(InboxMessage original, InboxMessageStatus newStatus) {
        return original.toBuilder()
                .setStatus(newStatus)
                .build();
    }

    /**
     * Generates a new {@code InboxMessage} in
     * {@link io.spine.server.delivery.InboxMessageStatus#TO_CATCH_UP TO_CATCH_UP} status.
     *
     * @param targetId
     *         the ID of the target for the generated message
     * @param targetType
     *         the type URL of the target for the generated message
     * @return an instance of the generated message
     */
    public static InboxMessage catchingUp(Object targetId, TypeUrl targetType) {
        return newMessage(targetId, targetType, TO_CATCH_UP);
    }

    /**
     * Generates a new {@code InboxMessage} in
     * {@link io.spine.server.delivery.InboxMessageStatus#TO_CATCH_UP TO_CATCH_UP} status
     * and the receiving time specified.
     *
     * @param targetId
     *         the ID of the target for the generated message
     * @param targetType
     *         the type URL of the target for the generated message
     * @param whenReceived
     *         the message receiving time to set
     * @return an instance of the generated message
     */
    public static InboxMessage catchingUp(Object targetId,
                                          TypeUrl targetType,
                                          Timestamp whenReceived) {
        return newMessage(targetId, targetType, TO_CATCH_UP)
                .toBuilder()
                .setWhenReceived(whenReceived)
                .build();
    }

    /**
     * Generates a new {@code InboxMessage} in
     * {@link io.spine.server.delivery.InboxMessageStatus#TO_DELIVER TO_DELIVER} status.
     *
     * @param targetId
     *         the ID of the target for the generated message
     * @param targetType
     *         the type URL of the target for the generated message
     * @return an instance of the generated message
     */
    public static InboxMessage toDeliver(Object targetId, TypeUrl targetType) {
        return newMessage(targetId, targetType, TO_DELIVER);
    }

    /**
     * Generates a new {@code InboxMessage} in
     * {@link io.spine.server.delivery.InboxMessageStatus#TO_DELIVER TO_DELIVER} status and
     * the receiving time specified.
     *
     * @param targetId
     *         the ID of the target for the generated message
     * @param targetType
     *         the type URL of the target for the generated message
     * @param whenReceived
     *         the message receiving time to set
     * @return an instance of the generated message
     */
    public static InboxMessage toDeliver(Object targetId,
                                         TypeUrl targetType,
                                         Timestamp whenReceived) {
        return newMessage(targetId, targetType, TO_DELIVER)
                .toBuilder()
                .setWhenReceived(whenReceived)
                .build();
    }

    /**
     * Generates a new {@code InboxMessage} in
     * {@link io.spine.server.delivery.InboxMessageStatus#DELIVERED DELIVERED} status.
     *
     * @param targetId
     *         the ID of the target for the generated message
     * @param targetType
     *         the type URL of the target for the generated message
     * @return an instance of the generated message
     */
    public static InboxMessage delivered(Object targetId, TypeUrl targetType) {
        return newMessage(targetId, targetType, DELIVERED);
    }

    /**
     * Generates a new {@code InboxMessage} in
     * {@link io.spine.server.delivery.InboxMessageStatus#TO_DELIVER TO_DELIVER} status on top
     * of the passed command in  and sets the receiving time according to the passed value.
     */
    public static InboxMessage toDeliver(Command source, Timestamp whenReceived) {
        var inboxId = newInboxId("some-target", TypeUrl.of(DTask.class));
        var message = messageReceivedAt(source, TO_DELIVER, inboxId, whenReceived);
        return message;
    }

    private static InboxMessage newMessage(Object target, TypeUrl type, InboxMessageStatus status) {
        var command = generateCommand(target);
        var inboxId = newInboxId(target, type);
        var message = messageReceivedAt(command, status, inboxId, Time.currentTime());
        return message;
    }

    private static InboxMessage messageReceivedAt(Command command,
                                                  InboxMessageStatus status,
                                                  InboxId inboxId,
                                                  Timestamp whenReceived) {
        var index = DeliveryStrategy.newIndex(0, 1);
        var id = InboxMessageMixin.generateIdWith(index);
        var signalId = InboxSignalId.newBuilder()
                .setValue(command.getId().value());
        var result = InboxMessage.newBuilder()
                .setId(id)
                .setStatus(status)
                .setCommand(command)
                .setInboxId(inboxId)
                .setSignalId(signalId)
                .setLabel(InboxLabel.HANDLE_COMMAND)
                .setWhenReceived(whenReceived)
                .build();
        return result;
    }

    private static InboxId newInboxId(Object targetId, TypeUrl targetType) {
        return InboxId.newBuilder()
                .setEntityId(EntityId.newBuilder()
                                     .setId(Identifier.pack(targetId))
                                     .build())
                .setTypeUrl(targetType.value())
                .build();
    }

    private static Command generateCommand(Object targetId) {
        var commandMessage = AddNumber.newBuilder()
                .setCalculatorId("some-id-" + targetId)
                .setValue(targetId.hashCode())
                .build();
        return factory.createCommand(commandMessage);
    }
}
