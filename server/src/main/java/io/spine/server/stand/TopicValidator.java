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

import io.spine.base.EventMessage;
import io.spine.client.Topic;

import static io.spine.option.EntityOption.Visibility.SUBSCRIBE;
import static java.lang.String.format;

/**
 * Validates the {@linkplain Topic} instances submitted to {@linkplain Stand}.
 */
final class TopicValidator extends AbstractTargetValidator<Topic> {

    TopicValidator(TypeRegistry typeRegistry) {
        super(SUBSCRIBE, typeRegistry);
    }

    @Override
    protected boolean isSupported(Topic request) {
        var target = request.getTarget();
        var supportedEntityTopic = typeRegistryContains(target) && visibilitySufficient(target);
        if (supportedEntityTopic) {
            return true;
        } else {
            var targetClass = target.type().getMessageClass();
            var supportedEventTopic = EventMessage.class.isAssignableFrom(targetClass);
            return supportedEventTopic;
        }
    }

    @Override
    protected String errorMessage(Topic request) {
        var targetType = getTypeOf(request.getTarget());
        return format("The topic target type is not supported: %s", targetType.typeName());
    }
}
