/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common;

import static com.vmware.admiral.common.util.UriUtilsExtended.getReverseProxyLocation;
import static com.vmware.admiral.common.util.UriUtilsExtended.getReverseProxyTargetUri;

import java.net.URI;
import java.util.function.Function;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

/**
 * Simple reverse proxy service to forward requests to 3rd party services.
 */
public class ReverseProxyService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.REVERSE_PROXY;

    public ReverseProxyService() {
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

        URI targetUri = getTargetUri(op);
        if (targetUri == null) {
            op.fail(new IllegalArgumentException("Invalid target URI provided!"));
            return;
        }

        Operation forwardOp = createOp.apply(targetUri)
                .transferRequestHeadersFrom(op)
                .setContentType(op.getContentType())
                .setBody(op.getBodyRaw())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        op.fail(e);
                        return;
                    }
                    op.transferResponseHeadersFrom(o);
                    op.getResponseHeaders().put(Operation.CONTENT_TYPE_HEADER, o.getContentType());
                    op.setBody(o.getBodyRaw());
                    op.setStatusCode(o.getStatusCode());

                    // handle HTTP 301/302 responses to redirect through the reverse proxy also
                    if (o.getStatusCode() == Operation.STATUS_CODE_MOVED_PERM ||
                            o.getStatusCode() == Operation.STATUS_CODE_MOVED_TEMP) {
                        String location = o.getResponseHeader(Operation.LOCATION_HEADER);
                        String rpLocation = getReverseProxyLocation(location, o.getUri());
                        op.getResponseHeaders().put(Operation.LOCATION_HEADER, rpLocation);
                    }

                    op.complete();
                });

        sendRequest(forwardOp);
    }

    private URI getTargetUri(final Operation op) {
        // try to get it directly from the request URI
        // the request URI should look like ../rp/{http://target-host/target-path}
        URI uri = op.getUri();
        URI targetUri = getReverseProxyTargetUri(uri);
        if (targetUri == null) {
            // otherwise try to get it in combination with the referer URI
            // the request URI should look like ../rp/target-path (relative to previous request)
            // and then the referer URI should look like ../rp/{http://target-host}
            URI referer = op.getReferer();
            if (referer == null) {
                // impossible to get a valid target URI!
                return null;
            }
            String path = uri.getPath().replaceFirst(ReverseProxyService.SELF_LINK, "");
            uri = UriUtilsExtended.buildUri(referer, referer.getPath(), path);
            targetUri = getReverseProxyTargetUri(uri);
        }
        return targetUri;
    }

}
