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

package io.spine.server.command.model;

import com.google.errorprone.annotations.Immutable;
import io.spine.base.CommandMessage;
import io.spine.base.RejectionThrowable;
import io.spine.server.EventProducer;
import io.spine.server.dispatch.Success;
import io.spine.server.model.AbstractReceptor;
import io.spine.server.model.ArgumentFilter;
import io.spine.server.model.ParameterSpec;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.type.MessageClass;

import java.lang.reflect.Method;
import java.util.Optional;

import static io.spine.server.event.RejectionFactory.reject;

/**
 * An abstract base for methods that accept a command message and optionally its context.
 *
 * @param <T>
 *         the type of the target object
 * @param <R>
 *         the type of the produced message classes
 */
@Immutable
public abstract class CommandReceptor<T extends EventProducer,
                                      R extends MessageClass<?>>
        extends AbstractReceptor<T, CommandMessage, CommandClass, CommandEnvelope, R> {

    CommandReceptor(Method method, ParameterSpec<CommandEnvelope> params) {
        super(checkNotFiltered(method, "command accepting"), params);
    }

    @Override
    public final CommandClass messageClass() {
        return CommandClass.from(rawMessageClass());
    }

    @Override
    protected final Optional<Success>
    handleRejection(T target, CommandEnvelope origin, RejectionThrowable throwable) {
        var command = origin.outerObject();
        throwable.initProducer(target.producerId());
        var rejection = reject(command, throwable);
        var success = Success.newBuilder()
                .setRejection(rejection)
                .build();
        return Optional.of(success);
    }

    @Override
    protected final ArgumentFilter createFilter() {
        return ArgumentFilter.acceptingAll();
    }
}
