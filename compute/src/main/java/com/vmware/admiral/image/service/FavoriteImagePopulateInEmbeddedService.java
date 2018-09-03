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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.image.service.FavoriteImagePopulateFlagService.FavoriteImagePopulateFlag;
import com.vmware.admiral.image.service.FavoriteImagesService.FavoriteImage;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

public class FavoriteImagePopulateInEmbeddedService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.FAVORITE_IMAGES_POPULATE_EMBEDDED;

    public FavoriteImagePopulateInEmbeddedService() {
        super();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handlePost(Operation post) {
        logInfo("Population of favorite images for specific tenants triggered.");
        List<String> tenantLinks = post.getBody(List.class);
        List<FavoriteImage> globalFavorites = new LinkedList<>();

        QueryTask queryTask = QueryUtil.buildQuery(FavoriteImage.class, true,
                QueryUtil.addTenantClause(null));
        QueryUtil.addExpandOption(queryTask);

        new ServiceDocumentQuery<>(getHost(), FavoriteImage.class)
                .query(queryTask, r -> {
                    if (r.hasException()) {
                        post.fail(r.getException());
                    } else if (r.hasResult()) {
                        globalFavorites.add(r.getResult());
                    } else {
                        createFavoriteImagesBasedOnFlagStatus(post, tenantLinks, globalFavorites);
                    }
                });
    }

    /**
     * Retrieve shouldPopulateInEmbedded flag. If flag is set to true, update its state to false
     * and replicate all global favorite image states for each tenant.
     *
     * @param post The operation which triggered the event.
     * @param tenantLinks The tenant links for which states should be created.
     * @param globalFavorites The global images which should be replicated for each tenantLink.
     */
    private void createFavoriteImagesBasedOnFlagStatus(Operation post, List<String> tenantLinks,
                                                       List<FavoriteImage> globalFavorites) {
        sendRequest(Operation
                .createGet(UriUtils.buildUri(getHost(),
                        FavoriteImagePopulateFlagService.FAVORITE_IMAGE_POPULATE_FLAG_LINK))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        post.fail(e);
                    }
                    FavoriteImagePopulateFlag flag = o.getBody(FavoriteImagePopulateFlag.class);
                    if (flag.shouldPopulateInEmbedded) {
                        disableShouldPopulateInEmbeddedFlag(flag);
                        createFavoriteImagesForTenants(tenantLinks, globalFavorites);
                    } else {
                        logInfo("Favorite images for specific tenants has already been run. " +
                                "Not populating tenant specific favorite images");
                    }
                    post.complete();
                }));
    }

    /**
     * Change state of shouldPopulateInEmbedded flag to FALSE.
     *
     * @param flag State of the flag.
     */
    private void disableShouldPopulateInEmbeddedFlag(FavoriteImagePopulateFlag flag) {
        flag.shouldPopulateInEmbedded = Boolean.FALSE;
        sendRequest(Operation
                .createPut(UriUtils.buildUri(getHost(), flag.documentSelfLink))
                .setBody(flag));
    }

    /**
     * Replicate the state of each global favorite image for each tenant.
     *
     * @param tenantLinks The tenant links for which states should be created.
     * @param globalFavorites The global images which should be replicated for each tenantLink.
     */
    private void createFavoriteImagesForTenants(List<String> tenantLinks, List<FavoriteImage> globalFavorites) {
        tenantLinks.forEach(tenantLink -> {
            logInfo(String.format("Population of favorite images for %s - started", tenantLink));
            globalFavorites.forEach(image -> {
                List<String> tenants = new ArrayList<>();
                tenants.add(tenantLink);
                FavoriteImage tenantedImage = new FavoriteImage();
                tenantedImage.name = image.name;
                tenantedImage.description = image.description;
                tenantedImage.registry = image.registry;
                tenantedImage.tenantLinks = tenants;

                logInfo(String.format("Creating default favorite image state %s", tenantedImage.name));
                Operation createImageOperation = Operation
                        .createPost(UriUtils.buildUri(getHost(),FavoriteImageFactoryService.SELF_LINK))
                        .setBody(tenantedImage)
                        .addRequestHeader(OperationUtil.PROJECT_ADMIRAL_HEADER, tenantLink);

                sendRequest(createImageOperation);
            });
        });
    }

}
