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

package com.vmware.admiral.service.common.harbor;

import static com.vmware.admiral.common.ManagementUriParts.CONFIG_PROPS;
import static com.vmware.admiral.common.util.UriUtilsExtended.getReverseProxyLocation;

import java.net.URI;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.function.Function;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * Simple reverse proxy service to forward requests to harbor services.
 */
public class HarborApiProxyService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.HBR_REVERSE_PROXY;

    private volatile URI harborUri;
    private volatile ServiceClient client;
    private String harborUser;
    private String harborPassword;
    private String harborAuthHeader;

    public HarborApiProxyService() {
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void authorizeRequest(Operation op) {
        if (ConfigurationUtil.isEmbedded()) {
            op.complete();
            return;
        }
        super.authorizeRequest(op);
    }

    @Override
    public void handleStart(Operation startOp) {
        startOp.complete();

        getConfigProperty(Harbor.CONFIGURATION_USER_PROPERTY_NAME,
                (state) -> harborUser = state.value);

        getConfigProperty(Harbor.CONFIGURATION_PASS_PROPERTY_NAME,
                (state) -> harborPassword = state.value);

        getConfigProperty(Harbor.CONFIGURATION_URL_PROPERTY_NAME, (state) -> {
            String harborUrl = state.value;
            logInfo("Harbor url is %s", harborUrl);

            if (harborUrl != null && !harborUrl.trim().isEmpty()) {
                ServerX509TrustManager trustManager = ServerX509TrustManager.create(getHost());
                client = ServiceClientFactory.createServiceClient(trustManager, null);

                harborUri = UriUtils.buildUri(harborUrl);
            }
        });
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
        if (harborUri == null) {
            op.fail(new IllegalStateException("Configuration property "
                    + Harbor.CONFIGURATION_URL_PROPERTY_NAME + " not provided"));
            return;
        }

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
                    op.transferResponseHeadersFrom(o);
                    op.getResponseHeaders().put(Operation.CONTENT_TYPE_HEADER, o.getContentType());
                    op.setBodyNoCloning(o.getBodyRaw());
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

        prepareAuthn(forwardOp);

        forwardOp.setReferer(UriUtils.buildUri(getHost().getPublicUri(), getSelfLink()));
        client.send(forwardOp);
    }

    private URI getTargetUri(Operation op) {
        URI uri = op.getUri();
        String opUriPath = uri.getPath();

        int rpIndex = opUriPath.indexOf(SELF_LINK);
        if (rpIndex == -1) {
            // no target URI provided!
            return null;
        }
        String opPath = opUriPath.substring(rpIndex + SELF_LINK.length());

        if (opPath.startsWith(UriUtils.URI_PATH_CHAR)) {
            opPath = opPath.substring(1);
        } else {
            // no target URI provided!
            return null;
        }

        String baseEndpoint = Harbor.API_BASE_ENDPOINT;

        if (opPath.contains(Harbor.I18N_RESOURCE_SUBPATH)) {
            baseEndpoint = "";
        }

        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            query = "?" + query;
        }
        return UriUtils.buildUri(harborUri, baseEndpoint, opPath, query);
    }

    private void getConfigProperty(String propName, Consumer<ConfigurationState> callback) {
        sendRequest(Operation
                .createGet(getHost(), UriUtils.buildUriPath(CONFIG_PROPS, propName))
                .setCompletion((res, ex) -> {
                    if (ex == null && res.hasBody()) {
                        ConfigurationState body = res.getBody(ConfigurationState.class);
                        callback.accept(body);
                    }
                }));
    }

    private void prepareAuthn(Operation op) {
        if (harborAuthHeader != null) {
            op.addRequestHeader("Authorization", "Basic " + harborAuthHeader);
        } else {
            if (harborUser != null && harborPassword != null) {
                String s = String.format("%s:%s", harborUser, harborPassword);
                harborAuthHeader = new String(Base64.getEncoder().encode(s.getBytes()));
                op.addRequestHeader("Authorization", "Basic " + harborAuthHeader);
            }
        }
    }

}
