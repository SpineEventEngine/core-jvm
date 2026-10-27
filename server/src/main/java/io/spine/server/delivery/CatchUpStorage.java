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

package io.spine.server.delivery;

import com.google.common.collect.ImmutableList;
import io.spine.annotation.SPI;
import io.spine.annotation.VisibleForTesting;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.storage.StorageFactory;
import io.spine.type.TypeUrl;

import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.collect.Streams.stream;
import static io.spine.protobuf.AnyPacker.unpack;

/**
 * A storage for the state of the catch-up processes.
 *
 * <p>The catch-up processes of all Bounded Contexts and all tenants share one instance
 * of this storage, that of the {@link Delivery}. The {@code Delivery} reads all the jobs
 * for each page of messages it delivers, and such a page holds the messages of every
 * context and every tenant. So the storage must be single-tenant.
 *
 * <p>The records are those of the process managers that run the catch-ups.
 */
@SPI
public class CatchUpStorage extends EntityRecordStorage<CatchUpId, CatchUp> {

    /**
     * Creates a new instance of this storage.
     *
     * <p>The storage of a {@code Delivery} must be single-tenant, so that the {@code Delivery}
     * could read the jobs of every tenant at once.
     *
     * @param factory
     *         storage factory to create an underlying record storage
     * @param multitenant
     *         whether {@code CatchUpStorage} should be multi-tenant; must be {@code false}
     *         for the storage of a {@code Delivery}
     */
    public CatchUpStorage(StorageFactory factory, boolean multitenant) {
        super(Delivery.contextSpec(multitenant), factory, CatchUpProcess.class);
    }

    /**
     * Reads all the catch-up jobs.
     */
    public Iterator<CatchUp> readJobs() {
        return allJobs().iterator();
    }

    /**
     * Reads the catch-up job with the given ID.
     */
    public Optional<CatchUp> readJob(CatchUpId id) {
        return read(id).map(CatchUpStorage::jobOf);
    }

    /**
     * Reads all the catch-up jobs that update the projection of the specified type.
     *
     * @param projectionType
     *         the type of the projection state to use for filtering
     */
    public Iterator<CatchUp> readByType(TypeUrl projectionType) {
        var type = projectionType.value();
        return allJobs()
                .filter(job -> job.getId().getProjectionType().equals(type))
                .iterator();
    }

    /**
     * Does nothing.
     *
     * <p>The catch-up processes of every System context share this storage, and the repository
     * of each closes its storage when its context closes. So this method does nothing, and
     * the storage stays open for the {@code Delivery}.
     */
    @Override
    public void close() {
        // Do nothing.
    }

    /**
     * Clears this storage by removing all records.
     */
    @VisibleForTesting
    void clear() {
        var allIds = ImmutableList.copyOf(index());
        allIds.forEach(this::delete);
    }

    private Stream<CatchUp> allJobs() {
        return stream(readAll()).map(CatchUpStorage::jobOf);
    }

    private static CatchUp jobOf(EntityRecord record) {
        return unpack(record.getState(), CatchUp.class);
    }
}
