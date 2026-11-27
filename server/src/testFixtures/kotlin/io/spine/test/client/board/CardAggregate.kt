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

package io.spine.test.client.board

import io.spine.server.aggregate.Aggregate
import io.spine.server.command.Assign
import io.spine.test.client.board.CardStatus.DONE
import io.spine.test.client.board.CardStatus.OPEN
import io.spine.test.client.board.command.AssignCard
import io.spine.test.client.board.command.CompleteCard
import io.spine.test.client.board.command.CreateCard
import io.spine.test.client.board.command.ReopenCard
import io.spine.test.client.board.event.CardAssigned
import io.spine.test.client.board.event.CardCompleted
import io.spine.test.client.board.event.CardCreated
import io.spine.test.client.board.event.CardReopened
import io.spine.test.client.board.event.cardAssigned
import io.spine.test.client.board.event.cardCompleted
import io.spine.test.client.board.event.cardCreated
import io.spine.test.client.board.event.cardReopened

/**
 * A card on a board.
 *
 * The commands change the columns of the card, so that the card starts or stops matching
 * the filters of client subscriptions.
 */
internal class CardAggregate : Aggregate<CardId, Card, Card.Builder>() {

    @Assign
    fun handle(c: CreateCard): CardCreated {
        alter {
            title = c.title
            status = OPEN
        }
        return cardCreated {
            id = c.id
            title = c.title
        }
    }

    @Assign
    fun handle(c: AssignCard): CardAssigned {
        alter {
            assignee = c.assignee
        }
        return cardAssigned {
            id = c.id
            assignee = c.assignee
        }
    }

    @Assign
    fun handle(c: CompleteCard): CardCompleted {
        alter {
            status = DONE
        }
        return cardCompleted {
            id = c.id
        }
    }

    @Assign
    fun handle(c: ReopenCard): CardReopened {
        alter {
            status = OPEN
        }
        return cardReopened {
            id = c.id
        }
    }
}
