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

package io.spine.server.bus;

import io.spine.annotation.SPI;
import io.spine.base.Error;
import io.spine.core.Ack;
import io.spine.server.Closeable;
import io.spine.server.type.MessageEnvelope;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.bus.MessageIdExtensions.acknowledge;
import static io.spine.server.bus.MessageIdExtensions.causedError;

/**
 * The filter for the messages posted to a bus.
 *
 * <p>A bus may have several filters that can prevent a message from being posted.
 *
 * @param <E>
 *         the type of the envelopes into which the messages posted to the bus are packed
 */
@SPI
@FunctionalInterface
public interface BusFilter<E extends MessageEnvelope<?, ?, ?>> extends Closeable {

    /**
     * Accepts or rejects a passed message.
     *
     * <p>A filter can:
     * <ul>
     *     <li>accept the message (by returning {@code Optional.empty()});
     *     <li>reject the message with an {@link io.spine.base.Error Error} status, for example, if
     *         it fails to pass the validation;
     *     <li>reject the message with an {@code OK} status. For example, a scheduled command
     *         may not pass a filter;
     *     <li>reject the message with a business rejection. For example, a command can be rejected
     *         if the user who made the request does not have enough permissions in the system.
     *         Such rejection is only applicable to commands.
     * </ul>
     *
     * @param envelope
     *         the envelope with the message to filter
     * @return {@code Optional.empty()} if the message passes the filter,
     *         {@linkplain Ack posting result} with either status otherwise
     */
    Optional<Ack> filter(E envelope);

    /**
     * Lets the message pass the filter.
     *
     * <p>This method is a shortcut that can be used in {@link #filter(MessageEnvelope)} when this
     * filter shouldn't prevent the message from being posted.
     */
    default Optional<Ack> letPass() {
        return Optional.empty();
    }

    /**
     * Rejects the message with the {@code OK} status.
     *
     * <p>This method is a shortcut that can be used in {@link #filter(MessageEnvelope)} when the
     * message does not pass the filter and this is expected.
     *
     * @param envelope
     *          the envelope with the message to filter
     * @return the {@code Optional.of(Ack)} signaling that the message does not pass the filter
     */
    default Optional<Ack> reject(E envelope) {
        checkNotNull(envelope);
        var ack = acknowledge(envelope.id());
        return Optional.of(ack);
    }

    /**
     * Rejects the message with an {@link io.spine.base.Error Error} status.
     *
     * <p>This method is a shortcut that can be used in {@link #filter(MessageEnvelope)} when the
     * message does not pass the filter due to a technical error or inconsistency in model
     * data.
     *
     * <p>The poster of the message does not receive the error. The bus replaces it with
     * an {@linkplain io.spine.core.ErrorCase error case}, which tells only the kind of
     * the error, and posts the error to the System context in a diagnostic event.
     * An error given by a filter of the application is reported to the poster as
     * {@link io.spine.core.ErrorCase#REQUEST_DENIED REQUEST_DENIED}.
     *
     * @param envelope
     *          the envelope with the message to filter
     * @param cause
     *         the cause of the rejection
     * @return the {@code Optional.of(Ack)} signaling that the message does not pass the filter
     */
    @SuppressWarnings("deprecation")
    // Reason: `Status.error` stays the carrier between a filter and its bus until the filter
    // API gets its own result type.
    default Optional<Ack> reject(E envelope, Error cause) {
        checkNotNull(envelope);
        checkNotNull(cause);
        var ack = causedError(envelope.id(), cause);
        return Optional.of(ack);
    }

    /**
     * {@inheritDoc}
     *
     * <p>By default, always returns {@code true} as most filters don't manage resources.
     */
    @Override
    default boolean isOpen() {
        return true;
    }

   /**
     * {@inheritDoc}
     *
     * <p>By default, performs no action.
     */
    @Override
    default void close() {
        // NoOp by default.
    }
}
