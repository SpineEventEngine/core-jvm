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
import io.spine.annotation.Internal;
import io.spine.base.Identifier;
import io.spine.core.ActorContext;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * A factory of {@link Topic} instances.
 *
 * <p>Uses the given {@link ActorRequestFactory} as a source of the topic meta information
 * such as the actor.
 *
 * @see ActorRequestFactory#topic()
 */
public final class TopicFactory {

    private final ActorContext actorContext;

    /**
     * Creates a new {@code TopicFactory} that uses supplied {@code actorRequestFactory}
     * to generate the {@code ActorContext}.
     */
    TopicFactory(ActorRequestFactory actorRequestFactory) {
        checkNotNull(actorRequestFactory);
        this.actorContext = actorRequestFactory.newActorContext();
    }

    /**
     * Creates a new instance of {@link TopicBuilder} for further {@link Topic}
     * construction.
     *
     * @param targetType
     *         a class of target events/entities
     * @return new {@link TopicBuilder} instance
     */
    public TopicBuilder select(Class<? extends Message> targetType) {
        checkNotNull(targetType);
        var builder = new TopicBuilder(targetType, this);
        return builder;
    }

    /**
     * Creates a {@link Topic} for all events/entities of the specified type.
     *
     * @param targetType
     *         a class of target events/entities
     * @return an instance of {@code Topic} assembled according to the parameters
     */
    public Topic allOf(Class<? extends Message> targetType) {
        checkNotNull(targetType);

        var builder = new TopicBuilder(targetType, this);
        var result = builder.build();
        return result;
    }

    /**
     * Creates a {@link Topic} for the specified {@link Target}.
     *
     * <p>This method is intended for internal use only. To achieve the similar result, use
     * {@linkplain #allOf(Class)}.
     *
     * @param target
     *         a {@code Target} to create a topic for
     * @return an instance of {@code Topic}
     * @apiNote Assumes the passed target is {@linkplain TargetMixin#checkValid() valid} and
     *        doesn't do any additional checks.
     */
    @Internal
    public Topic forTarget(Target target) {
        checkNotNull(target);
        return builderForTarget(target).build();
    }

    private Topic.Builder builderForTarget(Target target) {
        return Topic.newBuilder()
                .setId(generateId())
                .setContext(actorContext)
                .setTarget(target);
    }

    private static TopicId generateId() {
        var formattedId = format("t-%s", Identifier.newUuid());
        return TopicId.newBuilder()
                .setValue(formattedId)
                .build();
    }
}
