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
import static com.vmware.admiral.common.util.AssertUtil.assertTrue;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

public class ResourceGroupUtils {

    public static final String COMPUTE_DEPLOYMENT_TYPE_VALUE = "compute_deployment";

    public static DeferredResult<ResourceGroupState> createResourceGroup(ServiceHost host,
            URI referer, String contextId, List<String> tenantLinks) {

        ResourceGroupState resourceGroup = new ResourceGroupState();
        resourceGroup.name = contextId;
        resourceGroup.documentSelfLink = generateSelfLink(resourceGroup);
        resourceGroup.tenantLinks = tenantLinks;
        resourceGroup.customProperties = new HashMap<>();
        resourceGroup.customProperties.put(ComputeProperties.RESOURCE_TYPE_KEY,
                COMPUTE_DEPLOYMENT_TYPE_VALUE);

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

    public static DeferredResult<ResourceGroupState> updateDeploymentResourceGroup(ServiceHost host,
            URI referer, ResourceGroupState state, Set<String> groupLinks,
            List<String> tenantLinks) {
        assertNotNull(state, "state should not be null");
        assertNotNull(groupLinks, "groupLinks should not be null");
        assertTrue(groupLinks.size() > 0, "There should be at least one groupLinks");

        QueryTask.Query.Builder builder = QueryTask.Query.Builder.create()
                .addKindFieldClause(ResourceGroupState.class)
                .addInClause(ResourceGroupState.FIELD_NAME_SELF_LINK, groupLinks)
                .addCompositeFieldClause(ResourceGroupState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeProperties.RESOURCE_TYPE_KEY, COMPUTE_DEPLOYMENT_TYPE_VALUE);
        QueryUtils.QueryByPages<ResourceGroupState> query = new QueryUtils.QueryByPages<>(
                host,
                builder.build(), ResourceGroupState.class, tenantLinks);

        return query.collectLinks(Collectors.toList())
                .thenApply(resourceGroupLinks -> {
                    assertNotNull(resourceGroupLinks, "No resource groups found at [" +
                            groupLinks + "]");
                    assertTrue(resourceGroupLinks.size() == 1, "There should only be "
                            + "one resource group at [" + groupLinks + "]");

                    return resourceGroupLinks.get(0);
                })
                .thenCompose(resourceGroupLink -> host.sendWithDeferredResult(
                        Operation.createPatch(host, resourceGroupLink)
                                .setReferer(referer)
                                .setBody(state))
                .thenApply(op -> op.getBody(ResourceGroupState.class)));
    }
}
