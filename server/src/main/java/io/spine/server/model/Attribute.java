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

import com.google.errorprone.annotations.Immutable;

/**
 * Contains a value of an annotation attribute.
 *
 * <p>Typical way to add more semantics to a method is via a parameterized annotation,
 * such as {@link io.spine.core.Subscribe Subscribe}.
 *
 * @param <V>
 *         the type of the value of the attribute. As it would typically be an annotation field
 *         value, its type cannot be restricted to anything except for {@link Object}.
 * @see Receptor#attributes()
 */
@Immutable(containerOf = "V")
public interface Attribute<V> {

    /**
     * The name of the parameter.
     *
     * @apiNote This is mostly a diagnostics method. We do not use it directly.
     *  This method cannot be called {@code name()} because it would clash with the built-in method
     *  {@code name()} of enums that implement this interface.
     */
    String parameter();

    /** The value of the attribute. */
    V value();
}
