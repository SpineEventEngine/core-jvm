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

package io.spine.server.delivery;

import com.google.common.collect.ImmutableList;
import io.spine.server.model.ModelError;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;

/**
 * Throwables observed during signal delivery.
 */
final class DeliveryErrors {

    private final ImmutableList<DeliveryError> errors;

    private DeliveryErrors(Builder builder) {
        this.errors = ImmutableList.copyOf(builder.errors);
    }

    /**
     * Creates a new instance of {@code Builder} for {@code DeliveryErrors} instances.
     *
     * @return new instance of {@code Builder}
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Throws the observed throwables.
     *
     * <p>If there are no throwables, does nothing.
     *
     * <p>Strictly speaking, this method throws the first throwable observed while delivering
     * a message batch. If there are many such throwables, all the consequent ones are attached to
     * the first one as {@link Throwable#addSuppressed suppressed}.
     */
    void throwIfAny() {
        if (hasErrors()) {
            var first = errors.get(0);
            var other = this.errors.subList(1, this.errors.size());
            other.forEach(first::addSuppressed);
            first.rethrow();
        }
    }

    /**
     * Tells if any throwables were observed.
     */
    boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * An error which occurred during a message delivery.
     *
     * <p>Can be a {@code RuntimeException} or a {@code ModelError}.
     */
    private static final class DeliveryError {

        private final @Nullable RuntimeException exception;
        private final @Nullable ModelError error;

        private DeliveryError(RuntimeException exception) {
            this.exception = checkNotNull(exception);
            this.error = null;
        }

        private DeliveryError(ModelError error) {
            this.error = checkNotNull(error);
            this.exception = null;
        }

        private Throwable asThrowable() {
            return exception != null ? exception : requireNonNull(error);
        }

        private void addSuppressed(DeliveryError error) {
            this.asThrowable().addSuppressed(error.asThrowable());
        }

        private void rethrow() {
            if (exception != null) {
                throw exception;
            } else {
                throw requireNonNull(error);
            }
        }
    }

    /**
     * A builder for the {@code DeliveryErrors} instances.
     */
    static final class Builder {

        private final List<DeliveryError> errors = new ArrayList<>();

        /**
         * Prevents direct instantiation.
         */
        private Builder() {
        }

        void addException(RuntimeException exception) {
            checkNotNull(exception);
            this.errors.add(new DeliveryError(exception));
        }

        void addError(ModelError error) {
            checkNotNull(error);
            this.errors.add(new DeliveryError(error));
        }

        /**
         * Creates a new instance of {@code DeliveryErrors}.
         *
         * @return new instance of {@code DeliveryErrors}
         */
        DeliveryErrors build() {
            return new DeliveryErrors(this);
        }
    }
}
