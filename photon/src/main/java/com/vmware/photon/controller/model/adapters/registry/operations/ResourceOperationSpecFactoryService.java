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

package com.vmware.photon.controller.model.adapters.registry.operations;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;

/**
 * The purpose of this custom FactoryService is to enforce uniqueness of {@link
 * ResourceOperationSpec} documents. I.e {@link ResourceOperationSpec} objects with the same
 * {@link ResourceOperationSpec#endpointType} and {@link ResourceOperationSpec#operation} are
 * considered the same.
 */
public class ResourceOperationSpecFactoryService extends FactoryService {

    public static final String TOKEN_SEPARATOR = "-";
    private static final String TOKEN_SEPARATOR_REPLACEMENT = "_";

    public ResourceOperationSpecFactoryService() {
        super(ResourceOperationSpec.class);

        this.setUseBodyForSelfLink(true);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new ResourceOperationSpecService();
    }

    @Override
    public void handlePost(Operation post) {
        post.addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);
        super.handlePost(post);
    }

    /**
     * Override the buildDefaultChildSelfLink method to set the documentSelfLink. document
     * identifier is combination of operation and endpoint type
     * @see #generateSelfLink(ResourceOperationSpec)
     */
    @Override
    protected String buildDefaultChildSelfLink(ServiceDocument document) {
        ResourceOperationSpec initState = (ResourceOperationSpec) document;
        if (initState.operation != null
                && initState.resourceType != null
                && initState.endpointType != null) {
            return generateId(initState);
        }
        if (initState.documentSelfLink != null) {
            return initState.documentSelfLink;
        }
        return super.buildDefaultChildSelfLink();
    }

    public static String generateSelfLink(ResourceOperationSpec state) {
        return generateSelfLink(state.endpointType, state.resourceType, state.operation);
    }

    public static String generateSelfLink(String endpointType,
            ResourceType resourceType,
            String operation) {
        String id = generateId(endpointType, resourceType, operation);
        return UriUtils.buildUriPath(ResourceOperationSpecService.FACTORY_LINK, id);
    }

    private static String generateId(String endpointType, ResourceType resourceType,
            String operation) {
        return endpointType.replace(TOKEN_SEPARATOR, TOKEN_SEPARATOR_REPLACEMENT)
                + TOKEN_SEPARATOR + resourceType.name().toLowerCase()
                + TOKEN_SEPARATOR + operation;
    }

    private static String generateId(ResourceOperationSpec state) {
        return generateId(state.endpointType, state.resourceType, state.operation);
    }
}
