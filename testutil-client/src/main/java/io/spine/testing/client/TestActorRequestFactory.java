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

package io.spine.testing.client;

import com.google.protobuf.Timestamp;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.CommandMessage;
import io.spine.client.ActorRequestFactory;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.core.TenantId;
import io.spine.core.UserId;
import io.spine.testing.TestValues;
import io.spine.testing.client.command.TestCommandMessage;
import io.spine.testing.core.given.GivenUserId;
import io.spine.time.ZoneId;
import io.spine.time.ZoneIds;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An {@code ActorRequestFactory} for running tests.
 */
@VisibleForTesting
@SuppressWarnings({
        "PMD.UnnecessaryFullyQualifiedName", "UnnecessarilyQualifiedStaticUsage"
        // These are in conflict with https://errorprone.info/bugpattern/BadImport.
})
public class TestActorRequestFactory extends ActorRequestFactory {

    public TestActorRequestFactory(UserId actor, ZoneId zoneId) {
        super(ActorRequestFactory.newBuilder()
                      .setActor(actor)
                      .setZoneId(zoneId)
        );
    }

    public TestActorRequestFactory(@Nullable TenantId tenantId, UserId actor, ZoneId zoneId) {
        super(ActorRequestFactory.newBuilder()
                      .setTenantId(tenantId)
                      .setActor(actor)
                      .setZoneId(zoneId)
        );
    }

    public TestActorRequestFactory(String actor, ZoneId zoneId) {
        this(GivenUserId.of(actor), zoneId);
    }

    public TestActorRequestFactory(Class<?> testClass) {
        this(testClass.getName(), ZoneIds.systemDefault());
    }

    public TestActorRequestFactory(UserId actor) {
        this(actor, ZoneIds.systemDefault());
    }

    public TestActorRequestFactory(UserId actor, TenantId tenantId) {
        this(tenantId, actor, ZoneIds.systemDefault());
    }

    public TestActorRequestFactory(Class<?> testClass, TenantId tenantId) {
        this(tenantId,
             GivenUserId.of(testClass.getName()),
             ZoneIds.systemDefault());
    }

    /** Creates new command with the passed timestamp. */
    public Command createCommand(CommandMessage message, Timestamp timestamp) {
        var command = command().create(message);
        return withTimestamp(command, timestamp);
    }

    private static Command withTimestamp(Command command, Timestamp timestamp) {
        var origin = command.context();
        var withTime = origin.getActorContext()
                .toBuilder()
                .setTimestamp(timestamp)
                .build();
        var resultContext = origin.toBuilder()
                .setActorContext(withTime)
                .build();
        var result = command.toBuilder()
                .setContext(resultContext)
                .build();
        return result;
    }

    public Command createCommand(CommandMessage message) {
        var command = command().create(message);
        return command;
    }

    /**
     * Generates a test instance of a command with the message
     * {@link io.spine.testing.client.command.TestCommandMessage TestCommandMessage}.
     */
    public Command generateCommand() {
        @SuppressWarnings("MagicNumber")
        var randomSuffix = String.format("%04d", TestValues.random(10_000));
        var msg = TestCommandMessage.newBuilder()
                .setId("random-number-" + randomSuffix)
                .build();
        return createCommand(msg);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to open access to creating command contexts in tests.
     */
    @Override
    public CommandContext createCommandContext() {
        return super.createCommandContext();
    }

    /**
     * Obtains the current offset for the passed time zone.
     *
     * @deprecated please use {@link #zoneId()}.
     */
    @Deprecated
    public static io.spine.time.ZoneOffset toOffset(ZoneId zoneId) {
        var offset = ZoneIds.toJavaTime(zoneId)
                       .getRules()
                       .getOffset(Instant.now());
        return io.spine.time.ZoneOffsets.of(offset);
    }
}
