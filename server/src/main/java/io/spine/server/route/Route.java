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

package io.spine.server.route;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a <strong>static</strong> method of an {@link io.spine.server.entity.Entity Entity}
 * class for arranging message routing for this class of entities.
 *
 * <p>The method must accept a {@link io.spine.base.SignalMessage SignalMessage} as the first
 * parameter, and <em>may</em> contain corresponding
 * {@link io.spine.base.MessageContext MessageContext} as the second parameter.
 *
 * <p>The method <em>must</em> return one identifier of type {@code <I>} for {@link Unicast}
 * dispatching, and <em>may</em> return an {@code Iterable<I>}, if this message can be dispatched
 * via {@linkplain Multicast multicast}.
 *
 * <p>When used in Java, the method <strong>must</strong> be either package-private or
 * {@code protected} for being accessible from the generated code in the same package.
 * <p>The {@code protected} modifier should be used <em>only</em> in the very rare cases of
 * dealing with {@linkplain io.spine.core.ContractFor entity class hierarchies}.
 *
 * <p>When used in Kotlin, the methods <strong>must</strong> be {@code internal},
 * with the {@code @Route} annotation of a companion object function.
 *
 * <p>Using the annotated functions, Spine Compiler will generate classes implementing
 * the {@link io.spine.server.route.setup.RoutingSetup RoutingSetup} interface.
 * Repositories will use these classes for configuring their routing schemas.
 *
 * @see io.spine.server.route.setup.RoutingSetup
 * @see io.spine.server.entity.EventDispatchingRepository#setupEventRouting(EventRouting)
 * @see io.spine.server.aggregate.AggregateRepository#setupCommandRouting(CommandRouting)
 */
@SuppressWarnings("JavadocReference") // We reference protected methods of repository classes.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Route {
}
