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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import io.spine.base.CommandMessage;
import io.spine.base.RejectionThrowable;
import io.spine.core.CommandContext;
import io.spine.server.model.AllowedParams;
import io.spine.server.model.ExtractedArguments;
import io.spine.server.model.Receptor;
import io.spine.server.model.MethodParams;
import io.spine.server.model.ReceptorSignature;
import io.spine.server.model.ParameterSpec;
import io.spine.server.model.TypeMatcher;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static io.spine.server.model.TypeMatcher.classImplementing;
import static io.spine.server.model.TypeMatcher.exactly;

/**
 * The signature of a method, that accepts {@code Command} envelopes as parameter values.
 *
 * @param <H> the type of {@link Receptor} whose signature this is
 */
abstract class CommandAcceptingSignature
        <H extends Receptor<?, CommandClass, CommandEnvelope>>
        extends ReceptorSignature<H, CommandEnvelope> {

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType") // to save on allocations.
    private static final Optional<Class<? extends Throwable>>
            ALLOWED_THROWABLE = Optional.of(RejectionThrowable.class);

    CommandAcceptingSignature(Class<? extends Annotation> annotation) {
        super(annotation);
    }

    @Override
    public AllowedParams<CommandEnvelope> params() {
        return CommandAcceptingMethodParams.ALLOWED;
    }

    /**
     * Returns {@code RejectionThrowable.class} wrapped into {@code Optional}.
     *
     * <p>The methods accepting commands may reject the command by throwing {@linkplain
     * RejectionThrowable command rejections} that are based on {@code RejectionThrowable}.
     */
    @Override
    protected Optional<Class<? extends Throwable>> allowedThrowable() {
        return ALLOWED_THROWABLE;
    }

    /**
     * Tells that a command-accepting method never returns an ignored result.
     */
    @Override
    public boolean mayReturnIgnored() {
        return false;
    }

    /**
     * Allowed combinations of parameters in the methods, that accept {@code Command}s.
     */
    @Immutable
    private enum CommandAcceptingMethodParams implements ParameterSpec<CommandEnvelope> {

        MESSAGE(classImplementing(CommandMessage.class)) {

            @Override
            public ExtractedArguments extractArguments(CommandEnvelope envelope) {
                return ExtractedArguments.ofOne(envelope.message());
            }
        },

        MESSAGE_AND_CONTEXT(classImplementing(CommandMessage.class),
                            exactly(CommandContext.class)) {
            @Override
            public ExtractedArguments extractArguments(CommandEnvelope cmd) {
                return ExtractedArguments.ofTwo(cmd.message(), cmd.context());
            }
        };

        private static final AllowedParams<CommandEnvelope> ALLOWED = new AllowedParams<>(values());

        private final ImmutableList<TypeMatcher> criteria;

        CommandAcceptingMethodParams(TypeMatcher... criteria) {
            this.criteria = ImmutableList.copyOf(criteria);
        }

        @Override
        public boolean matches(MethodParams params) {
            return params.match(criteria);
        }
    }
}
