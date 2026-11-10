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

package io.spine.server;

import com.google.common.collect.ImmutableSet;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import io.spine.base.Error;
import io.spine.base.EventMessage;
import io.spine.client.grpc.CommandServiceGrpc;
import io.spine.core.Ack;
import io.spine.core.Command;
import io.spine.core.ErrorCase;
import io.spine.core.Origin;
import io.spine.core.TenantId;
import io.spine.logging.WithLogging;
import io.spine.system.server.event.CommandPostingFailed;
import io.spine.type.MessageClass;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.spine.core.ErrorCase.INTERNAL_ERROR;
import static io.spine.server.bus.MessageIdExtensions.causedError;
import static io.spine.util.Exceptions.newIllegalStateException;

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
     * <p>A command that is not accepted is answered with an acknowledgement that carries only
     * the {@linkplain ErrorCase kind} of the problem. The details of the problem stay on
     * the server side.
     *
     * <p>An unexpected failure on the server side fails the call with
     * the {@link ErrorCase#INTERNAL_ERROR INTERNAL_ERROR} error case instead, because
     * the command may have been acknowledged already.
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

        /**
         * {@inheritDoc}
         *
         * <p>Reads the type URL of the packed command message, and does not unpack it.
         * So a command of a type internal to the server, or unknown to it, is refused alike,
         * whatever its message holds.
         */
        @Override
        protected TypeUrl enclosedMessageType(Command request) {
            var typeUrl = request.getMessage()
                                 .getTypeUrl();
            return TypeUrl.parse(typeUrl);
        }

        @Override
        protected void serve(BoundedContext context,
                             Command cmd,
                             StreamObserver<Ack> observer,
                             @Nullable Object params) {
            context.commandBus().post(cmd, observer);
        }

        @Override
        protected TenantId tenantOf(Command cmd) {
            return cmd.tenant();
        }

        @Override
        protected Origin originOf(Command cmd) {
            return cmd.asMessageOrigin();
        }

        @Override
        protected EventMessage failureEvent(Command cmd, Error error) {
            return CommandPostingFailed.newBuilder()
                    .setId(cmd.getId())
                    .setError(error)
                    .build();
        }

        /**
         * Since a command can be handled by only one bounded context, responds with
         * the acknowledgement of {@link ErrorCase#UNSUPPORTED_REQUEST UNSUPPORTED_REQUEST}.
         */
        @Override
        protected void serveNoContext(Command cmd,
                                      StreamObserver<Ack> observer,
                                      @Nullable Object params) {
            refuseNotServed(cmd, observer);
        }

        /**
         * Responds with the acknowledgement that carries the error case.
         *
         * <p>{@link ErrorCase#INTERNAL_ERROR INTERNAL_ERROR} fails the call instead,
         * because the bus may have acknowledged the command already.
         */
        @Override
        protected void respondWithError(Command cmd,
                                        ErrorCase errorCase,
                                        StreamObserver<Ack> observer) {
            if (errorCase == INTERNAL_ERROR) {
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
         *
         * @throws IllegalStateException
         *         if two of the added Bounded Contexts handle commands of one type
         */
        @Override
        public CommandService build() {
            var dictionary = TypeDictionary.newBuilder();
            Map<TypeUrl, BoundedContext> handlers = new HashMap<>();
            contexts().forEach(context -> {
                var types = commandTypes(context);
                types.forEach(type -> recordHandler(type, context, handlers));
                dictionary.putAll(context, c -> types);
            });

            var result = new CommandService(dictionary.build());
            warnIfEmpty(result);
            return result;
        }

        private static ImmutableSet<TypeUrl> commandTypes(BoundedContext context) {
            return context.commandBus()
                          .registeredCommandClasses()
                          .stream()
                          .map(MessageClass::typeUrl)
                          .collect(toImmutableSet());
        }

        /**
         * Records that the given context handles commands of the given type.
         *
         * @throws IllegalStateException
         *         if another context handles commands of the type
         */
        private static void recordHandler(TypeUrl type,
                                          BoundedContext context,
                                          Map<TypeUrl, BoundedContext> handlers) {
            var other = handlers.putIfAbsent(type, context);
            if (other != null) {
                throw newIllegalStateException(
                        "Commands of the type `%s` are handled by two Bounded Contexts:" +
                                " `%s` and `%s`.",
                        type, other.name().getValue(), context.name().getValue());
            }
        }

        @Override
        Builder self() {
            return this;
        }
    }
}
