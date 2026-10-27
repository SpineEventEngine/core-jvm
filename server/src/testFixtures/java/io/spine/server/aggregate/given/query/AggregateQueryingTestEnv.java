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

package io.spine.server.aggregate.given.query;

import com.google.common.collect.ImmutableList;
import io.spine.net.Url;
import io.spine.server.DefaultRepository;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.command.Assign;
import io.spine.test.aggregate.query.MRPhoto;
import io.spine.test.aggregate.query.MRPhotoId;
import io.spine.test.aggregate.query.MRPhotoType;
import io.spine.test.aggregate.query.MRSoundRecord;
import io.spine.test.aggregate.query.command.MRArchivePhoto;
import io.spine.test.aggregate.query.command.MRDeletePhoto;
import io.spine.test.aggregate.query.command.MRUploadPhoto;
import io.spine.test.aggregate.query.command.MRUploadSound;
import io.spine.test.aggregate.query.event.MRPhotoArchived;
import io.spine.test.aggregate.query.event.MRPhotoDeleted;
import io.spine.test.aggregate.query.event.MRPhotoUploaded;
import io.spine.test.aggregate.query.event.MRSoundUploaded;

import java.util.Collection;

import static io.spine.test.aggregate.query.MRPhotoType.CROP_FRAME;
import static io.spine.test.aggregate.query.MRPhotoType.FULL_FRAME;
import static io.spine.test.aggregate.query.MRPhotoType.THUMBNAIL;

public final class AggregateQueryingTestEnv {

    private static final MRPhoto spineLogo = newPhoto(THUMBNAIL,
                                                      "spine.io/logo",
                                                      "Spine Logo",
                                                      200,
                                                      200);
    private static final MRPhoto projectsLogo = newPhoto(CROP_FRAME,
                                                         "projects.tm/logo",
                                                         "Projects Logo",
                                                         1000,
                                                         800);
    private static final MRPhoto jxBrowserLogo = newPhoto(FULL_FRAME,
                                                          "teamdev.com/jxbrowser/logo",
                                                          "JxBrowser Logo",
                                                          7000,
                                                          7000);

    /**
     * Prevents the utility class instantiation.
     */
    private AggregateQueryingTestEnv() {
    }

    public static Collection<MRPhoto> givenPhotos() {
        return ImmutableList.of(spineLogo200by200(), projectLogo1000by800(), jxBrowserLogo7K());
    }

    public static MRPhoto jxBrowserLogo7K() {
        return jxBrowserLogo;
    }

    public static MRPhoto projectLogo1000by800() {
        return projectsLogo;
    }

    public static MRPhoto spineLogo200by200() {
        return spineLogo;
    }

    private static MRPhoto newPhoto(MRPhotoType type,
                                    String url,
                                    String altText,
                                    int width,
                                    int height) {
        var fullSizeUrl = Url.newBuilder()
                .setSpec(url)
                .buildPartial();
        var thumbnail = Url.newBuilder()
                .setSpec(url + "-thumbnail")
                .buildPartial();
        var photo = MRPhoto.newBuilder()
                .setId(MRPhotoId.generate())
                .setFullSizeUrl(fullSizeUrl)
                .setThumbnailUrl(thumbnail)
                .setAltText(altText)
                .setSourceType(type)
                .setWidth(width)
                .setHeight(height)
                .build();
        return photo;
    }

    // This suppression makes sense only to Error Prone.
    @SuppressWarnings("unchecked")  // as per declaration of the `DefaultRepository`;
    public static AggregateRepository<MRPhotoId, PhotoAggregate, MRPhoto> newPhotosRepository() {
        var repo = DefaultRepository.of(PhotoAggregate.class);
        return (AggregateRepository<MRPhotoId, PhotoAggregate, MRPhoto>) repo;
    }

    public static MRUploadPhoto upload(MRPhoto state) {
        return MRUploadPhoto.newBuilder()
                .setId(state.getId())
                .setFullSizeUrl(state.getFullSizeUrl())
                .setThumbnailUrl(state.getThumbnailUrl())
                .setAltText(state.getAltText())
                .setWidth(state.getWidth())
                .setHeight(state.getHeight())
                .setSourceType(state.getSourceType())
                .build();
    }

    public static MRArchivePhoto archive(MRPhoto photo) {
        return MRArchivePhoto.newBuilder()
                .setId(photo.getId())
                .build();
    }

    public static MRDeletePhoto delete(MRPhoto photo) {
        return MRDeletePhoto.newBuilder()
                .setId(photo.getId())
                .build();
    }

    public static class PhotoAggregate extends Aggregate<MRPhotoId, MRPhoto, MRPhoto.Builder> {

        @Assign
        MRPhotoUploaded handle(MRUploadPhoto cmd) {
            var event = MRPhotoUploaded.newBuilder()
                    .setId(cmd.getId())
                    .setFullSizeUrl(cmd.getFullSizeUrl())
                    .setThumbnailUrl(cmd.getThumbnailUrl())
                    .setAltText(cmd.getAltText())
                    .setWidth(cmd.getWidth())
                    .setHeight(cmd.getHeight())
                    .setSourceType(cmd.getSourceType())
                    .build();
            builder().setId(event.getId())
                     .setFullSizeUrl(event.getFullSizeUrl())
                     .setThumbnailUrl(event.getThumbnailUrl())
                     .setAltText(event.getAltText())
                     .setWidth(event.getWidth())
                     .setHeight(event.getHeight())
                     .setSourceType(event.getSourceType());
            return event;
        }

        @Assign
        MRPhotoArchived handle(MRArchivePhoto photo) {
            var event = MRPhotoArchived.newBuilder()
                    .setId(photo.getId())
                    .build();
            setArchived(true);
            return event;
        }

        @Assign
        MRPhotoDeleted handle(MRDeletePhoto photo) {
            var event = MRPhotoDeleted.newBuilder()
                    .setId(photo.getId())
                    .build();
            setDeleted(true);
            return event;
        }
    }

    public static final class InvisibleSound
            extends Aggregate<String, MRSoundRecord, MRSoundRecord.Builder> {

        /**
         * Creates a new repository of these aggregates.
         *
         * <p>The caller registers it with a context, and closes the context after the test.
         */
        // This suppression makes sense only to Error Prone.
        @SuppressWarnings("unchecked")  // as per declaration of the `DefaultRepository`.
        public static AggregateRepository<String, InvisibleSound, MRSoundRecord> newRepository() {
            var repo = DefaultRepository.of(InvisibleSound.class);
            return (AggregateRepository<String, InvisibleSound, MRSoundRecord>) repo;
        }

        @Assign
        MRSoundUploaded handle(MRUploadSound cmd) {
            var event = MRSoundUploaded.newBuilder()
                    .setId(cmd.getId())
                    .build();
            builder().setId(event.getId());
            return event;
        }
    }
}
