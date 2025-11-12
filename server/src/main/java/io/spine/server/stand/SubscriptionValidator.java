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
package io.spine.server.stand;

import com.google.protobuf.ProtocolMessageEnum;
import io.spine.base.Error;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionValidationError;
import io.spine.server.tenant.TenantAwareFunction;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;

/**
 * Validates the {@linkplain Subscription} instances submitted to {@linkplain Stand}.
 */
final class SubscriptionValidator extends RequestValidator<Subscription> {

    private final SubscriptionRegistry registry;

    /**
     * Creates an instance of {@code SubscriptionValidator} based on the subscription registry.
     *
     * @param registry
     *         the registry to validate the subscription against
     */
    SubscriptionValidator(SubscriptionRegistry registry) {
        super();
        this.registry = registry;
    }

    @Override
    protected SubscriptionValidationError invalidMessageErrorCode() {
        return SubscriptionValidationError.INVALID_SUBSCRIPTION;
    }

    @Override
    protected ProtocolMessageEnum unsupportedTargetErrorCode() {
        return SubscriptionValidationError.UNKNOWN_SUBSCRIPTION;
    }

    @Override
    protected InvalidSubscriptionException invalidMessageException(String exceptionMsg,
                                                                   Subscription subscription,
                                                                   Error error) {
        return new InvalidSubscriptionException(exceptionMsg, subscription, error);
    }

    @Override
    protected boolean isSupported(Subscription request) {
        return checkInRegistry(request);
    }

    @Override
    protected String errorMessage(Subscription request) {
        return "Cannot find the subscription in the registry";
    }

    @Override
    protected InvalidRequestException unsupportedException(Subscription request,
                                                           Error error) {
        return new InvalidSubscriptionException(errorMessage(request), request, error);
    }

    private boolean checkInRegistry(Subscription request) {
        var tenantId = request.getTopic()
                              .getContext()
                              .getTenantId();
        var result = new TenantAwareFunction<Subscription, Boolean>(tenantId) {

            @Override
            public Boolean apply(@Nullable Subscription input) {
                requireNonNull(input);
                var result = registry.containsId(input.getId());
                return result;
            }
        }.execute(request);

        checkNotNull(result);
        return result;
    }
}
