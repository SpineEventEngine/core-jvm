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

package io.spine.server.aggregate.given.repo;

import com.google.common.base.Splitter;
import io.spine.base.CommandMessage;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.route.CommandRoute;
import io.spine.server.route.CommandRouting;
import io.spine.server.test.shared.LongIdAggregate;
import io.spine.test.aggregate.cli.Evaluate;
import io.spine.test.aggregate.cli.Evaluated;

import static java.lang.String.format;

/**
 * The aggregate repository under tests.
 *
 * <p>The repository accepts commands as {@code StringValue}s in the form:
 * {@code AggregateId-CommandMessage}.
 */
public class RepoOfAggregateWithLifecycle
        extends AggregateRepository<Long, AggregateWithLifecycle, LongIdAggregate> {

    private static final char SEPARATOR = '-';
    /**
     * Custom {@code IdCommandFunction} that parses an aggregate ID from {@code StringValue}.
     */
    private static final CommandRoute<Long, CommandMessage> parsingRoute =
            (msg, ctx) -> getId((Evaluate) msg);

    @Override
    protected void setupCommandRouting(CommandRouting<Long> routing) {
        super.setupCommandRouting(routing);
        routing.replaceDefault(parsingRoute);
    }

    /**
     * Creates a command message in the form {@code <id>-<action>}.
     *
     * @see #getId(io.spine.test.aggregate.cli.Evaluate)
     * @see #getMessage(io.spine.test.aggregate.cli.Evaluated)
     */
    public static Evaluate createCommandMessage(Long id, String msg) {
        return Evaluate.newBuilder()
                .setCmd(format("%d%s%s", id, SEPARATOR, msg))
                .build();
    }

    private static Long getId(Evaluate commandMessage) {
        return Long.valueOf(Splitter.on(SEPARATOR)
                                    .splitToList(commandMessage.getCmd())
                                    .get(0));
    }

    static String getMessage(Evaluated commandMessage) {
        return Splitter.on(SEPARATOR)
                       .splitToList(commandMessage.getCmd())
                       .get(1);
    }
}
