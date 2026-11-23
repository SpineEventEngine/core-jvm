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

import com.google.common.collect.ImmutableSet;
import io.spine.server.command.AbstractCommander;
import io.spine.server.command.Commander;
import io.spine.server.event.model.EventReceiverClass;
import io.spine.server.event.model.EventReceivingClassDelegate;
import io.spine.server.model.ExternalCommandReceiverMethodError;
import io.spine.server.model.ModelClassValue;
import io.spine.server.model.Receptor;
import io.spine.server.type.CommandClass;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.union;
import static java.util.stream.Collectors.toSet;

/**
 * Provides information on message handling for a class of {@link Commander}s.
 *
 * @param <C>
 *         the type of commanders
 */
public final class CommanderClass<C extends Commander>
        extends AbstractCommandHandlingClass<C, CommandClass, CommandSubstituter>
        implements EventReceiverClass, CommandingClass {

    /**
     * Associates a {@code CommanderClass} with a Java class of commanders.
     *
     * @see #asCommanderClass(Class)
     */
    @SuppressWarnings("unchecked") // `asCommanderClass()` accepts only classes of commanders.
    private static final ModelClassValue<CommanderClass<?>> classValue =
            new ModelClassValue<>(cls -> new CommanderClass<>((Class<AbstractCommander>) cls));

    private final EventReceivingClassDelegate<C, CommandClass, CommandingReaction> delegate;

    private CommanderClass(Class<C> cls) {
        super(cls, new CommandSubstituteSignature());
        this.delegate = new EventReceivingClassDelegate<>(cls, new CommandReactionSignature());
        validateExternalMethods();
    }

    /**
     * Creates a new commander class for the passed raw class, without caching it.
     *
     * <p>Serves the model classes that substitute commands on behalf of their
     * raw classes, such as the class of process managers.
     */
    public static <C extends Commander> CommanderClass<C> delegateFor(Class<C> cls) {
        checkNotNull(cls);
        var result = new CommanderClass<>(cls);
        return result;
    }

    /**
     * Obtains a commander class for the passed raw class.
     */
    public static <C extends AbstractCommander>
    CommanderClass<C> asCommanderClass(Class<C> cls) {
        checkNotNull(cls);
        @SuppressWarnings("unchecked") // The class value creates the model class of `cls`.
        var result = (CommanderClass<C>) classValue.get(cls);
        return result;
    }

    @Override
    public ImmutableSet<EventClass> events() {
        return delegate.events();
    }

    @Override
    public ImmutableSet<EventClass> domesticEvents() {
        return delegate.domesticEvents();
    }

    @Override
    public ImmutableSet<EventClass> externalEvents() {
        return delegate.externalEvents();
    }

    /**
     * Obtains the method that reacts on the passed event class.
     */
    public Optional<CommandingReaction> commanderOn(EventEnvelope event) {
        return delegate.findReceptorOf(event);
    }

    /**
     * Tells if instances of this commander class substitute the commands of the passed class.
     */
    public boolean substitutesCommand(CommandClass commandClass) {
        return hasReceptor(commandClass);
    }

    /**
     * Tells if instances of this commander class produce commands in response to
     * the passed event class.
     */
    public boolean producesCommandsOn(EventClass eventClass) {
        return delegate.contains(eventClass);
    }

    @Override
    public ImmutableSet<CommandClass> outgoingCommands() {
        var result = union(commandOutput(), delegate.producedTypes());
        return result.immutableCopy();
    }

    /**
     * Ensures no {@linkplain io.spine.core.External external} command substitution methods in
     * the class.
     *
     * <p>Command substitution methods accept {@linkplain io.spine.base.CommandMessage commands} as
     * input and there is no notion of "external" commands in the system. Thus, such method
     * declarations, although technically possible, should be avoided to prevent confusion.
     *
     * @throws ExternalCommandReceiverMethodError
     *         in case external command substitution methods are found within the class
     */
    private void validateExternalMethods() {
        var methods = commands().stream()
                .flatMap(type -> receptorsForType(type).stream())
                .filter(Receptor::isExternal)
                .collect(toSet());
        if (!methods.isEmpty()) {
            throw new ExternalCommandReceiverMethodError(this, methods);
        }
    }
}
