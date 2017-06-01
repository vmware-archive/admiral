/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.compute;

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;

import java.net.URI;
import java.util.List;

import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class ResourceGroupUtils {

    public static DeferredResult<ResourceGroupState> createResourceGroup(ServiceHost host,
            URI referer, String contextId, List<String> tenantLinks) {

        ResourceGroupState resourceGroup = new ResourceGroupState();
        resourceGroup.name = contextId;
        resourceGroup.documentSelfLink = generateSelfLink(resourceGroup);
        resourceGroup.tenantLinks = tenantLinks;

        return host.sendWithDeferredResult(
                Operation.createPost(host, ResourceGroupService.FACTORY_LINK)
                        .setReferer(referer)
                        .setBody(resourceGroup))
                .thenApply(op -> op.getBody(ResourceGroupState.class));
    }

    private static String generateSelfLink(ResourceGroupState resourceGroup) {
        String contextId = resourceGroup.name;
        assertNotNull(contextId, "contextId should not be null");

        return UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK, contextId);
    }
}
