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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a package as one belonging to a Bounded Context with
 * the {@linkplain #value() specified name}.
 *
 * <p>Java does not have the notion of package nesting. Packages in Java are separate namespaces
 * that seem to have a hierarchical structure for convenience.
 *
 * <h2>Nesting Convention</h2>
 * <p>This annotation assumes that nesting formed by a programmer when naming packages <em>is</em>
 * a hierarchy. Terms mentioned below assume that package “inheritance” or “hierarchy” is a
 * convention supported by this framework and is not a standard feature of Java.
 *
 * <h2>Inheriting Package Annotation</h2>
 * <p>Packages whose names start with the name of the annotated package “inherit” the annotation.
 * This means that these packages belong to the same Bounded Context <em>unless</em> they are
 * annotated with another Bounded Context name.
 *
 * <p>A possible usage scenario would be to have an aggregate class placed in a “parent”
 * package, and the projections and process managers that serve it placed under
 * “sub-packages”.
 *
 * <p>Packages that do not have a common “parent” but are annotated with the same name belong
 * to the same Bounded Context.
 */
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BoundedContext {

    /**
     * The name of the Bounded Context to which the package belongs.
     */
    String value();
}
