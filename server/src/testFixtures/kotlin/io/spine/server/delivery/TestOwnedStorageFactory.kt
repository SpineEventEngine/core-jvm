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

package io.spine.server.delivery

import io.spine.server.storage.StorageFactory

/**
 * A storage factory that a delivery test gives to each application it builds.
 *
 * The applications share the [delegate]. Closing an application closes this factory.
 * [close] leaves the delegate open, because the test still reads the storages of its
 * [Delivery] after an application is closed, and another application of the test may
 * still be open. The test closes the delegate once, after every application, when
 * the test ends.
 *
 * @property delegate The storage factory of the test.
 */
internal class TestOwnedStorageFactory(
    private val delegate: StorageFactory
) : StorageFactory by delegate {

    /**
     * Leaves the [delegate] open for the test to close when the test ends.
     */
    override fun close() = Unit
}
