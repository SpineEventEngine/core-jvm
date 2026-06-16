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
package io.spine.core

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

/**
 * Filters events delivered to a handler method.
 *
 * To apply filtering to a handler method, annotate the first parameter of the method.
 *
 * For example, the following method would be invoked only if the owner of
 * the project is `admin@ackme.net`:
 * ```
 *   @React
 *   ProjectDeletedForever on(@Where(field = "owner.email", equals = "admin@ackme.net")
 *                            ProjectRanOutOfMoney e) {
 *       ...
 *   }
 * ```
 *
 * # Filtering Events by a Field Value
 *
 * If a field filter is defined, only the events matching this filter are passed to the handler
 * method.
 *
 * Only methods that accept an event may add filters. Entity subscribers cannot use this kind of
 * filtering, neither can methods that receive a command. Declaring a `@Where` filter on a parameter
 * of such a method will cause a runtime error. See the annotation [AcceptsFilters] for more info
 * on which methods can have a filer.
 *
 * A single class may define a number of handler methods with different field filters. Though,
 * all the field filters must target the same field. For example, this setup is valid:
 * ```
 *   @React
 *   SubscriptionEnded onExpired(@Where(field = "subscription.status", equals = "EXPIRED")
 *                               UserLoggedIn event) {
 *       // Handle expired subscription.
 *   }
 *
 *   @React
 *   FreeUserLoggedIn onInactive(@Where(field = "subscription.status", equals = "INACTIVE")
 *                               UserLoggedIn event) {
 *      // Handle inactive subscription.
 *   }
 *
 *   @React
 *   Optional<PayingUserLoggedIn> on(UserLoggedIn event) {
 *      // Handle other cases.
 *   }
 * ```
 *
 * And this one is not:
 * ```
 *   @Subscribe
 *   void onExpired(@Where(field = "subscription.status", equals = "EXPIRED")
 *                  UserLoggedIn event) {
 *      ...
 *   }
 *
 *   @Subscribe
 *   void onUnknownBilling(@Where(field = "payment_method.status", equals = "UNSET")
 *                         UserLoggedIn event) {
 *       // Error, different field paths used in the same class for the same event type.
 *   }
 * ```
 */
@Retention(RUNTIME)
@Target(VALUE_PARAMETER)
@MustBeDocumented
public annotation class Where(

    /**
     * The [path to the field][io.spine.base.FieldPath] of the event message to filter by.
     */
    public val field: String,

    /**
     * The expected value of the field.
     *
     *
     * The value converted with help of [Stringifier][io.spine.string.Stringifier]s into
     * the type of the actual value of the message field.
     */
    public val equals: String
)
