/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server;

import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import io.spine.client.grpc.CommandServiceGrpc;
import io.spine.core.Ack;
import io.spine.core.ActorContext;
import io.spine.core.Command;
import io.spine.core.ErrorCase;
import io.spine.core.MessageId;
import io.spine.logging.WithLogging;
import io.spine.server.type.CommandClass;
import io.spine.system.server.RequestDiagnostics;
import io.spine.type.MessageClass;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.spine.server.bus.MessageIdExtensions.causedError;
import static io.spine.system.server.UnsupportedRequestReceived.Reason.NO_CONTEXT;

/**
 * The {@code CommandService} provides a synchronous way to post commands
 * to the application backend.
 *
 * <p>This class is an implementation of a corresponding gRPC service.
 * Hence, the public API of this class is dictated by
 * the {@linkplain CommandServiceGrpc generated code}.
 *
 * <p>Commands are sent to the application backend by an instance
 * of the {@linkplain io.spine.client.Client Client} class.
 */
public final class CommandService
        extends CommandServiceGrpc.CommandServiceImplBase
        implements WithLogging {

    private final CommandServiceImpl impl;

    /**
     * Creates a new instance using the given {@link TypeDictionary}.
     */
    @SuppressWarnings("ThisEscapedInObjectConstruction") // Safe, injected at the end of the ctor.
    private CommandService(TypeDictionary types) {
        super();
        this.impl = new CommandServiceImpl(this, types);
    }

    /**
     * Creates a new builder for the service.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Creates a service with the single given Bounded Context.
     *
     * @see #newBuilder()
     */
    public static CommandService withSingle(BoundedContext context) {
        checkNotNull(context);
        var result = newBuilder().add(context).build();
        return result;
    }

    /**
     * Posts the command to {@code CommandBus}.
     *
     * @param command
     *         The command to post
     * @param observer
     *         The observer for the acknowledgement of the command.
     */
    @Override
    public void post(Command command, StreamObserver<Ack> observer) {
        impl.serve(command, observer, null);
    }

    private static final class CommandServiceImpl extends ServiceDelegate<Command, Ack> {

        private CommandServiceImpl(BindableService service, TypeDictionary types) {
            super(service, types);
        }

        @Override
        protected TypeUrl enclosedMessageType(Command request) {
            var type = CommandClass.of(request).typeUrl();
            return type;
        }

        @Override
        protected MessageId requestId(Command cmd) {
            return RequestDiagnostics.idOf(cmd);
        }

        @Override
        protected ActorContext actorOf(Command cmd) {
            return cmd.getContext()
                      .getActorContext();
        }

        @Override
        protected void serve(BoundedContext context,
                             Command cmd,
                             StreamObserver<Ack> observer,
                             @Nullable Object params) {
            context.commandBus().post(cmd, observer);
        }

        /**
         * Since a command can be handled by only one bounded context, responds with
         * the acknowledgement of {@link ErrorCase#UNSUPPORTED_REQUEST UNSUPPORTED_REQUEST}.
         */
        @Override
        protected void serveNoContext(Command cmd,
                                      StreamObserver<Ack> observer,
                                      @Nullable Object params) {
            rejectUnsupported(cmd, NO_CONTEXT, observer);
        }

        /**
         * Responds with the acknowledgement that carries the error case.
         *
         * <p>An unexpected failure still fails the call, because the bus may have already
         * acknowledged the command.
         */
        @Override
        protected void respondWithError(Command cmd,
                                        ErrorCase errorCase,
                                        StreamObserver<Ack> observer) {
            if (errorCase == ErrorCase.INTERNAL_ERROR) {
                super.respondWithError(cmd, errorCase, observer);
                return;
            }
            var response = causedError(cmd.getId(), errorCase);
            observer.onNext(response);
            observer.onCompleted();
        }
    }

    /**
     * The builder for a {@code CommandService}.
     */
    public static class Builder extends AbstractServiceBuilder<CommandService, Builder> {

        /**
         * Builds a new {@link CommandService}.
         */
        @Override
        public CommandService build() {
            var dictionary = TypeDictionary.newBuilder();
            contexts().forEach(
                    context -> dictionary.putAll(context, (c) ->
                            c.commandBus()
                             .registeredCommandClasses()
                                    .stream()
                                    .map(MessageClass::typeUrl)
                                    .collect(toImmutableSet())
                    )
            );

            var result = new CommandService(dictionary.build());
            warnIfEmpty(result);
            return result;
        }

        @Override
        Builder self() {
            return this;
        }
    }
}
