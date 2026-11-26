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

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import io.spine.type.TypeUrl;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

/**
 * Useful methods for {@link Subscription}.
 */
@Immutable
public interface SubscriptionMixin extends SubscriptionOrBuilder {

    /**
     * Obtains the URLs of the types of the topic targets, in the order of the targets.
     *
     * <p>A type that several targets name appears once.
     */
    default ImmutableSet<TypeUrl> targetTypes() {
        var result = getTopic().getTargetList()
                .stream()
                .map(Target::type)
                .collect(toImmutableSet());
        return result;
    }

    /**
     * Obtains a short printable form of subscription.
     *
     * <p>Standard {@link Subscription#toString()} includes all subscription data and thus its
     * output is too huge to use in short log messages and stack traces.
     *
     * @return a printable {@code String} with core subscription data
     */
    default String toShortString() {
        var id = getId().getValue();
        var types = getTopic().getTargetList()
                .stream()
                .map(target -> '`' + target.getType() + '`')
                .collect(joining(", "));
        var result = format("(ID: `%s`, targets: %s)", id, types);
        return result;
    }
}
