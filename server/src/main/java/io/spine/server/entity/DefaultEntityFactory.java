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

package io.spine.server.entity;

import com.google.errorprone.annotations.concurrent.LazyInit;
import io.spine.server.entity.model.AbstractEntityFactory;
import io.spine.server.model.ModelError;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Default implementation of entity factory which creates entities by invoking constructor
 * which accepts entity ID.
 *
 * @param <E>
 *         the type of produced entities
 */
public final class DefaultEntityFactory<E extends Entity<?, ?>> extends AbstractEntityFactory<E> {

    @Serial
    private static final long serialVersionUID = 0L;

    /**
     * The method which the factory would use for setting an entity ID, if the entity class
     * does not have a constructor which accepts the ID.
     */
    private static final String SET_ID_METHOD_NAME = "setId";

    /**
     * Diagnostics message suffix for wrong type of an entity identifier.
     */
    private static final String ADVISE_CHECK_ROUTING = " Check for message routing mistakes.";

    /**
     * If {@code true}, the factory uses default constructor of the entity.
     * If {@code false}, the constructor with ID parameter is used.
     */
    private transient boolean usesDefaultConstructor;

    /**
     * The type of the first constructor parameter, if any.
     * {@code null} if the constructor does not accept parameters.
     */
    @LazyInit
    @SuppressWarnings("Immutable") // effectively
    private transient volatile @Nullable Class<?> firstParameterType;

    /**
     * Creates new instance.
     */
    public DefaultEntityFactory(Class<E> entityClass) {
        super(entityClass);
    }

    @Override
    public E create(Object id) {
        checkArgumentMatches(id);
        var result = doCreate(id);
        return result;
    }

    private E doCreate(Object id) {
        var ctor = constructor();
        try {
            E result;
            if (usesDefaultConstructor) {
                result = ctor.newInstance();
                setId(result, id);
            } else {
                result = ctor.newInstance(id);
            }
            return result;
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) /*  Type is checked when obtaining default ctor. */
    private void setId(E entity, Object id) {
        var cast = (AbstractEntity) entity;
        cast.setId(id);
    }

    @Override
    protected Constructor<E> findConstructor() {
        Constructor<E> result;
        var constructors = entityClass().getDeclaredConstructors();
        result = idConstructorIn(constructors);

        if (result == null) {
            result = defaultConstructorIn(constructors);
        }

        if (result == null) {
            throw new ModelError(
                "The entity class `%s` must have either a constructor which accepts `%s` as" +
                " a single parameter, or a default constructor and `%s()` method.",
                entityClass(), idClass(), SET_ID_METHOD_NAME
            );
        }

        result.setAccessible(true);
        return result;
    }

    private @Nullable Constructor<E> idConstructorIn(Constructor<?>[] constructors) {
        @SuppressWarnings("unchecked") // ensured by the generic parameter of this class.
        var result = (Constructor<E>)
                Arrays.stream(constructors)
                      .filter(c -> {
                          var parameterTypes = c.getParameterTypes();
                          return parameterTypes.length == 1
                                  && idClass().equals(parameterTypes[0]);
                      })
                      .findAny()
                      .orElse(null);
        if (result != null) {
            var parameterTypes = result.getParameterTypes();
            firstParameterType =
                    (parameterTypes.length > 0)
                    ? parameterTypes[0]
                    : null;
        }
        return result;
    }

    /**
     * Obtains the type of the first constructor parameter, or {@code null} if the entity
     * does not have an ID constructor.
     */
    private @Nullable Class<?> firstParameterType() {
        return firstParameterType;
    }

    private @Nullable Constructor<E> defaultConstructorIn(Constructor<?>[] constructors) {
        var defaultCtor =
                Arrays.stream(constructors)
                      .filter(c -> c.getParameterTypes().length == 0)
                      .findAny();

        if (defaultCtor.isPresent()) {
            @SuppressWarnings("unchecked") // ensured by the generic parameter of this class.
            var result = (Constructor<E>) defaultCtor.get();
            var idClass = idClass();
            var entityClass = entityClass();
            if (!AbstractEntity.class.isAssignableFrom(entityClass)) {
                throw new ModelError(
                        "The entity class `%s` is not assignable from `%s`" +
                        " (so that `%s()` method can be used for setting the entity ID)," +
                        " neither it has a constructor which accepts the ID class `%s` as" +
                        " a single parameter. Please provide the constructor.",
                        entityClass, AbstractEntity.class.getName(), SET_ID_METHOD_NAME, idClass
                );
            }
            usesDefaultConstructor = true;
            return result;
        }
        return null;
    }

    private void checkArgumentMatches(Object argument) {
        // Ensure we have the constructor, and if it parameterized, it's first parameter type.
        // If not, we must have the method for setting ID.
        checkNotNull(constructor());

        var actualArgumentType = argument.getClass();

        var idClass = idClass();
        if (usesDefaultConstructor) {
            checkArgument(
                    idClass.isAssignableFrom(actualArgumentType),
                    "`%s()` argument type mismatch: expected `%s`, but was: `%s`.%s",
                    SET_ID_METHOD_NAME, idClass, actualArgumentType, ADVISE_CHECK_ROUTING
            );
            return;
        }

        var firstParamType = firstParameterType();
        checkState(firstParamType != null,
                   "The entity class `%s` does not have a constructor which" +
                           " accepts ID parameter of type `%s`.",
                   entityClass().getName(), idClass.getName());
        var errorMessage = "Argument type mismatch: expected `%s`, but was `%s`." + ADVISE_CHECK_ROUTING;
        checkArgument(firstParamType.isAssignableFrom(actualArgumentType),
                      errorMessage,
                      firstParamType.getName(),
                      actualArgumentType.getName());
    }
}
