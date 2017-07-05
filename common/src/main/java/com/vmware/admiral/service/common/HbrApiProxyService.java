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

package com.vmware.admiral.service.common;

import static com.vmware.admiral.common.util.UriUtilsExtended.getReverseProxyLocation;

import java.net.URI;
import java.util.Base64;
import java.util.function.Function;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.SslTrustImportService.SslTrustImportRequest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * Simple reverse proxy service to forward requests to harbor services.
 */
public class HbrApiProxyService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.HBR_REVERSE_PROXY;

    public static final String HARBOR_ENDPOINT_REPOSITORIES = "/repositories";

    public static final String HARBOR_QUERY_PARAM_PROJECT_ID = "project_id";
    public static final String HARBOR_QUERY_PARAM_DETAIL = "detail";

    public static final String HARBOR_RESP_PROP_ID = "id";
    public static final String HARBOR_RESP_PROP_NAME = "name";
    public static final String HARBOR_RESP_PROP_TAGS_COUNT = "tags_count";

    private static final String HBR_URL_PROP = "harbor.tab.url";
    private static final String HBR_API_BASE_ENDPOINT = "api";
    private static final String I18N_RESOURCE_SUBPATH = "i18n/lang";

    private volatile URI harborUri;
    private volatile ServiceClient client;

    public HbrApiProxyService() {
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

        sendRequest(Operation
                .createGet(getHost(), UriUtils.buildUriPath(ManagementUriParts.CONFIG_PROPS,
                        HBR_URL_PROP))
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((res, ex) -> {
                    if (ex == null && res.hasBody()) {
                        ConfigurationState body = res.getBody(ConfigurationState.class);
                        String harborUrl = body.value;

                        if (harborUrl != null && !harborUrl.trim().isEmpty()) {
                            ServerX509TrustManager trustManager = ServerX509TrustManager
                                    .create(getHost());
                            client = ServiceClientFactory.createServiceClient(trustManager, null);

                            harborUri = UriUtils.buildUri(harborUrl);

                            if (harborUri != null
                                    && UriUtils.HTTPS_SCHEME.equals(harborUri.getScheme())) {
                                logFine("Importing ssl trust for harbor uri %s", harborUri);
                                SslTrustImportRequest sslTrustRequest = new SslTrustImportRequest();
                                sslTrustRequest.hostUri = harborUri;
                                sslTrustRequest.acceptCertificate = true;

                                sendRequest(
                                        Operation
                                                .createPut(getHost(),
                                                        SslTrustImportService.SELF_LINK)
                                                .setBody(sslTrustRequest)
                                                .setCompletion((o, e) -> {
                                                    if (e != null) {
                                                        logSevere(
                                                                "There was a problem importing SSL trust for harbor URI %s, Error: %s",
                                                                harborUri, e);
                                                    }
                                                }));
                            }

                        }
                    }
                }));
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
            op.fail(new IllegalStateException(
                    "Configuration property " + HBR_URL_PROP + " not provided"));
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

        String baseEndpoint = HBR_API_BASE_ENDPOINT;

        if (opPath.contains(I18N_RESOURCE_SUBPATH)) {
            baseEndpoint = "";
        }

        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            query = "?" + query;
        }
        return UriUtils.buildUri(harborUri, baseEndpoint, opPath, query);
    }

    private void prepareAuthn(Operation op) {
        // TODO: replace with actual token
        String encoding = new String(Base64.getEncoder().encode("admin:Harbor12345".getBytes()));
        op.addRequestHeader("Authorization", "Basic " + encoding);
    }
}
