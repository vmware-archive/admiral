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

package com.vmware.admiral.service.common;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Function;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.ServiceUriPaths;


/**
 * Simple reverse proxy service to forward requests to credential service, when a prefix is used.
 */
public class CredentialsProxyService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.CREDENTIALS_PROXY;

    public CredentialsProxyService() {
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void handleGet(Operation get) {
        forwardRequest(get, Operation::createGet);
    }

    @Override
    public void handlePost(Operation post) {
        forwardRequest(post, Operation::createPost);
    }

    @Override
    public void handlePatch(Operation patch) {
        forwardRequest(patch, Operation::createPatch);
    }

    @Override
    public void handlePut(Operation put) {
        forwardRequest(put, Operation::createPut);
    }

    @Override
    public void handleDelete(Operation delete) {
        forwardRequest(delete, Operation::createDelete);
    }

    @Override
    public void handleOptions(Operation options) {
        forwardRequest(options, Operation::createOptions);
    }

    private void forwardRequest(final Operation op, final Function<URI, Operation> createOp) {
        String targetUri = op.getUri().toString();
        targetUri = targetUri.replace(ManagementUriParts.CREDENTIALS_PROXY,
                                        ServiceUriPaths.CORE_CREDENTIALS);

        URI updatedUri = null;
        try {
            updatedUri = new URI(targetUri);
        } catch (URISyntaxException e) {
            op.fail(e);
            return;
        }

        ReverseProxyService.forwardRequest(updatedUri, op, createOp, this);
    }
}
