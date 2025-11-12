/*
 * Copyright 2023, TeamDev. All rights reserved.
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
package io.spine.server.procman.given.pm

import io.spine.base.Identifier
import io.spine.test.procman.quiz.PmAnswer
import io.spine.test.procman.quiz.PmQuestionId
import io.spine.test.procman.quiz.PmQuizId
import io.spine.test.procman.quiz.pmQuizId
import io.spine.test.procman.quiz.command.PmAnswerQuestion
import io.spine.test.procman.quiz.command.PmStartQuiz
import io.spine.test.procman.quiz.command.pmAnswerQuestion
import io.spine.test.procman.quiz.command.pmStartQuiz
import io.spine.test.procman.quiz.pmAnswer
import io.spine.test.procman.quiz.pmQuestionId

/**
 * Factory methods for creating test values.
 */
object QuizGiven {

    fun newQuizId(): PmQuizId = pmQuizId {
        id = Identifier.newUuid()
    }

    fun startQuiz(id: PmQuizId, problems: Iterable<PmQuestionId>): PmStartQuiz = pmStartQuiz {
        quiz = id
        question.addAll(problems)
    }

    fun answerQuestion(id: PmQuizId, answer: PmAnswer): PmAnswerQuestion = pmAnswerQuestion {
        quiz = id
        this.answer = answer
    }

    fun newQuestionId(): PmQuestionId =
        pmQuestionId {
            id = Identifier.newUuid()
        }

    @JvmOverloads
    fun newAnswer(
        id: PmQuestionId = newQuestionId(),
        correct: Boolean = true
    ): PmAnswer = pmAnswer {
        question = id
        this.correct = correct
    }
}
