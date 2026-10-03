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

package io.spine.server.entity.given;

import io.spine.client.OrderBy;
import io.spine.client.ResponseFormat;

public final class RecordBasedRepositoryTestEnv {

    private static final String ENTITY_NAME_COLUMN = "name";

    /** Prevents instantiation of this test environment class. */
    private RecordBasedRepositoryTestEnv() {
    }

    /**
     * An order by {@linkplain #ENTITY_NAME_COLUMN entity name column}.
     */
    public static OrderBy orderByName(OrderBy.Direction direction) {
        return OrderBy.newBuilder()
                .setColumn(ENTITY_NAME_COLUMN)
                .setDirection(direction)
                .build();
    }

    public static ResponseFormat emptyFormat() {
        return ResponseFormat.getDefaultInstance();
    }
}
