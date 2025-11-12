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

package io.spine.server.security

import io.spine.code.java.PackageName
import io.spine.server.Server
import io.spine.server.security.CallerProvider.previousCallerClass
import io.spine.system.server.SystemContext
import java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE
import java.lang.StackWalker.StackFrame
import java.util.stream.Stream

/**
 * Controls which class is allowed to call a method.
 */
public object Security {

    private const val TESTING_SERVER_PACKAGE = "io.spine.testing.server"

    /**
     * Throws [SecurityException] if the calling class does not belong to
     * the Spine Event Engine framework or its testing utilities.
     */
    @JvmStatic
    public fun allowOnlyFrameworkServer() {
        val callingClass = previousCallerClass()
        if (!belongsToServer(callingClass)) {
            throw notAllowedCaller(callingClass)
        }
    }

    /**
     * Tells if the calling class belongs to the server packages for which a call is permitted.
     *
     * The allowed packages are:
     *  1. [io.spine.server]
     *  2. [io.spine.system.server]
     *  3. [io.spine.testing.server][Security.TESTING_SERVER_PACKAGE]
     *
     * @param callingClass The class to check.
     * @return `true` if the class is allowed to make a call, `false` otherwise.
     */
    private fun belongsToServer(callingClass: Class<*>): Boolean {
        val serverPackage = PackageName.of(Server::class.java)
        val systemServerPackage = PackageName.of(
            SystemContext::class.java
        )
        val callingClassName = callingClass.name
        val result =
            callingClassName.startsWith(serverPackage.value())
                    || callingClassName.startsWith(systemServerPackage.value())
                    || callingClassName.startsWith(TESTING_SERVER_PACKAGE)
        return result
    }

    private fun notAllowedCaller(callingClass: Class<*>): SecurityException =
        throw SecurityException(
            "The class `${callingClass.name}` is not allowed to make this call."
        )
}

/**
 * Provides information about the class calling a method.
 */
private object CallerProvider {

    private val stackWalker: StackWalker = StackWalker.getInstance(RETAIN_CLASS_REFERENCE)

    /**
     * Obtains the class of the object which calls the method from which
     * this method is being called.
     */
    @Suppress("unused")
    fun callerClass(): Class<*> {
        return stackWalker.walk { frames ->
            frames.findCallerClass(2)
        }
    }

    /**
     * Obtains the class preceding in the call chain the class which calls
     * the method from which this method is being called.
     */
    fun previousCallerClass(): Class<*> {
        return stackWalker.walk { frames ->
            frames.findCallerClass(3)
        }
    }

    private fun Stream<StackFrame>.findCallerClass(skipCount: Long) =
        skip(skipCount)
            .filter { frame -> frame.declaringClass != CallerProvider::class.java }
            .filter { frame -> frame.toStackTraceElement().moduleName != "java.base" }
            .findFirst()
            .map { frame -> frame.declaringClass }
            .get() // We're safe because the stacktrace will be deeper than 3.
}
