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

package io.spine.server.model;

import java.util.Objects;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Abstract base for classes providing additional information on a Java class,
 * such as classes of messages being handled by the methods exposed by the class.
 *
 * @param <T> the type of objects
 */
public abstract class ModelClass<T> {

    private final Class<?> rawClass;

    protected ModelClass(Class<? extends T> rawClass) {
        this.rawClass = checkNotNull(rawClass);
    }

    /**
     * Obtains the Java class for which this model class provides extended information.
     */
    public Class<?> rawClass() {
        return rawClass;
    }

    /**
     * Obtains the model class for the passed raw class.
     *
     * <p>If the model does not have the model class yet, it would be obtained
     * from the passed supplier and remembered.
     */
    protected static <T, M extends ModelClass<T>>
    ModelClass<T> get(Class<T> rawClass,
                      Class<M> requestedModelClass,
                      Supplier<ModelClass<T>> supplier) {
        var model = Model.inContextOf(rawClass);
        var result = model.getClass(rawClass, requestedModelClass, supplier);
        return result;
    }

    @Override
    public int hashCode() {
        return rawClass.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ModelClass)) {
            return false;
        }
        var other = (ModelClass<?>) obj;
        return Objects.equals(this.rawClass, other.rawClass);
    }

    @Override
    public String toString() {
        return rawClass.getName();
    }
}
