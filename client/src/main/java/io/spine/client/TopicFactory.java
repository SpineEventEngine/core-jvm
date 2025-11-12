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

package io.spine.client;

import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import io.spine.annotation.Internal;
import io.spine.base.Identifier;
import io.spine.core.ActorContext;
import org.jspecify.annotations.Nullable;

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
     * Creates a {@link Topic} for the specified {@link Target};
     * updates for it will include only the fields specified by the {@link FieldMask}.
     *
     * @param target
     *         a {@code Target} to create a topic for
     * @param fieldMask
     *         a {@code FieldMask} defining fields to be included in updates
     * @return an instance of {@code Topic}
     */
    @SuppressWarnings("CheckReturnValue")
    Topic composeTopic(Target target, @Nullable FieldMask fieldMask) {
        checkNotNull(target, "Target must be specified to compose a Topic.");
        var builder = builderForTarget(target);
        if (fieldMask != null) {
            builder.setFieldMask(fieldMask);
        }
        var query = builder.build();
        return query;
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
