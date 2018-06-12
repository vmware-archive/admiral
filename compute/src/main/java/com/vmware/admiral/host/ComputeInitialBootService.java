/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host;

import java.util.ArrayList;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.HostContainerListDataCollection;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection;
import com.vmware.admiral.compute.container.HostVolumeListDataCollection;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.admiral.compute.kubernetes.KubernetesEntityDataCollection;
import com.vmware.admiral.image.service.FavoriteImagePopulateFlagService;
import com.vmware.admiral.image.service.FavoriteImagePopulateFlagService.FavoriteImagePopulateFlag;
import com.vmware.admiral.image.service.FavoriteImagesService;
import com.vmware.admiral.image.service.FavoriteImagesService.FavoriteImage;
import com.vmware.admiral.service.common.AbstractInitialBootService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;

/**
 * Initial boot service for creating system default documents for the common module.
 */
public class ComputeInitialBootService extends AbstractInitialBootService {
    public static final String SELF_LINK = ManagementUriParts.CONFIG + "/compute-initial-boot";

    @Override
    public void handlePost(Operation post) {
        initInstances(Operation.createGet(null), false, false,
                SystemContainerDescriptions.buildCoreAgentContainerDescription());

        ArrayList<ServiceDocument> states = new ArrayList<>();
        ArrayList<FavoriteImage> defaultFavoriteImageStates = new ArrayList<>();

        /**
         * Operation to retrieve the flag which tells whether or not to add the default
         * favorite images. In case the images should not be added, initInstances is called
         * to complete the deletion of the ComputeInitialBootService.
         */
        Operation populateDefaultFavoriteImages = Operation
                .createGet(UriUtils.buildUri(getHost(),
                        FavoriteImagePopulateFlagService.FAVORITE_IMAGE_POPULATE_FLAG_LINK))
                .setReferer(getSelfLink())
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logInfo("Could not retrieve shouldPopulate images flag.");
                    } else {
                        FavoriteImagePopulateFlag flag = o.hasBody() ? o.getBody(FavoriteImagePopulateFlag.class) : null;
                        if (flag != null && flag.shouldPopulate) {
                            //Add the default favorite image states to the list.
                            defaultFavoriteImageStates.addAll(FavoriteImagesService.buildDefaultFavoriteImages(getHost()));
                            flag.shouldPopulate = Boolean.FALSE;
                            sendRequest(updateShouldPopulateFlagState(flag));
                        }
                    }
                    initInstances(post, defaultFavoriteImageStates.toArray(
                            new ServiceDocument[defaultFavoriteImageStates.size()]));
                });

        states.add(ContainerHostDataCollectionService.buildDefaultStateInstance());
        states.add(KubernetesEntityDataCollection.buildDefaultStateInstance());
        states.add(HostContainerListDataCollection.buildDefaultStateInstance());
        states.add(HostNetworkListDataCollection.buildDefaultStateInstance());
        states.add(HostVolumeListDataCollection.buildDefaultStateInstance());
        states.add(FavoriteImagePopulateFlagService.buildDefaultStateInstance());

        if (DeploymentProfileConfig.getInstance().isTest()) {
            states.add(GroupResourcePlacementService.buildDefaultResourcePool());
            if (!ConfigurationUtil.isEmbedded()) {
                states.add(GroupResourcePlacementService.buildDefaultStateInstance());
            }
        }

        /**
         * Initialize default instances without deleting the initial boot service.
         */
        initInstances(post, true, false, states.toArray(new ServiceDocument[states.size()]));
        /**
         * Check whether adding the default favorite images is needed and do so if yes.
         */
        sendRequest(populateDefaultFavoriteImages);
    }

    /**
     * Updates the state of the shouldPopulate flag.
     *
     * @param flagState The new state of the flag.
     * @return          The update operation to be invoked.
     */
    private Operation updateShouldPopulateFlagState(FavoriteImagePopulateFlag flagState) {
        return Operation.createPut(UriUtils.buildUri(getHost(),flagState.documentSelfLink))
                .setReferer(getSelfLink())
                .setBody(flagState);
    }

}
