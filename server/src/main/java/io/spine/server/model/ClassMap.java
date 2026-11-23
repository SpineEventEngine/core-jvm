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

import com.google.common.collect.Maps;

import java.util.Map;
import java.util.function.Supplier;

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
     * @param rawClass
     *        raw class for which to obtain a model class
     * @param requestedClass
     *        the class of the model class to obtain.
     *        If the class already available in the map is a super-class of the passed one,
     *        it will be replaced with the new class obtained from the supplier.
     * @param supplier a supplier of a model class
     * @return the instance of the model class corresponding to the raw class
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
        var modelClass = supplier.get();
        classes.put(key, modelClass);
        return modelClass;
    }
}
