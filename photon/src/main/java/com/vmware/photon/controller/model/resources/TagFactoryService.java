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

package com.vmware.photon.controller.model.resources;

import java.util.ArrayList;
import java.util.UUID;

import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;

/**
 * The purpose of this custom FactoryService is to enforce uniqueness of TagState documents. I.e
 * TagState objects with the same field values are considered the same.
 */
public class TagFactoryService extends FactoryService {

    public TagFactoryService() {
        super(TagState.class);

        this.setUseBodyForSelfLink(true);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new TagService();
    }

    /**
     * Override the buildDefaultChildSelfLink method to set the documentSelfLink. We don't want to
     * have multiple tags with the same values, so we build the documentSelfLink ourselves taking
     * into account all fields in the TagState
     *
     * @see #generateSelfLink(TagState)
     */
    @Override
    protected String buildDefaultChildSelfLink(ServiceDocument document) {
        TagState initState = (TagState) document;
        if (initState.key != null && initState.value != null) {
            return generateId(initState);
        }
        if (initState.documentSelfLink != null) {
            return initState.documentSelfLink;
        }
        return super.buildDefaultChildSelfLink();
    }

    public static String generateSelfLink(TagState tagState) {
        String id = generateId(tagState);
        return UriUtils.buildUriPath(TagService.FACTORY_LINK, id);
    }

    private static String generateId(TagState tagState) {
        ArrayList<String> values = new ArrayList<>();
        values.add(tagState.key);
        values.add(tagState.value);
        if (tagState.tenantLinks != null) {
            values.addAll(tagState.tenantLinks);
        }
        return UUID.nameUUIDFromBytes(values.toString().getBytes()).toString();
    }
}
