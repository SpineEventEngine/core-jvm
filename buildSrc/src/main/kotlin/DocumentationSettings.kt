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

/**
 * The documentation settings specific to this project.
 *
 * @see <a href="https://kotlinlang.org/docs/dokka-gradle.html#source-link-configuration">
 *     Dokka source link configuration</a>
 */
@Suppress("ConstPropertyName")
object DocumentationSettings {

    /**
     * Settings passed to Dokka for
     * [sourceLink][[org.jetbrains.dokka.gradle.engine.parameters.DokkaSourceLinkSpec]
     */
    object SourceLink {

        /**
         * The URL of the remote source code
         * [location][org.jetbrains.dokka.gradle.engine.parameters.DokkaSourceLinkSpec.remoteUrl].
         */
        const val url: String = "https://github.com/SpineEventEngine/base/tree/master/src"

        /**
         * The suffix used to append the source code line number to the URL.
         *
         * The suffix depends on the online code repository.
         *
         * @see <a href="https://kotlinlang.org/docs/dokka-gradle.html#fwor0d_534">
         *     remoteLineSuffix</a>
         */
        const val lineSuffix: String = "#L"
    }
}
