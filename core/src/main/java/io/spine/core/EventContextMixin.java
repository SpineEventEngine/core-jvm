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

package io.spine.core;

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Timestamp;
import io.spine.annotation.GeneratedMixin;
import io.spine.annotation.Internal;
import io.spine.base.Identifier;
import io.spine.logging.WithLogging;
import io.spine.validation.FieldAwareMessage;

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
     * <p>If at some point the event origin is not set, an {@link IllegalStateException} is
     * thrown as it contradicts the Spine validation rules. See {@link EventContext} proto
     * declaration.
     *
     * @see #findActorContext()
     */
    @Override
    default ActorContext actorContext() {
        return findActorContext().orElseThrow(
                () -> newIllegalStateException(
                        "The provided event context has no origin defined."
                )
        );
    }

    /**
     * Finds an actor context for the event context.
     *
     * <p>Traverses the event origin in the same way as {@link #actorContext()}, but does not
     * throw if the origin is not set. Use this method for an event context that has not passed
     * the validation yet.
     *
     * @return the actor context, or {@code Optional.empty()} if the origin of this context,
     *         or of any event context in its origin chain, is not set
     */
    @Internal
    @SuppressWarnings("deprecation") // For backward compatibility.
    default Optional<ActorContext> findActorContext() {
        return switch (getOriginCase()) {
            case COMMAND_CONTEXT -> Optional.of(getCommandContext().getActorContext());
            case EVENT_CONTEXT -> getEventContext().findActorContext();
            case PAST_MESSAGE -> Optional.of(getPastMessage().getActorContext());
            case IMPORT_CONTEXT -> Optional.of(getImportContext());
            case ORIGIN_NOT_SET -> Optional.empty();
        };
    }

    /**
     * Obtains the ID of the first signal in a chain.
     *
     * <p>The root message is either a {@code Command} or an {@code Event} that was produced by
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
     * Obtains the ID of the entity that produced the event.
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
     * Reads the values of the fields without using reflection.
     *
     * <p>During the validation of the contents this call reduces the cost of value extraction.
     * It is needed to improve the performance. However, some of the fields in {@code EventContext}
     * are deprecated, so the respective warnings are suppressed.
     */
    @SuppressWarnings({"OverlyComplexMethod", "MagicNumber", "deprecation"})    // see the docs.
    @Override
    @Internal
    default Object readValue(FieldDescriptor field) {
        return switch (field.getIndex()) {
            case 0 -> getTimestamp();
            case 1 -> getCommandContext();
            case 2 -> getEventContext();
            case 3 -> getPastMessage();
            case 4 -> getImportContext();
            case 5 -> getCommandId();
            case 6 -> getEventId();
            case 7 -> getRootCommandId();
            case 8 -> getProducerId();
            case 9 -> getVersion();
            case 10 -> getEnrichment();
            case 11 -> getExternal();
            case 12 -> getRejection();
            default -> getField(field);
        };
    }
}
