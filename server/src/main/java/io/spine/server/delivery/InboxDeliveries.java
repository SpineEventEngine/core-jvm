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

package io.spine.server.delivery;

import io.spine.core.BoundedContextName;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Maps.newConcurrentMap;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.util.stream.Collectors.joining;

/**
 * The container for the {@linkplain ShardedMessageDelivery message deliveries} for each
 * {@link Inbox} registered.
 *
 * <p>An inbox is registered by the state type of its entities and by its Bounded Context.
 * Several contexts may have inboxes of one type: for example, every System context has
 * the same repositories. A message carries the context of the inbox that stored it.
 * It is delivered to the inbox of its target's type in that context.
 *
 * <p>The two types of the catch-up machinery, {@link CatchUpProcess} and
 * {@link ShardMaintenanceProcess}, are an exception: the System context of a catch-up message
 * is taken from its catch-up job, not from the message itself.
 * The job tells the System context this way:
 *
 * <ol>
 *     <li>the job tells the type of the projection it catches up;
 *     <li>the inbox of the type tells the domain context that serves it;
 *     <li>the domain context tells its System context.
 * </ol>
 */
final class InboxDeliveries {

    /**
     * The registered inboxes, by the state type of their entities and their context.
     */
    private final Map<InboxKey, Registered> inboxes = newConcurrentMap();

    /**
     * Obtains the proper delivery for the message passed according to its target.
     *
     * @throws IllegalStateException
     *         if there is no delivery found, or a catch-up message is addressed in the form of
     *         an earlier version of the framework
     */
    ShardedMessageDelivery<InboxMessage> get(InboxMessage message) {
        var inboxId = message.getInboxId();
        var type = TypeUrl.parse(inboxId.getTypeUrl());
        var context = isCatchUpType(type)
                      ? systemContextOf(inboxId)
                      : message.getContext();
        var registered = inboxes.get(new InboxKey(type, context));
        checkState(registered != null,
                   "Cannot find the registered Inbox for the type URL `%s` in the context `%s`.",
                   type, context.getValue());
        return registered.delivery;
    }

    /**
     * Registers the given {@code Inbox}.
     *
     * <p>If an inbox of the same type in the same context is registered already, replaces it.
     */
    void register(Inbox<?> inbox) {
        inboxes.put(keyOf(inbox), new Registered(inbox));
    }

    /**
     * Unregisters the given {@code Inbox}.
     *
     * <p>Does nothing if this {@code Inbox} is not registered. In particular, it does not
     * unregister an inbox of the same type in the same context, which replaced this one.
     */
    void unregister(Inbox<?> inbox) {
        var key = keyOf(inbox);
        var registered = inboxes.get(key);
        if (registered != null && registered.inbox == inbox) {
            inboxes.remove(key, registered);
        }
    }

    private static InboxKey keyOf(Inbox<?> inbox) {
        return new InboxKey(inbox.entityStateType(), inbox.contextName());
    }

    private static boolean isCatchUpType(TypeUrl type) {
        return CatchUpProcess.TYPE.equals(type)
                || ShardMaintenanceProcess.TYPE.equals(type);
    }

    /**
     * Finds the System context, to which the catch-up message with the passed address goes.
     *
     * <p>It is the System context of the domain context, which registered the inbox of
     * the projection type of the job.
     *
     * @throws IllegalStateException
     *         if no inbox, or more than one, is registered for the projection type of the job
     */
    private BoundedContextName systemContextOf(InboxId inboxId) {
        var job = jobOf(inboxId);
        var projectionType = TypeUrl.parse(job.getProjectionType());
        var contexts = inboxes.keySet()
                .stream()
                .filter(key -> key.type.equals(projectionType))
                .map(key -> key.context)
                .collect(toImmutableSet());
        checkState(!contexts.isEmpty(),
                   "No inbox is registered for the projection type `%s`" +
                           " of the catch-up job `%s`.",
                   projectionType, job.getUuid());
        checkState(contexts.size() == 1,
                   "The projection type `%s` of the catch-up job `%s` has inboxes" +
                           " in several Bounded Contexts: %s.",
                   projectionType, job.getUuid(),
                   contexts.stream()
                           .map(BoundedContextName::getValue)
                           .collect(joining(", ")));
        return contexts.iterator()
                       .next()
                       .toSystem();
    }

    /**
     * Obtains the catch-up job from the address of a message of the catch-up machinery.
     *
     * @throws IllegalStateException
     *         if the address is of the form written by an earlier version of the framework
     */
    @SuppressWarnings("ChainOfInstanceofChecks" /* An inbox target here could be either
       `CatchUpId` or `ShardMaintenanceId` that refers to a catch-up job. Having a common
       interface for them just to avoid the cast does not make much sense. */)
    private static CatchUpId jobOf(InboxId inboxId) {
        var target = InboxIds.unwrap(inboxId);
        if (target instanceof CatchUpId job) {
            return job;
        }
        if (target instanceof ShardMaintenanceId maintenance) {
            return maintenance.getJob();
        }
        throw newIllegalStateException(
                "Cannot deliver a message to `%s` with the ID `%s`: an earlier version of" +
                        " the framework wrote it. Before upgrading, let every catch-up" +
                        " complete, and the `Delivery` deliver every message.",
                inboxId.getTypeUrl(), target);
    }

    /**
     * A registered inbox and its delivery.
     */
    private static final class Registered {

        private final Inbox<?> inbox;
        private final ShardedMessageDelivery<InboxMessage> delivery;

        private Registered(Inbox<?> inbox) {
            this.inbox = inbox;
            this.delivery = inbox.delivery();
        }
    }

    /**
     * The key of a registered inbox.
     */
    private static final class InboxKey {

        private final TypeUrl type;
        private final BoundedContextName context;

        private InboxKey(TypeUrl type, BoundedContextName context) {
            this.type = type;
            this.context = context;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof InboxKey other)) {
                return false;
            }
            return type.equals(other.type)
                    && context.equals(other.context);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, context);
        }
    }
}
