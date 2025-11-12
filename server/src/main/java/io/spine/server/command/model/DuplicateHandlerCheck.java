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

package io.spine.server.command.model;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.logging.WithLogging;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.command.AbstractAssignee;
import io.spine.server.command.AbstractCommander;
import io.spine.server.model.Model;
import io.spine.server.model.ModelClass;
import io.spine.server.procman.ProcessManager;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkState;
import static io.spine.server.aggregate.model.AggregateClass.asAggregateClass;
import static io.spine.server.command.model.AssigneeClass.asCommandAssigneeClass;
import static io.spine.server.command.model.CommanderClass.asCommanderClass;
import static io.spine.server.procman.model.ProcessManagerClass.asProcessManagerClass;
import static io.spine.util.Exceptions.newIllegalArgumentException;
import static java.lang.String.format;

/**
 * Ensures that there are no duplicating command-handling methods among the passed classes.
 */
@SuppressWarnings("unused") // Use by `model-tools`.
public final class DuplicateHandlerCheck implements WithLogging {

    /**
     * Maps a raw class to a function that creates an appropriate model class, which in turn
     * appends itself to the corresponding model.
     */
    @SuppressWarnings({
            "unchecked", // The cast is preserved by correct key-value pairs.
            "CheckReturnValue" /* Returned values for asXxxClass() are ignored because we use
                                  these methods only for verification of the classes. */
            , "rawtypes" /* For code brevity. */
    })
    private final ImmutableMap<Class<?>, Appender> appenders =
            ImmutableMap.<Class<?>, Appender>builder()
                    .put(Aggregate.class,
                         (c) -> asAggregateClass((Class<? extends Aggregate>) c))
                    .put(ProcessManager.class,
                         (c) -> asProcessManagerClass((Class<? extends ProcessManager>) c))
                    .put(AbstractAssignee.class,
                         (c) -> asCommandAssigneeClass((Class<? extends AbstractAssignee>) c))
                    .put(AbstractCommander.class,
                         (c) -> asCommanderClass((Class<? extends AbstractCommander>) c))
                    .build();

    /**
     * Prevents instantiation from outside.
     */
    private DuplicateHandlerCheck() {
    }

    /**
     * Creates a new instance of the check.
     */
    public static DuplicateHandlerCheck newInstance() {
        return new DuplicateHandlerCheck();
    }

    /**
     * Performs the check for the passed classes.
     */
    public void check(Iterable<Class<?>> classes) {
        logger().atDebug().log(() -> "Dropping models...");
        Model.dropAllModels();
        for (var cls : classes) {
            add(cls);
        }
    }

    /**
     * Verifies if the passed raw Java class is accepted by
     * {@link Model Model} as a valid command handling class.
     *
     * <p>This means that commands handled by this class are not handled by other classes already
     * known to the Model.
     */
    public void add(Class<?> cls) {
        for (var keyClass : appenders.keySet()) {
            if (keyClass.isAssignableFrom(cls)) {
                var appender = appenders.get(keyClass);
                checkState(appender != null, "No appender found for `%s`.", keyClass.getName());
                appender.apply(cls);
                logger().atDebug().log(() -> format("`%s` has been added to the Model.", cls));
                return;
            }
        }

        throw newIllegalArgumentException(
                "Class `%s` is not a command handling type.", cls.getName()
        );
    }

    /**
     * Creates a model class by a raw class, and adds it to the Model.
     */
    @FunctionalInterface
    private interface Appender extends Function<Class<?>, ModelClass<?>> {

        @Override
        @CanIgnoreReturnValue
        ModelClass<?> apply(Class<?> aClass);
    }
}
