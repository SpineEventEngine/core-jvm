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
        var topic = topicFactory.composeTopic(target);
        return topic;
    }

    @Override
    TopicBuilder self() {
        return this;
    }
}
