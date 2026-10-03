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

package io.spine.dependency.lib

@Suppress("unused", "ConstPropertyName")
object Netty {
    // https://github.com/netty/netty/tags
    private const val version = "4.2.17.Final"
    const val common = "io.netty:netty-common:$version"
    const val buffer = "io.netty:netty-buffer:$version"
    const val transport = "io.netty:netty-transport:$version"
    const val handler = "io.netty:netty-handler:$version"
    const val codecHttp = "io.netty:netty-codec-http:$version"
}
