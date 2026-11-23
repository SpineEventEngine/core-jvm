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

package io.spine.core;

import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.reflect.PackageInfo;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;

/**
 * Utility class for working with Bounded Context names.
 *
 * @see io.spine.core.BoundedContextName
 */
public final class BoundedContextNames {

    /** The name of a Bounded Context to be used if the name was explicitly set. */
    private static final BoundedContextName ASSUMING_TESTS = newName("AssumingTests");

    /**
     * The names of the Bounded Contexts of the classes.
     *
     * @see #contextOf(Class)
     */
    private static final ClassValue<BoundedContextName> contexts = new ClassValue<>() {
        @Override
        protected BoundedContextName computeValue(Class<?> type) {
            // If no name of a Bounded Context found, assume the default name.
            // This is a safety net for newcomers and our tests.
            // We may want to make this check strict and require specifying Bounded Context names.
            return findContext(type).orElseGet(BoundedContextNames::assumingTests);
        }
    };

    /**
     * Prevents the utility class instantiation.
     */
    private BoundedContextNames() {
    }

    /**
     * Creates a new value object for a bounded context name.
     *
     * <p>The {@code name} argument value must not be {@code null} or empty.
     *
     * <p>This method, however, does not check for the uniqueness of the value passed.
     *
     * @param name the unique string name of the {@code BoundedContext}
     * @return a newly created name
     */
    public static BoundedContextName newName(String name) {
        checkNotEmptyOrBlank(name, "Empty context name is not allowed.");
        var result = BoundedContextName.newBuilder()
                .setValue(name)
                .build();
        checkValid(result);
        return result;
    }

    /**
     * Validates the given {@link BoundedContextName}.
     *
     * <p>The name must not be empty or blank in order to pass the validation.
     *
     * @throws IllegalArgumentException if the name is not valid
     */
    @Internal
    public static void checkValid(BoundedContextName name) throws IllegalArgumentException {
        checkNotNull(name);
        var value = name.getValue();
        checkValid(value);
    }

    /**
     * Validates the given {@link BoundedContextName}.
     *
     * <p>The name must not be {@code null}, empty or blank in order to pass the validation.
     */
    @Internal
    public static void checkValid(String boundedContextName) throws IllegalArgumentException {
        checkNotEmptyOrBlank(
                boundedContextName,
                "A Bounded Context name cannot be empty or blank."
        );
    }

    /**
     * Obtains the name for a Bounded Context, which will be used when no name was specified.
     */
    @Internal
    @VisibleForTesting
    public static String assumingTestsValue() {
        return ASSUMING_TESTS.getValue();
    }

    /**
     * Obtains the name for a Bounded Context, which will be used when no name was specified.
     */
    @Internal
    @VisibleForTesting
    public static BoundedContextName assumingTests() {
        return ASSUMING_TESTS;
    }

    /**
     * Obtains the name of the Bounded Context to which the passed class belongs.
     *
     * <p>The method takes the name from the {@link BoundedContext} annotation of the package
     * of the class, or of the packages into which the package of the class is nested.
     * If no package has the annotation, the method returns the name that tests use
     * by default.
     *
     * <p>The name is searched for once per class, and then kept as long as the class
     * is loaded.
     */
    @Internal
    public static BoundedContextName contextOf(Class<?> cls) {
        checkNotNull(cls);
        return contexts.get(cls);
    }

    /**
     * Finds Bounded Context name in the package annotations of the raw class,
     * or in the packages into which the package of the class is nested.
     */
    private static Optional<BoundedContextName> findContext(Class<?> rawClass) {
        var pkg = PackageInfo.of(rawClass);
        var annotation = pkg.findAnnotation(BoundedContext.class);
        return annotation.map(a -> newName(a.value()));
    }
}
