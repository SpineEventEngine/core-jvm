/*
 * Copyright 2023, TeamDev. All rights reserved.
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
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Timestamp;
import io.spine.annotation.GeneratedMixin;
import io.spine.annotation.Internal;
import io.spine.base.Identifier;
import io.spine.logging.WithLogging;
import io.spine.validate.FieldAwareMessage;

import java.util.Optional;

import static io.spine.type.ProtoTexts.shortDebugString;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.lang.String.format;

/**
 * Mixin interface for {@link EventContext}s.
 */
@GeneratedMixin
@Immutable
interface EventContextMixin extends EventContextOrBuilder,
                                    SignalContext,
                                    WithTime,
                                    EnrichableMessageContext,
                                    FieldAwareMessage,
                                    WithLogging {

    /**
     * Obtains an actor context for the event context.
     *
     * <p>The context is obtained by traversing the events origin for a valid context source.
     * There can be three sources for the actor context:
     * <ol>
     *     <li>The command context set as the event origin.
     *     <li>The command context of an event which is an origin of this event.
     *     <li>The import context if the event is imported to an aggregate.
     * </ol>
     *
     * <p>If at some point the event origin is not set, an {@link IllegalArgumentException} is
     * thrown as it contradicts the Spine validation rules. See {@link EventContext} proto
     * declaration.
     */
    @Override
    @SuppressWarnings({
            "ClassReferencesSubclass", // which is the only impl.
            "deprecation" // For backward compatibility.
    })
    default ActorContext actorContext() {
        ActorContext actorContext = null;
        var ctx = (EventContext) this;

        while (actorContext == null) {
            switch (ctx.getOriginCase()) {
                case COMMAND_CONTEXT:
                    actorContext = ctx.getCommandContext()
                                      .getActorContext();
                    break;
                case EVENT_CONTEXT:
                    ctx = ctx.getEventContext();
                    break;
                case PAST_MESSAGE:
                    actorContext = ctx.getPastMessage()
                                      .getActorContext();
                    break;
                case IMPORT_CONTEXT:
                    actorContext = ctx.getImportContext();
                    break;
                case ORIGIN_NOT_SET:
                default:
                    throw newIllegalStateException(
                            "The provided event context has no origin defined."
                    );
            }
        }
        return actorContext;
    }

    /**
     * Obtains the ID of the first signal in a chain.
     *
     * <p>The root message is either a {@code Command} or an {@code Event} which was produced by
     * an actor directly and caused the associated {@code Event} to be emitted.
     *
     * <p>If the associated {@code Event} itself is the root of its chain, i.e. it was imported into
     * the system, the ID cannot be assembled and thus an {@code Optional.empty()} is returned.
     *
     * <p>If the origin cannot be determined, an {@code Optional.empty()} is returned.
     *
     * @see Event#rootMessage()
     */
    @SuppressWarnings("deprecation") // For backward compatibility.
    default Optional<MessageId> rootMessage() {
        var origin = getOriginCase();
        switch (origin) {
            case PAST_MESSAGE:
                return Optional.of(getPastMessage().root());
            case IMPORT_CONTEXT:
                return Optional.empty();
            case EVENT_CONTEXT:
            case COMMAND_CONTEXT:
            case ORIGIN_NOT_SET:
            default:
                if (hasRootCommandId()) {
                    var id = MessageId.newBuilder()
                            .setId(Identifier.pack(getRootCommandId()))
                            .setTypeUrl("Unknown")
                            .build();
                    return Optional.of(id);
                } else {
                    logger().atWarning().log(() -> format(
                            "Cannot determine root message ID. Event context: `%s`.",
                            shortDebugString(this)));
                    return Optional.empty();
                }
        }
    }

    /**
     * Obtains the ID of the entity which produced the event.
     */
    default Object producer() {
        @SuppressWarnings("ClassReferencesSubclass")  // which is the only impl.
        var thisContext = (EventContext) this;
        return Identifier.unpack(thisContext.getProducerId());
    }

    /**
     * Obtains the time of the event as {@link Timestamp}.
     *
     * @see #instant()
     */
    @Override
    default Timestamp timestamp() {
        return getTimestamp();
    }

    /**
     * Reads the values of the fields without using the reflection.
     *
     * <p>During the validation of the contents this call reduces the cost of value extraction.
     * It is needed to improve the performance. However, some of the fields in {@code EventContext}
     * are deprecated, so the respective warnings are suppressed.
     */
    @SuppressWarnings({"OverlyComplexMethod", "MagicNumber", "deprecation"})    // see the docs.
    @Override
    @Internal
    default Object readValue(FieldDescriptor field) {
        switch (field.getIndex()) {
            case 0:
                return getTimestamp();
            case 1:
                return getCommandContext();
            case 2:
                return getEventContext();
            case 3:
                return getPastMessage();
            case 4:
                return getImportContext();
            case 5:
                return getCommandId();
            case 6:
                return getEventId();
            case 7:
                return getRootCommandId();
            case 8:
                return getProducerId();
            case 9:
                return getVersion();
            case 10:
                return getEnrichment();
            case 11:
                return getExternal();
            case 12:
                return getRejection();
            default:
                return getField(field);
        }
    }
}
