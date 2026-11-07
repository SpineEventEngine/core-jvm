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

package io.spine.server.commandbus;

import com.google.common.collect.ImmutableList;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.core.CommandId;
import io.spine.server.bus.EnvelopeValidator;
import io.spine.server.type.CommandEnvelope;
import io.spine.validation.ConstraintViolation;
import io.spine.string.TemplateString;
import io.spine.validation.Validate;
import io.spine.validation.ValidationError;

import java.util.List;
import java.util.Optional;

import static io.spine.protobuf.Messages.isDefault;

/**
 * Validates a command.
 *
 * <p>Ensures that:
 * <ol>
 *     <li>The command message {@linkplain CommandValidator#inspect(CommandEnvelope) conforms} to
 *         the options specified in the proto declaration of the message.
 *     <li>The command ID is populated.
 *     <li>The command context is not blank.
 * </ol>
 *
 * <p>The tenant of the command is checked by {@link TenantFilter} before the validation.
 */
final class CommandValidator implements EnvelopeValidator<CommandEnvelope> {

    @Override
    public Optional<ValidationError> validate(CommandEnvelope envelope) {
        var violations = inspect(envelope);
        if (violations.isEmpty()) {
            return Optional.empty();
        }
        var error = ValidationError.newBuilder()
                .addAllConstraintViolation(violations)
                .build();
        return Optional.of(error);
    }

    /**
     * Validates a command checking that its required fields are valid and
     * validates a command message according to Spine custom protobuf options.
     *
     * @param envelope a command to validate
     * @return constraint violations found
     */
    @VisibleForTesting
    static List<ConstraintViolation> inspect(CommandEnvelope envelope) {
        var result = new ViolationCheck(envelope);
        return result.build();
    }

    /**
     * Performs the command instance validation.
     */
    private static final class ViolationCheck {

        private static final String COMMAND_ID_CANNOT_BE_EMPTY = "Command ID cannot be empty.";

        private final CommandEnvelope command;
        private final ImmutableList.Builder<ConstraintViolation> result = ImmutableList.builder();

        private ViolationCheck(CommandEnvelope commandEnvelope) {
            this.command = commandEnvelope;
        }

        /**
         * Validates the passed command ID.
         */
        private static List<ConstraintViolation> validateId(CommandId id) {
            var violations = Validate.violationsOf(id);
            if (id.getUuid().isEmpty()) {
                var idViolation = ConstraintViolation.newBuilder()
                        .setMessage(TemplateString.newBuilder()
                                            .setWithPlaceholders(COMMAND_ID_CANNOT_BE_EMPTY))
                        .build();
                return ImmutableList.<ConstraintViolation>builder()
                        .addAll(violations)
                        .add(idViolation)
                        .build();
            }
            return violations;
        }

        private List<ConstraintViolation> build() {
            validateId();
            validateMessage();
            validateContext();
            return result.build();
        }

        private void validateId() {
            var violations = validateId(command.id());
            if (!violations.isEmpty()) {
                result.addAll(violations);
            }
        }

        private void validateMessage() {
            var message = command.message();
            if (isDefault(message)) {
                addViolation("Non-default command message must be set.");
            }
            var messageViolations = Validate.violationsOf(message);
            result.addAll(messageViolations);
        }

        private void validateContext() {
            if (isDefault(command.context())) {
                addViolation("Non-default command context must be set.");
            }
        }

        private void addViolation(String message) {
            var templateString = TemplateString.newBuilder()
                    .setWithPlaceholders(message)
                    .build();
            result.add(ConstraintViolation.newBuilder()
                               .setMessage(templateString)
                               .build());
        }
    }
}
