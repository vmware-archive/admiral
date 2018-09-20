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
import java.util.logging.Level;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatelessService;

/**
 * Simple reverse proxy service to forward requests to 3rd party services.
 */
public class ReverseProxyService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.REVERSE_PROXY;

    protected volatile Boolean isEmbedded;
    protected volatile Boolean isVic;
    protected volatile Boolean allowSshConsole;

    public ReverseProxyService() {
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void handleGet(Operation get) {
        checkIfEnabledAndForwardRequest(get, Operation::createGet);
    }

    @Override
    public void handlePost(Operation post) {
        checkIfEnabledAndForwardRequest(post, Operation::createPost);
    }

    @Override
    public void handlePatch(Operation patch) {
        checkIfEnabledAndForwardRequest(patch, Operation::createPatch);
    }

    @Override
    public void handlePut(Operation put) {
        checkIfEnabledAndForwardRequest(put, Operation::createPut);
    }

    @Override
    public void handleDelete(Operation delete) {
        checkIfEnabledAndForwardRequest(delete, Operation::createDelete);
    }

    @Override
    public void handleOptions(Operation options) {
        checkIfEnabledAndForwardRequest(options, Operation::createOptions);
    }

    private void checkIfEnabledAndForwardRequest(final Operation op,
                                                    final Function<URI, Operation> createOp) {

        if (isEmbedded == null) {
            ConfigurationUtil.getConfigProperty(this, ConfigurationUtil.EMBEDDED_MODE_PROPERTY,
                    (embedded) -> {
                        isEmbedded = Boolean.valueOf(embedded);
                        checkIfEnabledAndForwardRequest(op, createOp);
                    });
            return;
        }

        if (isVic == null) {
            ConfigurationUtil.getConfigProperty(this, ConfigurationUtil.VIC_MODE_PROPERTY,
                    (vic) -> {
                        isVic = Boolean.valueOf(vic);
                        checkIfEnabledAndForwardRequest(op, createOp);
                    });
            return;
        }

        if (allowSshConsole == null) {
            ConfigurationUtil.getConfigProperty(this, ConfigurationUtil.ALLOW_SSH_CONSOLE_PROPERTY,
                    (sshConsole) -> {
                        allowSshConsole = Boolean.valueOf(sshConsole);
                        checkIfEnabledAndForwardRequest(op, createOp);
                    });
            return;
        }

        if (isEmbedded || isVic || !allowSshConsole) {
            logInfo("Reverse proxy access temporarily disabled!");
            op.fail(Operation.STATUS_CODE_FORBIDDEN);
            return;
        }

        URI targetUri = getTargetUri(op);

        forwardRequest(targetUri, op, createOp, this);
    }

    public static void forwardRequest(URI targetUri, final Operation op,
                                        final Function<URI, Operation> createOp, Service sender) {
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
                        String newLocation = getReverseProxyLocation(location, o.getUri(),
                                op.getUri());
                        op.getResponseHeaders().put(Operation.LOCATION_HEADER, newLocation);
                    }

                    op.complete();
                });

        sender.sendRequest(forwardOp);
    }

    private URI getTargetUri(final Operation op) {
        // try to get it directly from the request URI
        // the request URI should look like ../rp/{http://target-host/target-path}
        URI uri = op.getUri();
        URI targetUri;
        try {
            targetUri = getReverseProxyTargetUri(uri);
        } catch (Exception e) {
            getHost().log(Level.WARNING, "Exception getting target uri: %s", e.getMessage());
            targetUri = null;
        }
        if (targetUri == null) {
            getHost().log(Level.WARNING, "Unable to get target uri!");
            return null;
        }
        return targetUri;
    }
}
