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

package io.spine.dependency.local

/**
 * Dependencies on ProtoTap plugins.
 *
 * See [`SpineEventEngine/ProtoTap`](https://github.com/SpineEventEngine/ProtoTap/).
 */
@Suppress(
    "unused" /* Some subprojects do not use ProtoTap directly. */,
    "ConstPropertyName" /* We use a custom convention for artifact properties. */,
    "MemberVisibilityCanBePrivate" /* The properties are used directly by other subprojects. */,
)
object ProtoTap {
    const val group = Spine.toolsGroup
    const val version = "0.17.2"
    const val gradlePluginId = "io.spine.prototap"
    const val api = "$group:prototap-api:$version"
    const val gradlePlugin = "$group:prototap-gradle-plugin:$version"
    const val protocPlugin = "$group:prototap-protoc-plugin:$version"
}
