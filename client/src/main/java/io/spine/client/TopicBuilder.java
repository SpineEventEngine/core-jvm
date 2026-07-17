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

package io.spine.client;

import com.google.protobuf.Message;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A builder for the {@link io.spine.client.Topic Topic} instances.
 *
 * <p>None of the parameters set by builder methods are required. Call {@link #build()} to retrieve
 * the resulting {@link io.spine.client.Topic Topic} instance.
 *
 * <p>Usage example:
 * <pre>
 *     {@code
 *     Topic topic = factory().topic()
 *                            .select(Customer.class)
 *                            .byId(getWestCoastCustomerIds())
 *                            .withMask("name", "address", "email")
 *                            .where(eq("type", "permanent"),
 *                                   eq("discountPercent", 10),
 *                                   eq("companySize", Company.Size.SMALL))
 *                            .build();
 *     }
 * </pre>
 *
 * @see io.spine.client.TopicFactory#select(Class) to start topic building
 * @see Filters for filter creation shortcuts
 * @see TargetBuilder for more details on this builders API
 */
public final class TopicBuilder extends TargetBuilder<Topic, TopicBuilder> {

    private final TopicFactory topicFactory;

    TopicBuilder(Class<? extends Message> targetType, TopicFactory topicFactory) {
        super(targetType);
        this.topicFactory = checkNotNull(topicFactory);
    }

    /**
     * Generates a new {@link io.spine.client.Topic Topic} instance with the current builder
     * configuration.
     *
     * @return a new {@link io.spine.client.Topic Topic}
     * @throws IllegalArgumentException
     *         if the built {@link Target} instance is invalid, e.g. contains filters with
     *         non-existent fields
     */
    @Override
    public Topic build() {
        var target = buildTarget();
        target.checkValid();
        var mask = composeMask();
        var topic = topicFactory.composeTopic(target, mask);
        return topic;
    }

    @Override
    TopicBuilder self() {
        return this;
    }
}
