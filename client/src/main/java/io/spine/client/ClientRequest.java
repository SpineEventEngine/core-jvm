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

package io.spine.client;

import com.google.common.collect.ImmutableList;
import io.spine.base.CommandMessage;
import io.spine.base.EntityState;
import io.spine.base.EventMessage;
import io.spine.core.UserId;
import io.spine.query.EntityQuery;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.type.PubPreconditions.requirePublished;

/**
 * Entry point for creating client requests.
 *
 * <p>An instance of this class is obtained via
 * {@link Client#onBehalfOf(UserId)} or {@link Client#asGuest()} methods and then used for creating
 * a specific client request e.g. for {@linkplain ClientRequest#command(CommandMessage) posting
 * a command}.
 *
 * <p>A client request may be customized using fluent API provided by the classes derived
 * from {@link ClientRequestBase}.
 *
 * <p>Some features such as running an {@link EntityQuery} are available
 * {@linkplain #run(EntityQuery) directly from this class}.
 *
 * @see Client
 */
// we want to have DSL for calls encapsulated in this class.
public class ClientRequest extends ClientRequestBase {

    /**
     * Creates a new instance with the given user ID and the reference to
     * the {@code client} instance that is going to send the request.
     */
    ClientRequest(UserId user, Client client) {
        super(user, client);
    }

    /**
     * Creates a builder for customizing command request.
     */
    public CommandRequest command(CommandMessage c) {
        checkNotNull(c);
        requirePublished(c);
        return new CommandRequest(this, c);
    }

    /**
     * Creates a builder for customizing subscription for the passed entity state type.
     */
    public <S extends EntityState<?>> SubscriptionRequest<S> subscribeTo(Class<S> type) {
        checkNotNull(type);
        requirePublished(type);
        return new SubscriptionRequest<>(this, type);
    }

    /**
     * Creates a builder for customizing subscription for the passed event type.
     */
    public <E extends EventMessage> EventSubscriptionRequest<E> subscribeToEvent(Class<E> type) {
        checkNotNull(type);
        requirePublished(type);
        return new EventSubscriptionRequest<>(this, type);
    }

    /**
     * Runs the {@link EntityQuery} and returns the matched entity states.
     *
     * <p>Usage example:
     * <pre>
     *
     * Customer.Query query = Customer.query()
     *              .id().in(westCoastCustomerIds())
     *              .type().is(CustomerType.PERMANENT)
     *              .discountPercent().is(10)
     *              .companySize().is(Company.Size.SMALL)
     *              .sortAscendingBy(name())
     *              .limit(20)
     *              .build();
     *{@literal ImmutableList<Customer> customers = client.onBehalfOf(currentUser).run(query);}
     * </pre>
     *
     * @param <S>
     *         the type of the entity state for which the query is run
     */
    public <S extends EntityState<?>> ImmutableList<S> run(EntityQuery<?, S, ?> query) {
        requirePublished(query.subject().recordType());
        var request = new QueryRequest<>(this, query);
        var results = request.run();
        return results;
    }
}
