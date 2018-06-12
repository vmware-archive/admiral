/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.image.service;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;

public class FavoriteImagePopulateFlagService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.FAVORITE_IMAGES_FLAG;
    public static final String FAVORITE_IMAGE_POPULATE_FLAG_ID = "flag";
    public static final String FAVORITE_IMAGE_POPULATE_FLAG_LINK = UriUtils
            .buildUriPath(FACTORY_LINK, FAVORITE_IMAGE_POPULATE_FLAG_ID);

    public FavoriteImagePopulateFlagService() {
        super(FavoriteImagePopulateFlag.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    public static FavoriteImagePopulateFlag buildDefaultStateInstance() {
        FavoriteImagePopulateFlag state = new FavoriteImagePopulateFlag();
        state.documentSelfLink = FAVORITE_IMAGE_POPULATE_FLAG_LINK;
        state.shouldPopulate = Boolean.TRUE;
        state.shouldPopulateInEmbedded = Boolean.TRUE;
        return  state;
    }

    public static class FavoriteImagePopulateFlag extends ServiceDocument {
        public Boolean shouldPopulate;
        public Boolean shouldPopulateInEmbedded;
    }

    @Override
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            post.fail(new IllegalArgumentException("body is required"));
            return;
        }

        FavoriteImagePopulateFlag initState = post.getBody(FavoriteImagePopulateFlag.class);
        if (initState.documentSelfLink == null
                || !initState.documentSelfLink
                .endsWith(FAVORITE_IMAGE_POPULATE_FLAG_ID)) {
            post.fail(new LocalizableValidationException(
                    "Only one instance of favorite image populate shouldPopulate can be started",
                    "compute.should-populte-flag.single"));
            return;
        }

        post.setBody(initState).complete();
    }
}
