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

package io.spine.server.model;

import java.util.Objects;

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

    @Override
    public int hashCode() {
        return rawClass.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj
                || (obj instanceof ModelClass<?> other
                        && Objects.equals(this.rawClass, other.rawClass));
    }

    @Override
    public String toString() {
        return rawClass.getName();
    }
}
