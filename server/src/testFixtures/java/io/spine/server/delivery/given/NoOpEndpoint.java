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

import io.spine.base.Identifier;
import io.spine.core.MessageId;
import io.spine.server.delivery.MessageEndpoint;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.Repository;
import io.spine.server.type.CommandEnvelope;

import static io.spine.server.dispatch.DispatchOutcomes.successfulOutcome;
import static io.spine.testing.TestValues.nullRef;

public class NoOpEndpoint implements MessageEndpoint<String, CommandEnvelope> {

    @Override
    public DispatchOutcome dispatchTo(String targetId) {
        var id = MessageId.newBuilder()
                .setId(Identifier.pack(targetId))
                .build();
        return successfulOutcome(id);
    }

    @Override
    public void onDuplicate(String target, CommandEnvelope envelope) {
        // do nothing.
    }

    @Override
    public Repository<String, ?> repository() {
        return nullRef();
    }
}
