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
import static com.google.common.collect.Maps.newConcurrentMap;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * The container for the {@linkplain ShardedMessageDelivery message deliveries} for each
 * {@link Inbox} registered.
 *
 * <p>The state type of the target of a message tells its inbox. One open Bounded Context
 * serves the entities of a state type, so there is one inbox per type.
 *
 * <p>The two types of the catch-up machinery, {@link CatchUpProcess} and
 * {@link ShardMaintenanceProcess}, are an exception. Every System context has a repository
 * of each, so each of these types has an inbox in every System context. The catch-up job
 * of a message tells which inbox to use:
 *
 * <ol>
 *     <li>the job tells the type of the projection it catches up;
 *     <li>the type tells the domain context that serves it;
 *     <li>the domain context tells its System context.
 * </ol>
 */
final class InboxDeliveries {

    /**
     * The inboxes of the types other than those of the catch-up machinery,
     * by the type URL.
     */
    private final Map<TypeUrl, Registered> byType = newConcurrentMap();

    /**
     * The inboxes of the catch-up machinery, by the type URL and the System context.
     */
    private final Map<CatchUpInbox, Registered> bySystemContext = newConcurrentMap();

    /**
     * Obtains the proper delivery for the message passed according to its target.
     *
     * @throws IllegalStateException
     *         if there is no delivery found, or the message is addressed in the form of
     *         an earlier version of the framework
     */
    ShardedMessageDelivery<InboxMessage> get(InboxMessage message) {
        var inboxId = message.getInboxId();
        var type = TypeUrl.parse(inboxId.getTypeUrl());
        if (!isCatchUpType(type)) {
            var registered = byType.get(type);
            checkState(registered != null,
                       "Cannot find the registered Inbox for the type URL `%s`.", type);
            return registered.delivery;
        }
        var systemContext = systemContextOf(inboxId);
        var registered = bySystemContext.get(new CatchUpInbox(type, systemContext));
        checkState(registered != null,
                   "Cannot find the registered Inbox for the type URL `%s` in the context `%s`.",
                   type, systemContext.getValue());
        return registered.delivery;
    }

    /**
     * Registers the given {@code Inbox}.
     *
     * <p>If an inbox for the same target is registered already, replaces it.
     */
    void register(Inbox<?> inbox) {
        var registered = new Registered(inbox);
        var type = inbox.entityStateType();
        if (isCatchUpType(type)) {
            bySystemContext.put(catchUpInboxOf(inbox), registered);
        } else {
            byType.put(type, registered);
        }
    }

    /**
     * Unregisters the given {@code Inbox}.
     *
     * <p>Does nothing if this {@code Inbox} is not registered. In particular, it does not
     * unregister an inbox of the same target, which replaced this one.
     */
    void unregister(Inbox<?> inbox) {
        var type = inbox.entityStateType();
        if (isCatchUpType(type)) {
            removeIfOf(inbox, catchUpInboxOf(inbox), bySystemContext);
        } else {
            removeIfOf(inbox, type, byType);
        }
    }

    /**
     * Removes the entry with the passed key, if it is the registration of the passed inbox.
     */
    private static <K> void removeIfOf(Inbox<?> inbox, K key, Map<K, Registered> registrations) {
        var registered = registrations.get(key);
        if (registered != null && registered.inbox == inbox) {
            registrations.remove(key, registered);
        }
    }

    private static boolean isCatchUpType(TypeUrl type) {
        return CatchUpProcess.TYPE.equals(type)
                || ShardMaintenanceProcess.TYPE.equals(type);
    }

    private static CatchUpInbox catchUpInboxOf(Inbox<?> inbox) {
        var type = inbox.entityStateType();
        var context = inbox.contextName();
        checkState(context != null,
                   "The inbox of `%s` must belong to a System context.", type);
        return new CatchUpInbox(type, context);
    }

    /**
     * Finds the System context, to which the catch-up message with the passed address goes.
     *
     * @throws IllegalStateException
     *         if no open context serves the projection type of the job
     */
    private static BoundedContextName systemContextOf(InboxId inboxId) {
        var job = jobOf(inboxId);
        return CatchUpProcess.domainContextOf(job)
                             .name()
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
     * The key of an inbox of the catch-up machinery.
     */
    private static final class CatchUpInbox {

        private final TypeUrl type;
        private final BoundedContextName systemContext;

        private CatchUpInbox(TypeUrl type, BoundedContextName systemContext) {
            this.type = type;
            this.systemContext = systemContext;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CatchUpInbox other)) {
                return false;
            }
            return type.equals(other.type)
                    && systemContext.equals(other.systemContext);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, systemContext);
        }
    }
}
