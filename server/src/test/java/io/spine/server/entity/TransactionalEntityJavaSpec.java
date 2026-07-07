/*
 * Copyright 2026, TeamDev. All rights reserved.
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

import io.spine.test.tryalter.Stock;
import io.spine.validation.ConstraintViolation;
import io.spine.validation.NonValidated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Verifies that the state-modification members of {@link TransactionalEntity},
 * declared in Kotlin, are usable from Java subclasses.
 */
@DisplayName("`TransactionalEntity`, when subclassed in Java, should")
class TransactionalEntityJavaSpec {

    private static final String ID = "stock-from-java";

    @Test
    @DisplayName("apply a valid change via `tryAlter()`")
    void applyValidChange() {
        var entity = stockEntity(10);

        var violations = entity.tryChange(20);

        assertThat(violations).isEmpty();
        assertThat(liveState(entity).getInStock()).isEqualTo(20);
    }

    @Test
    @DisplayName("return violations from `tryAlter()`, leaving the live builder untouched")
    void returnViolations() {
        var entity = stockEntity(10);

        var violations = entity.tryChange(-1);

        assertThat(violations).hasSize(1);
        assertThat(liveState(entity).getInStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("change the live builder via `alter()`")
    void alterState() {
        var entity = stockEntity(10);

        entity.change(30);

        assertThat(liveState(entity).getInStock()).isEqualTo(30);
    }

    @Test
    @DisplayName("change the live builder and return it via `update()`")
    void updateState() {
        var entity = stockEntity(10);

        var builder = entity.changeAndGet(40);

        assertThat(builder.getInStock()).isEqualTo(40);
        assertThat(liveState(entity).getInStock()).isEqualTo(40);
    }

    private static StockEntity stockEntity(int amount) {
        var state = Stock.newBuilder()
                .setId(ID)
                .setInStock(amount)
                .build();
        var entity = new StockEntity(ID);
        entity.setState(state);
        // Instantiating the stub transaction injects it into the `entity`.
        new StubTransaction<>(entity, /* active = */ true, /* stateChanged = */ false);
        return entity;
    }

    private static @NonValidated Stock liveState(StockEntity entity) {
        // `tx()` is `protected` in `TransactionalEntity`; this spec resides in the
        // same package, so it is reachable here. `builder()` is module-internal.
        return entity.tx().builder().buildPartial();
    }

    /**
     * A test-only entity exercising the state-modification members inherited
     * from {@code TransactionalEntity}.
     *
     * <p>All the calls use the {@link java.util.function.Consumer Consumer}-based
     * overloads, so the lambdas do not have to return {@code Unit.INSTANCE}.
     */
    private static final class StockEntity
            extends TransactionalEntity<String, Stock, Stock.Builder> {

        private StockEntity(String id) {
            super(id);
        }

        /**
         * Calls the {@code tryAlter} member from Java.
         */
        List<ConstraintViolation> tryChange(int newAmount) {
            return tryAlter(builder -> builder.setInStock(newAmount));
        }

        /**
         * Calls the protected {@code alter} member from Java.
         */
        void change(int newAmount) {
            alter(builder -> builder.setInStock(newAmount));
        }

        /**
         * Calls the protected {@code update} member from Java.
         */
        Stock.Builder changeAndGet(int newAmount) {
            return update(builder -> builder.setInStock(newAmount));
        }
    }
}
