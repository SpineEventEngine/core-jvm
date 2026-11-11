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

import com.google.common.base.MoreObjects;
import io.spine.core.BoundedContextName;

import java.util.Objects;

/**
 * An identifier of the signal-to-target dispatched as {@code InboxMessage}.
 *
 * <p>The target is identified by its inbox and by the context of the inbox, because
 * several contexts may have inboxes of the same entity type.
 */
final class DispatchingId {

    private final InboxSignalId signal;
    private final InboxId inbox;
    private final BoundedContextName context;

    /**
     * Creates the dispatching identifier for the passed message.
     */
    DispatchingId(InboxMessage message) {
        this.signal = message.getSignalId();
        this.inbox = message.getInboxId();
        this.context = message.getContext();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var id = (DispatchingId) o;
        return Objects.equals(signal, id.signal) &&
                Objects.equals(inbox, id.inbox) &&
                Objects.equals(context, id.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signal, inbox, context);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("signal", signal)
                          .add("inbox", inbox)
                          .add("context", context)
                          .toString();
    }
}
