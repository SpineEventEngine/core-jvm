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

package io.spine.core;

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import io.spine.annotation.GeneratedMixin;
import io.spine.annotation.Internal;
import io.spine.annotation.SPI;
import io.spine.base.MessageContext;
import io.spine.protobuf.AnyPacker;
import io.spine.type.KnownMessage;
import io.spine.type.SerializableMessage;
import io.spine.type.TypeUrl;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.protobuf.AnyPacker.pack;

/**
 * A message which can be dispatched and cause other messages.
 *
 * <p>A signal message travels through the system just like an electronic signal travels through
 * a neural network. It cases the system to change its state either directly or by exciting other
 * signals.
 *
 * <p>A signal message may originate from outside the system, from a user, or from a policy
 * implemented in the system.
 *
 * @param <I>
 *         the type of the signal identifier
 * @param <M>
 *         the type of the enclosed messages
 * @param <C>
 *         the type of the message context
 */
@SPI
@Immutable
@GeneratedMixin
public interface Signal<I extends SignalId,
                        M extends KnownMessage,
                        C extends MessageContext>
        extends SerializableMessage, WithActor, WithTime {

    /**
     * Obtains the identifier of the message.
     */
    I getId();

    /**
     * Obtains the packed version of the enclosed message.
     *
     * @see #enclosedMessage()
     */
    Any getMessage();

    /**
     * Obtains the context of the enclosed message.
     */
    C getContext();

    /**
     * Obtains the identifier of the message.
     */
    default I id() {
        return getId();
    }

    /**
     * Obtains the type of the signal as the type of the {@linkplain #enclosedMessage()
     * enclosed} {@code Message}.
     */
    default Class<? extends M> type() {
        @SuppressWarnings("unchecked") // Safe as we obtain it from an instance of <M>.
        var type = (Class<? extends M>) enclosedMessage().getClass();
        return type;
    }

    /**
     * Obtains the unpacked form of the enclosed message.
     *
     * @see #getMessage()
     */
    @SuppressWarnings("unchecked") // protected by generic params of extending interfaces
    default M enclosedMessage() {
        var enclosed = AnyPacker.unpack(getMessage());
        return (M) enclosed;
    }

    /**
     * Obtains the context of the enclosed message.
     */
    default C context() {
        return getContext();
    }

    /**
     * Obtains the type URL of the enclosed message.
     */
    default TypeUrl enclosedTypeUrl() {
        return enclosedMessage().typeUrl();
    }

    /**
     * Verifies if the enclosed message has the same type as the passed, or the passed type
     * is the super-type of the message.
     */
    default boolean is(Class<? extends Message> enclosedMessageClass) {
        checkNotNull(enclosedMessageClass);
        Message enclosed = enclosedMessage();
        var result = enclosedMessageClass.isAssignableFrom(enclosed.getClass());
        return result;
    }

    /**
     * Obtains the ID of this message.
     */
    default MessageId messageId() {
        return identityBuilder().build();
    }

    /**
     * Creates the builder for identity of the message, by supplying the ID and the type URL
     * of the enclosed message.
     */
    @Internal
    default MessageId.Builder identityBuilder() {
        return MessageId.newBuilder()
                .setId(pack(id()))
                .setTypeUrl(enclosedTypeUrl().value());
    }

    /**
     * Obtains this signal as an origin of other signals.
     *
     * <p>This origin is assigned to any signal message produced as a reaction to this one.
     */
    default Origin asMessageOrigin() {
        var originBuilder = Origin.newBuilder()
                .setActorContext(actorContext())
                .setMessage(messageId());
        origin().ifPresent(originBuilder::setGrandOrigin);
        return originBuilder.build();
    }

    /**
     * Obtains the origin of this signal.
     *
     * @return origin of this signal or {@code Optional.empty()} if this signal is posted directly
     *         by an actor
     */
    Optional<Origin> origin();

    /**
     * Obtains the ID of the first message in the chain.
     *
     * <p>The root message is always produced by an actor directly. Tenant and actor of the root
     * message define the tenant and actor of the whole chain.
     */
    MessageId rootMessage();

    default Optional<MessageId> parent() {
        return origin().map(Origin::messageId);
    }
}
