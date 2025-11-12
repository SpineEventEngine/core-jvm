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

package io.spine.server;

import io.spine.annotation.VisibleForTesting;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Strings.emptyToNull;
import static io.spine.server.DeploymentType.APPENGINE_CLOUD;
import static io.spine.server.DeploymentType.APPENGINE_EMULATOR;
import static io.spine.server.DeploymentType.STANDALONE;
import static java.util.Optional.ofNullable;

/**
 * The default implementation of {@linkplain DeploymentType deployment type} 
 * {@linkplain Supplier supplier}.
 */
final class DeploymentDetector implements Supplier<DeploymentType> {

    @VisibleForTesting
    static final String APP_ENGINE_ENVIRONMENT_PATH = "com.google.appengine.runtime.environment";
    @VisibleForTesting
    static final String APP_ENGINE_ENVIRONMENT_PRODUCTION_VALUE = "Production";
    @VisibleForTesting
    static final String APP_ENGINE_ENVIRONMENT_DEVELOPMENT_VALUE = "Development";

    /**
     * The deployment type is instantiated lazily to a non-{@code null} value during {@link #get()}.
     *
     * <p>Value is never changed after it is initially set.
     */
    private @MonotonicNonNull DeploymentType deploymentType;

    /** Prevent instantiation from outside. */
    private DeploymentDetector() {
    }

    public static Supplier<DeploymentType> newInstance() {
        return new DeploymentDetector();
    }

    @Override
    public DeploymentType get() {
        if (deploymentType == null) {
            deploymentType = detect();
        }
        return deploymentType;
    }

    private static DeploymentType detect() {
        var gaeEnvironment = getProperty(APP_ENGINE_ENVIRONMENT_PATH);
        if (gaeEnvironment.isPresent()) {
            if (APP_ENGINE_ENVIRONMENT_DEVELOPMENT_VALUE.equals(gaeEnvironment.get())) {
                return APPENGINE_EMULATOR;
            }
            if (APP_ENGINE_ENVIRONMENT_PRODUCTION_VALUE.equals(gaeEnvironment.get())) {
                return APPENGINE_CLOUD;
            }
        }
        return STANDALONE;
    }

    @SuppressWarnings("AccessOfSystemProperties") /*  Based on system property. */
    private static Optional<String> getProperty(String path) {
        return ofNullable(emptyToNull(System.getProperty(path)));
    }
}
