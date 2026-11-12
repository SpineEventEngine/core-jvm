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

import com.google.protobuf.Message;
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
import io.spine.server.app.RefusedRequest;
import io.spine.system.server.event.CommandPostingFailed;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.core.ErrorCase.INTERNAL_ERROR;
import static io.spine.server.bus.MessageIdExtensions.causedError;

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
 *
 * <p>The service posts commands to the Bounded Contexts of an {@link Application}.
 * A command to a multi-tenant context is posted only if its tenant is registered in
 * the {@linkplain Application#tenantIndex() tenant index} of the application.
 */
public final class CommandService
        extends CommandServiceGrpc.CommandServiceImplBase
        implements WithLogging {

    private final CommandServiceImpl impl;

    /**
     * Creates a new instance using the given {@link TypeDictionary}.
     */
    @SuppressWarnings("ThisEscapedInObjectConstruction") // Safe, injected at the end of the ctor.
    private CommandService(TypeDictionary types, Application application) {
        super();
        this.impl = new CommandServiceImpl(this, types, application);
    }

    /**
     * Creates the service that posts commands to the Bounded Contexts of the given application.
     */
    public static CommandService of(Application application) {
        checkNotNull(application);
        var dictionary = TypeDictionary.newBuilder();
        application.contexts().forEach(
                context -> dictionary.putAll(context, BoundedContext::commandTypes)
        );
        return new CommandService(dictionary.build(), application);
    }

    /**
     * Posts the command to {@code CommandBus}.
     *
     * <p>A command that is not accepted is answered with an acknowledgement that carries only
     * the {@linkplain ErrorCase kind} of the problem. The details of the problem stay on
     * the server side. A command of a tenant that is not registered in the application is
     * answered with {@link ErrorCase#TENANT_ERROR TENANT_ERROR}.
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

        private CommandServiceImpl(BindableService service,
                                   TypeDictionary types,
                                   Application application) {
            super(service, types, application);
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
        protected Message idOf(Command cmd) {
            return cmd.getId();
        }

        @Override
        protected RefusedRequest refusedRequest(Command cmd) {
            return RefusedRequest.newBuilder()
                    .setCommand(cmd)
                    .build();
        }

        /**
         * Returns {@code null}, since the {@code CommandBus} of the bounded context checks
         * the tenant of the command, including the commands posted from within the server.
         */
        @Override
        protected @Nullable EventMessage tenantNotSpecified(Command cmd) {
            return null;
        }

        /**
         * Returns {@code null}, since the {@code CommandBus} of the bounded context checks
         * the tenant of the command, including the commands posted from within the server.
         */
        @Override
        protected @Nullable EventMessage tenantNotExpected(Command cmd, TenantId tenant) {
            return null;
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
}
