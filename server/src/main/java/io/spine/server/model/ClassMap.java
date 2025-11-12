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

package io.spine.server.model;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.spine.server.command.model.CommandHandlingClass;
import io.spine.server.type.CommandClass;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.google.common.collect.Sets.intersection;

/**
 * Maps a Java class to its {@link ModelClass}.
 */
final class ClassMap {

    /**
     * The map from the name of the class to the instance of the corresponding model class.
     * @see #nameOf(Class)
     */
    private final Map<String, ModelClass<?>> classes = Maps.newConcurrentMap();

    /**
     * Returns a unique identifying name of the given class.
     *
     * <p>The returned value is guaranteed to be unique per class and non-null.
     *
     * @param cls the {@link Class} to identity
     * @return a non-null class name
     */
    private static String nameOf(Class<?> cls) {
        return cls.getName();
    }

    /**
     * Obtains a model class for the passed one.
     *
     * <p>If the class is not in the model, creates and puts it into the model using the provided
     * {@code Supplier}.
     *
     * <p>If the passed class handles commands, verifies that it does not handle commands for which
     * there are already handlers registered in the model.
     *
     * @param rawClass
     *        raw class for which to obtain a model class
     * @param requestedClass
     *        the class of the model class to obtain.
     *        If the class already available in the map is a super-class of the passed one,
     *        it will be replaced with the new class obtained from the supplier.
     * @param supplier a supplier of a model class
     * @return the instance of the model class corresponding to the raw class
     * @throws DuplicateCommandReceptorError
     *         if the passed class handles commands that are already handled
     */
    <T, M extends ModelClass<T>>
    ModelClass<T> get(Class<? extends T> rawClass,
                      Class<M> requestedClass,
                      Supplier<ModelClass<T>> supplier) {
        var key = nameOf(rawClass);
        @SuppressWarnings("unchecked")
            /* The cast is protected by generic parameters of this method.
               We store the model class with the correct type, getting it from the supplier
               passed to this method. */
        var modelClass = (ModelClass<T>) classes.get(key);
        if (modelClass == null) {
            return setEntry(key, supplier);
        }

        @SuppressWarnings("unchecked")
        var currentClass =
                (Class<? extends ModelClass<?>>) modelClass.getClass();
        if (currentClass.equals(requestedClass)) {
            return modelClass;
        }

        /* The classes are not equal. If the current entry is a super-class of the passed
           requested model class, replace the entry with this sub-class because it provides more
           model information about the raw class. */
        if (currentClass.isAssignableFrom(requestedClass)) {
            return setEntry(key, supplier);
        }

        return modelClass;
    }

    private <T> ModelClass<T> setEntry(String key, Supplier<ModelClass<T>> supplier) {
        ModelClass<T> modelClass;
        modelClass = supplier.get();
        if (modelClass instanceof CommandHandlingClass) {
            checkDuplicates((CommandHandlingClass<?, ?>) modelClass);
        }
        classes.put(key, modelClass);
        return modelClass;
    }

    void clear() {
        classes.clear();
    }

    private void checkDuplicates(CommandHandlingClass<?, ?> candidate)
            throws DuplicateCommandReceptorError {
        Set<CommandClass> candidateCommands = candidate.commands();
        ImmutableMap.Builder<Set<CommandClass>, CommandHandlingClass<?, ?>> duplicates =
                ImmutableMap.builder();

        for (var modelClass : classes.values()) {
            if (modelClass instanceof CommandHandlingClass) {
                var commandHandler =
                        (CommandHandlingClass<?, ?>) modelClass;
                Set<CommandClass> alreadyHandled = commandHandler.commands();
                Set<CommandClass> intersection = intersection(alreadyHandled, candidateCommands);
                if (intersection.size() > 0) {
                    duplicates.put(intersection, commandHandler);
                }
            }
        }

        var currentHandlers = duplicates.build();
        if (!currentHandlers.isEmpty()) {
            throw new DuplicateCommandReceptorError(candidate, currentHandlers);
        }
    }
}
