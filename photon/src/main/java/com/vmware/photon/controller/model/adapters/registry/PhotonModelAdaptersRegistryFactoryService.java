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

package com.vmware.photon.controller.model.adapters.registry;

import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;

/**
 * The purpose of this custom FactoryService is to enforce uniqueness of
 * {@link PhotonModelAdapterConfig} documents. I.e {@link PhotonModelAdapterConfig} objects with the
 * same {@link PhotonModelAdapterConfig#id} are considered the same.
 */
public class PhotonModelAdaptersRegistryFactoryService extends FactoryService {

    public PhotonModelAdaptersRegistryFactoryService() {
        super(PhotonModelAdapterConfig.class);

        this.setUseBodyForSelfLink(true);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new PhotonModelAdaptersRegistryService();
    }

    @Override
    public void handlePost(Operation post) {
        post.addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE);
        super.handlePost(post);
    }

    /**
     * Override the buildDefaultChildSelfLink method to set the documentSelfLink.
     */
    @Override
    protected String buildDefaultChildSelfLink(ServiceDocument document) {
        PhotonModelAdapterConfig initState = (PhotonModelAdapterConfig) document;
        if (initState.id != null) {
            return initState.id;
        }
        if (initState.documentSelfLink != null) {
            return initState.documentSelfLink;
        }
        return super.buildDefaultChildSelfLink();
    }

}
