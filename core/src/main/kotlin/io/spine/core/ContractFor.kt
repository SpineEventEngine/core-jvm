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

package io.spine.core

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.reflect.KClass

/**
 * Marks an abstract handler method.
 *
 * When there is a need to define an abstract base for a handler method, the base method must be
 * marked with `@ContractFor`. Such a base method is called a "contract method",
 * or simply a "contract". The `handler` type of the contract must match the type of
 * the handler method.
 *
 * In cases when there is a limitation on the allowed access modifiers for a handler method,
 * a method that inherits a contract may be declared `protected`.
 *
 * Example.
 * The abstract base that defines the contract method:
 *
 * ```java
 *   public abstract class ProjectCreatedSubscriber extends AbstractEventSubscriber {
 *
 *       @ContractFor(handler = Subscribe.class)
 *       protected abstract void on(ProjectCreated event);
 *
 *       // Utility methods required by descendants.
 *       protected final UserId ownerFrom(Project project) { /* ... */ }
 *       protected final boolean isAdmin(UserId user) { /* ... */ }
 *   }
 * ```
 *
 * The implementation:
 *
 * ```java
 *   public final class LoggingSubscriber extends ProjectCreatedSubscriber {
 *
 *       @Subscribe
 *       protected void on(ProjectCreated event) {
 *           // ...
 *       }
 *   }
 * ```
 *
 * Note that the contract method serves only for convenience. It has no effect on signal processing.
 * There are no changes to the default signal dispatching either.
 *
 * For example, commands can only have one handler method. Adding a contract method does
 * not change that. Yet, command handler methods can have contracts. If the contract
 * is generalized, i.e., the method accepts an argument of a generic type, then
 * the contract implementations may accept different types of commands, while still maintaining
 * a shared portion of the contract.
 *
 * Consider the following example. Here is an abstract commander definition:
 *
 * ```kotlin
 *   abstract class SplittingCommander<C : CommandMessage> : AbstractCommander() {
 *
 *       @ContractFor(handler = Command::class)
 *       protected abstract fun on(cmd: C): Pair<FreezeProject, NotifyAdmin>;
 *   }
 * ```
 * And here are the implementations:
 *
 * ```kotlin
 *   class PaymentSafety : SplittingCommander<InvalidatePaymentMethod> {
 *
 *       @Command
 *       protected fun on(cmd: InvalidatePaymentMethod): Pair<FreezeProject, NotifyAdmin> {
 *           // ...
 *       }
 *   }
 *
 *   // ...
 *
 *   class OrgCleansing : SplittingCommander<DivideOrganization> {
 *
 *       @Command
 *       protected fun on(cmd: DivideOrganization): Pair<FreezeProject, NotifyAdmin> {
 *           // ...
 *       }
 *   }
 * ```
 *
 * ## Motivation
 *
 * We need contract methods to enable creating small isolated "frameworks" based on Spine.
 * Such "frameworks" limit the possible usage of the general signal handling mechanism to
 * a predefined set of actions. For example, we would like to define a custom event reactor, which
 * only accepts one type of events (per reactor class) and produces some events as the output.
 * We create such a class, inheriting the standard framework reactor class, and define
 * a contract method with the desired API. The users of the new class will quickly identify
 * the need to implement a method, without diving into the framework details.
 *
 * However, in order to support such hierarchies, we need to give some (but not all) handler
 * methods a higher degree of freedom. For instance, such methods need to be `protected`, which is
 * prohibited for most signal handlers. So, in order to discriminate contracts from general signal
 * handlers at runtime, we mark contracts with this annotation.
 *
 * @see AcceptsContracts
 */
@Retention(RUNTIME)
@Target(FUNCTION)
public annotation class ContractFor(

    /**
     * Type of the annotation marking methods which can implement the associated contract method.
     *
     * Such an annotation must be marked with [AcceptsContracts].
     *
     * Note. This property is of type [KClass]. Kotlin compiler converts it to Java's `Class`
     * automatically to maintain Java interop.
     */
    public val handler: KClass<out Annotation>
)
