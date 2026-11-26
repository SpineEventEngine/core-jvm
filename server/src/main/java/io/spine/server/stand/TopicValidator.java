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

package io.spine.server.stand;

import io.spine.client.Target;
import io.spine.client.Topic;
import io.spine.validation.Validate;

import java.util.Set;

import static io.spine.option.EntityOption.Visibility.SUBSCRIBE;
import static java.lang.String.format;

/**
 * Validates the {@linkplain Topic} instances submitted to {@linkplain Stand}.
 *
 * <p>The {@code Stand} allows subscribing to a target of an entity type that it exposes for
 * subscription, and to a target of any event type.
 */
final class TopicValidator extends AbstractTargetValidator<Topic> {

    TopicValidator(TypeRegistry typeRegistry) {
        super(SUBSCRIBE, typeRegistry);
    }

    /**
     * Tells whether the {@code Stand} allows subscribing to the given target.
     */
    boolean allows(Target target) {
        var exposedEntity = typeRegistryContains(target) && visibilitySufficient(target);
        return exposedEntity || target.ofEvent();
    }

    /**
     * Checks whether the topic is valid, and the {@code Stand} allows subscribing to
     * the targets of the topic with the given indexes.
     *
     * <p>The targets with other indexes are not checked.
     *
     * @param topic
     *         the topic to validate
     * @param targets
     *         the indexes of the topic targets to check
     * @throws InvalidRequestException
     *         if the topic violates its constraints
     * @throws UnsupportedRequestException
     *         if the {@code Stand} does not allow subscribing to one of the checked targets
     */
    void validate(Topic topic, Set<Integer> targets)
            throws InvalidRequestException, UnsupportedRequestException {
        checkViolations(topic, Validate.violationsOf(topic));
        for (int index : targets) {
            var target = topic.getTarget(index);
            if (!allows(target)) {
                throw new UnsupportedRequestException(topic, errorMessage(target));
            }
        }
    }

    /**
     * Tells whether the {@code Stand} allows subscribing to every target of the topic.
     */
    @Override
    protected boolean isSupported(Topic request) {
        return request.getTargetList()
                      .stream()
                      .allMatch(this::allows);
    }

    @Override
    protected String errorMessage(Topic request) {
        var unsupported = request.getTargetList()
                                 .stream()
                                 .filter(target -> !allows(target))
                                 .findFirst()
                                 .orElseThrow();
        return errorMessage(unsupported);
    }

    private static String errorMessage(Target target) {
        var targetType = getTypeOf(target);
        return format("The topic target type is not supported: %s", targetType.typeName());
    }
}
