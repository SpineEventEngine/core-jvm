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

package io.spine.server.stand;

import io.spine.base.Time;
import io.spine.client.Query;
import io.spine.client.QueryId;
import io.spine.client.ResponseFormat;
import io.spine.client.Target;
import io.spine.core.ActorContext;
import io.spine.core.UserId;
import io.spine.server.stand.given.MenuRepository;
import io.spine.test.stand.Menu;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.base.Identifier.newUuid;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`QueryValidator` should")
class QueryValidatorTest {

    @Test
    @DisplayName("now allow `limit` without `order_by`")
    void limit() {
        var format = ResponseFormat.newBuilder()
                .setLimit(42)
                .build();
        var query = Query.newBuilder()
                .setContext(buildActorContext())
                .setId(queryId())
                .setTarget(buildTarget())
                .setFormat(format)
                .build();
        var typeRegistry = InMemoryTypeRegistry.newInstance();
        typeRegistry.register(new MenuRepository());
        var validator = new QueryValidator(typeRegistry);
        assertThrows(InvalidRequestException.class, () -> validator.validate(query));
    }

    private static ActorContext buildActorContext() {
        return ActorContext.newBuilder()
                .setActor(userId())
                .setTimestamp(Time.currentTime())
                .build();
    }

    private static Target buildTarget() {
        return Target.newBuilder()
                .setIncludeAll(true)
                .setType(TypeUrl.of(Menu.class).value())
                .build();
    }

    private static QueryId queryId() {
        return QueryId.newBuilder().setValue(newUuid()).build();
    }

    private static UserId userId() {
        return UserId.newBuilder().setValue(newUuid()).build();
    }
}
