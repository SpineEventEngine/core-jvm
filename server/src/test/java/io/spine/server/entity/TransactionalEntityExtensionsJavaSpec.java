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

import io.spine.server.entity.given.Given;
import io.spine.server.entity.given.tryalter.StockKeeper;
import io.spine.test.tryalter.Stock;
import io.spine.validation.NonValidated;
import kotlin.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Objects.requireNonNull;

/**
 * Verifies that the extensions declared in {@code TransactionalEntityExts.kt}
 * are usable from Java via the {@link TransactionalEntityExtensions} facade.
 */
@DisplayName("`TransactionalEntityExtensions` should")
class TransactionalEntityExtensionsJavaSpec {

    private static final String ID = "stock-from-java";

    @Test
    @DisplayName("apply a valid change via `tryAlter()`")
    void applyValidChange() {
        var pm = stockKeeper(10);

        var violations = TransactionalEntityExtensions.tryAlter(pm, builder -> {
            builder.setInStock(20);
            return Unit.INSTANCE;
        });

        assertThat(violations).isEmpty();
        assertThat(liveState(pm).getInStock()).isEqualTo(20);
    }

    @Test
    @DisplayName("return violations from `tryAlter()`, leaving the live builder untouched")
    void returnViolations() {
        var pm = stockKeeper(10);

        var violations = TransactionalEntityExtensions.tryAlter(pm, builder -> {
            builder.setInStock(-1);
            return Unit.INSTANCE;
        });

        assertThat(violations).hasSize(1);
        assertThat(liveState(pm).getInStock()).isEqualTo(10);
    }

    private static StockKeeper stockKeeper(int amount) {
        var state = Stock.newBuilder()
                .setId(ID)
                .setInStock(amount)
                .build();
        var pm = Given.processManagerOfClass(StockKeeper.class)
                      .withId(ID)
                      .withState(state)
                      .build();
        // The upcast is required: the package-private members of `TransactionalEntity`
        // are not inherited by `StockKeeper`, which resides in another package.
        TransactionalEntity<String, Stock, Stock.Builder> entity = pm;
        var tx = new StubTransaction<>(entity, /* active = */ true, /* stateChanged = */ false);
        entity.injectTransaction(tx);
        return pm;
    }

    private static @NonValidated Stock liveState(StockKeeper pm) {
        TransactionalEntity<String, Stock, Stock.Builder> entity = pm;
        var tx = requireNonNull(entity.transaction());
        return tx.builder().buildPartial();
    }
}
