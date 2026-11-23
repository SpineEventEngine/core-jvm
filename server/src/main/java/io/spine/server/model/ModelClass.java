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
import java.util.function.Function;

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
     * Creates a cache of the model classes of one kind.
     *
     * <p>The cache creates the model class of a Java class with the passed function when
     * the model class is requested for the first time. Then the cache keeps the model class
     * as long as the Java class is loaded. All the callers obtain the same instance, even when
     * they request it concurrently. If the function throws, the cache keeps nothing, and
     * the next request calls the function again.
     *
     * <p>Each kind of model classes has its own cache. So, one Java class may have model
     * classes of several kinds. For example, an aggregate class has both a plain
     * {@code EntityClass} and an {@code AggregateClass}.
     *
     * @param create
     *         the function that creates the model class of the passed Java class
     * @param <M>
     *         the type of the model classes
     * @return the new cache
     */
    protected static <M extends ModelClass<?>>
    ClassValue<M> cacheOf(Function<Class<?>, M> create) {
        checkNotNull(create);
        return new ClassValue<>() {
            @Override
            protected M computeValue(Class<?> type) {
                return create.apply(type);
            }
        };
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
