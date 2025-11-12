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

package io.spine.server.entity;

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UnknownFieldSet;
import io.spine.base.CommandMessage;
import io.spine.core.CommandContext;
import io.spine.server.route.DefaultCommandRoute;
import io.spine.test.entity.command.EntCreateProject;
import io.spine.testdata.Sample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.testing.TestValues.nullRef;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`DefaultCommandRoute` should")
class DefaultCommandRouteTest {

    @Test
    @DisplayName("get ID from command message")
    void getIdFromCommand() {
        var msg = Sample.messageOfType(EntCreateProject.class);

        assertTrue(DefaultCommandRoute.exists(msg));

        assertThat(DefaultCommandRoute
                           .newInstance(io.spine.test.entity.ProjectId.class)
                           .invoke(msg, CommandContext.getDefaultInstance()))
                .isEqualTo(msg.getProjectId());
    }

    @Test
    @DisplayName("test if command message is routable")
    void emptyOrNot() {
        assertThat(DefaultCommandRoute.exists(new StubCommand(true)))
                .isFalse();
        assertThat(DefaultCommandRoute.exists(new StubCommand(false)))
                .isTrue();
    }

    /**
     * Stub class which simulates empty command message.
     */
    @Immutable
    private static final class StubCommand implements CommandMessage {

        private static final long serialVersionUID = 0L;
        private final boolean empty;

        private StubCommand(boolean empty) {
            this.empty = empty;
        }

        @Override
        public void writeTo(CodedOutputStream output) {
            // Do nothing.
        }

        @Override
        public int getSerializedSize() {
            return 0;
        }

        @Override
        public Parser<? extends Message> getParserForType() {
            return nullRef();
        }

        @Override
        public ByteString toByteString() {
            return nullRef();
        }

        @SuppressWarnings("ZeroLengthArrayAllocation")
        @Override
        public byte[] toByteArray() {
            return new byte[0];
        }

        @Override
        public void writeTo(OutputStream output) {
            // Do nothing.
        }

        @Override
        public void writeDelimitedTo(OutputStream output) {
            // Do nothing.
        }

        @Override
        public Builder newBuilderForType() {
            return nullRef();
        }

        @Override
        public Builder toBuilder() {
            return nullRef();
        }

        @Override
        public Message getDefaultInstanceForType() {
            return nullRef();
        }

        @Override
        public boolean isInitialized() {
            return false;
        }

        @Override
        public List<String> findInitializationErrors() {
            return nullRef();
        }

        @Override
        public String getInitializationErrorString() {
            return nullRef();
        }

        @Override
        public Descriptors.Descriptor getDescriptorForType() {
            return empty
                   ? Empty.getDescriptor()
                   : Timestamp.getDescriptor();
        }

        @Override
        public Map<Descriptors.FieldDescriptor, Object> getAllFields() {
            return nullRef();
        }

        @Override
        public boolean hasOneof(Descriptors.OneofDescriptor oneof) {
            return false;
        }

        @Override
        public Descriptors.FieldDescriptor getOneofFieldDescriptor(
                Descriptors.OneofDescriptor oneof) {
            return nullRef();
        }

        @Override
        public boolean hasField(Descriptors.FieldDescriptor field) {
            return false;
        }

        @Override
        public Object getField(Descriptors.FieldDescriptor field) {
            return nullRef();
        }

        @Override
        public int getRepeatedFieldCount(Descriptors.FieldDescriptor field) {
            return 0;
        }

        @Override
        public Object getRepeatedField(Descriptors.FieldDescriptor field, int index) {
            return nullRef();
        }

        @Override
        public UnknownFieldSet getUnknownFields() {
            return nullRef();
        }
    }
}
