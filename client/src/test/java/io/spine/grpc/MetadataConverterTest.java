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

package io.spine.grpc;

import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Metadata;
import io.spine.base.Error;
import io.spine.testing.UtilityClassTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.grpc.MetadataConverter.toError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`MetadataConverter` should")
class MetadataConverterTest extends UtilityClassTest<MetadataConverter> {

    MetadataConverterTest() {
        super(MetadataConverter.class);
    }

    @SuppressWarnings("ConstantConditions") // A part of the test.
    @Test
    @DisplayName("convert `Error` to `Metadata`")
    void convertError() throws InvalidProtocolBufferException {
        var error = Error.getDefaultInstance();
        var metadata = MetadataConverter.toMetadata(error);
        var bytes = metadata.get(MetadataConverter.KEY);
        assertEquals(error, Error.parseFrom(bytes));
    }

    @Test
    @DisplayName("convert `Metadata` to `Error`")
    void convertMetadata() {
        var expectedError = Error.getDefaultInstance();
        var metadata = MetadataConverter.toMetadata(expectedError);

        var optional = toError(metadata);
        assertTrue(optional.isPresent());
        assertEquals(expectedError, optional.get());
    }

    @Test
    @DisplayName("return absent when converting empty `Metadata`")
    void processEmptyMetadata() {
        var metadata = new Metadata();

        assertThat(toError(metadata))
              .isEmpty();
    }

    @Test
    @DisplayName("throw wrapped `InvalidProtocolBufferException` when Metadata bytes are invalid")
    void throwOnInvalidBytes() {
        var metadata = new Metadata();
        metadata.put(MetadataConverter.KEY, new byte[]{(byte) 1});

        var e = assertThrows(
                IllegalStateException.class,
                () -> toError(metadata), "`InvalidProtocolBufferException` was not thrown."
        );

        assertThat(e)
                .hasCauseThat()
                .isInstanceOf(InvalidProtocolBufferException.class);
    }
}
