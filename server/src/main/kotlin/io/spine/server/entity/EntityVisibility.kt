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

package io.spine.server.entity

import com.google.common.graph.GraphBuilder
import com.google.common.graph.ImmutableGraph
import com.google.errorprone.annotations.Immutable
import io.spine.annotation.Internal
import io.spine.base.EntityState
import io.spine.code.proto.EntityStateOption
import io.spine.option.EntityOption.Kind.PROJECTION
import io.spine.option.EntityOption.Visibility
import io.spine.option.EntityOption.Visibility.DEFAULT
import io.spine.option.EntityOption.Visibility.FULL
import io.spine.option.EntityOption.Visibility.NONE
import io.spine.option.EntityOption.Visibility.QUERY
import io.spine.option.EntityOption.Visibility.SUBSCRIBE
import io.spine.server.entity.EntityVisibility.Companion.visibilities
import io.spine.type.TypeName
import java.io.Serial
import java.io.Serializable
import java.util.*

/**
 * The visibility of an entity type.
 *
 * An entity can have one of the following visibility levels:
 *
 *  1. [NONE] — the entity is not visible to the clients for reading;
 *  2. [QUERY] — the entity is visible for querying, but not for subscription;
 *  3. [SUBSCRIBE] — the entity is visible for subscription, but not for querying;
 *  4. [FULL] — the entity is visible for both subscription and querying.
 *
 * The visibility of an entity is defined by the `(entity)` option.
 * By default, any entity has the `NONE` level except for projections,
 * which have the `FULL` level.
 */
@Immutable
@Internal
public class EntityVisibility private constructor(
    private val value: Visibility
) : Serializable {

    /**
     * Checks if the visibility level is sufficient for subscription.
     *
     * If the level is sufficient, it is allowed to subscribe to the entity state updates via
     * [io.spine.core.Subscribe] or [io.spine.server.SubscriptionService].
     *
     * @return `true` if the visibility is `SUBSCRIBE` or `FULL`, `false` otherwise.
     */
    public fun canSubscribe(): Boolean = isAsLeast(SUBSCRIBE)

    /**
     * Checks if the visibility level is sufficient for querying.
     *
     * If the level is sufficient, it is allowed to query the entity states via
     * [io.spine.server.QueryService].
     *
     * @return `true` if the visibility is `QUERY` or `FULL`, `false` otherwise.
     */
    public fun canQuery(): Boolean = isAsLeast(QUERY)

    /**
     * Checks if the visibility level is not `NONE`.
     *
     * @return `true` if the visibility is anything but `NONE`, `false` otherwise
     */
    public fun isNotNone(): Boolean = value != NONE

    /**
     * Checks if the visibility is exactly of the given level.
     *
     * @param visibility The visibility to compare to.
     * @return `true` if this visibility is equal to the given visibility
     * @throws IllegalArgumentException if the given visibility is `DEFAULT`.
     */
    @Suppress("FunctionNaming") // Keep backward compatibility for Java code.
    public fun `is`(visibility: Visibility): Boolean {
        checkNotDefault(visibility)
        return value == visibility
    }

    /**
     * Checks if the visibility is at least as allowing as the given one.
     *
     * `isAsLeast(NONE)` always returns `true`.
     * `isAtLeast(FULL)` returns `true` only if this visibility is exactly `FULL`.
     *
     * @param visibility The visibility to compare to.
     * @return `true` if this visibility is equal to the given visibility.
     * @throws IllegalArgumentException if the given visibility is `DEFAULT`.
     */
    public fun isAsLeast(visibility: Visibility): Boolean {
        checkNotDefault(visibility)
        return visibilities.hasEdgeConnecting(value, visibility)
    }

    override fun toString(): String = value.name

    @Suppress("UnstableApiUsage") // `ImmutableGraph` is in `@Beta`.
    public companion object {

        @Serial
        private const val serialVersionUID = 0L

        /**
         * An ordered immutable graph of the visibility levels.
         *
         * This graph is used for checking if a certain level includes another level.
         * For example, `FULL` includes `QUERY`.
         * To represent this, there is an edge from `FULL` to `QUERY` in the graph.
         *
         * @see isAsLeast
         */
        private val visibilities: ImmutableGraph<Visibility> by lazy {
            buildVisibilityGraph()
        }

        /**
         * Obtains an instance of `EntityVisibility` for the given entity state class.
         *
         * Returns `Optional.empty()`, if the given message is not an `Entity`,
         * i.e., has no corresponding option defined.
         *
         * @param stateClass The entity state class.
         * @return new instance.
         */
        @JvmStatic
        public fun of(stateClass: Class<out EntityState<*>>): Optional<EntityVisibility> {
            val descriptor = TypeName.of(stateClass).messageDescriptor()
            val option = EntityStateOption.valueOf(descriptor)
            if (option.isEmpty) {
                return Optional.empty<EntityVisibility>()
            }
            val entityOption = option.get()
            var visibility = entityOption.getVisibility()
            if (visibility == DEFAULT) {
                val kind = entityOption.getKind()
                visibility =
                    if (kind == PROJECTION) FULL else NONE
            }
            val result = EntityVisibility(visibility)
            return Optional.of<EntityVisibility>(result)
        }

        private fun checkNotDefault(visibility: Visibility) =
            require(visibility != DEFAULT) {
                "Unexpected visibility level: ${DEFAULT}."
            }

        /**
         * Builds the graph of the visibility hierarchy.
         *
         * @see visibilities
         */
        private fun buildVisibilityGraph(): ImmutableGraph<Visibility> {
            val mutableGraph = GraphBuilder
                .directed()
                .expectedNodeCount(Visibility.entries.size)
                .allowsSelfLoops(true)
                .build<Visibility>()

            mutableGraph.run {
                addNode(NONE)
                addNode(QUERY)
                addNode(SUBSCRIBE)
                addNode(FULL)

                putEdge(QUERY, NONE)
                putEdge(SUBSCRIBE, NONE)
                putEdge(FULL, SUBSCRIBE)
                putEdge(FULL, QUERY)
                putEdge(FULL, NONE)
            }

            mutableGraph.nodes().forEach { visibility ->
                mutableGraph.putEdge(visibility, visibility)
            }
            return ImmutableGraph.copyOf<Visibility>(mutableGraph)
        }
    }
}
