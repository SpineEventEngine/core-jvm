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

package io.spine.server.bus.given.stock;

import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.tuple.Pair;
import io.spine.test.bus.Buy;
import io.spine.test.bus.PriceDropped;
import io.spine.test.bus.PriceRaised;
import io.spine.test.bus.Sell;
import io.spine.test.bus.Share;
import io.spine.test.bus.ShareId;
import io.spine.test.bus.ShareTraded;

import java.math.BigDecimal;

import static java.lang.Math.max;
import static java.math.RoundingMode.CEILING;

public final class ShareAggregate extends Aggregate<ShareId, Share, Share.Builder> {

    @Assign
    Pair<ShareTraded, PriceRaised> handle(Buy command) {
        var amount = command.getAmount();
        var traded = ShareTraded.newBuilder()
                .setShare(id())
                .setAmount(amount)
                .build();
        var percent = max(amount / 100.0f, 50.0f);
        var raised = PriceRaised.newBuilder()
                .setShare(id())
                .setPercent(percent)
                .build();
        return Pair.of(traded, raised);
    }

    @Assign
    Pair<ShareTraded, PriceDropped> handle(Sell command) {
        var amount = command.getAmount();
        var traded = ShareTraded.newBuilder()
                .setShare(id())
                .setAmount(amount)
                .build();
        var percent = max(amount / 100.0f, 50.0f);
        var raised = PriceDropped.newBuilder()
                .setShare(id())
                .setPercent(percent)
                .build();
        return Pair.of(traded, raised);
    }

    @Apply
    private void event(ShareTraded event) {
        builder().setId(event.getShare());
    }

    @Apply
    private void event(PriceRaised event) {
        var priceBuilder = builder().getPriceBuilder();
        var oldPrice = priceBuilder.getUsd();
        var newPrice = oldPrice + percentage(oldPrice, event.getPercent());
        priceBuilder.setUsd(newPrice);
    }

    @Apply
    private void event(PriceDropped event) {
        var priceBuilder = builder().getPriceBuilder();
        var oldPrice = priceBuilder.getUsd();
        var newPrice = oldPrice - percentage(oldPrice, event.getPercent());
        priceBuilder.setUsd(newPrice);
    }

    private static float percentage(float value, float percent) {
        var raise = (value * percent / 100.0f);
        var decimalRaise = BigDecimal.valueOf(raise);
        return decimalRaise.setScale(2, CEILING)
                           .floatValue();
    }
}
