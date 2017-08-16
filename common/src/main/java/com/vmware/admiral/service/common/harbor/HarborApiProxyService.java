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
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * Simple reverse proxy service to forward requests to harbor services.
 */
public class HarborApiProxyService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.HBR_REVERSE_PROXY;

    private static final String PROJECT_NAME_CRITERIA_MESSAGE =
            "Project name is allowed to contain alpha-numeric characters, periods, dashes and "
                    + "underscores, lowercase only.";

    private static final String PROJECT_NAME_CRITERIA_CODE = "auth.projects.name.criteria";

    private static final String PROJECT_NAME_LENGTH_MESSAGE =
            "Maximum allowed length for project name is 254 characters.";

    private static final String PROJECT_NAME_LENGTH_CODE = "auth.projects.name.length";

    private volatile URI harborUri;
    private volatile ServiceClient client;
    private String harborUser;
    private String harborPassword;
    private String harborAuthHeader;

    public static class HarborProjectDeleteResponse {

        public Boolean deletable;

        public String message;
    }

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

    public static DeferredResult<String> getHarborUrl(Service service) {
        Operation getConfigProp = Operation.createGet(service, UriUtils.buildUriPath(
                ConfigurationFactoryService.SELF_LINK, Harbor.CONFIGURATION_URL_PROPERTY_NAME));

        DeferredResult<String> result = new DeferredResult<>();

        service.sendWithDeferredResult(getConfigProp, ConfigurationState.class)
                .thenAccept(state -> result.complete(state.value))
                .exceptionally(ex -> {
                    ex = (ex instanceof CompletionException) ? ex.getCause() : ex;
                    if (ex instanceof ServiceNotFoundException) {
                        result.complete(null);
                    } else {
                        result.fail(ex);
                    }
                    return null;
                });

        return result;
    }

    public static DeferredResult<HarborProjectDeleteResponse> validateProjectDelete(Service service,
            String projectIndex) {

        if (projectIndex == null || projectIndex.isEmpty()) {
            return DeferredResult.failed(new IllegalStateException(
                    "Project index is null or empty."));
        }

        DeferredResult<String> harborUrl = getHarborUrl(service);

        return harborUrl.thenCompose(url -> {
            // If there is harbor configured continue the project removal.
            if (url == null || url.isEmpty()) {
                HarborProjectDeleteResponse hbrResp = new HarborProjectDeleteResponse();
                hbrResp.deletable = true;
                return DeferredResult.completed(hbrResp);
            }

            String uri = UriUtils.buildUriPath(HarborApiProxyService.SELF_LINK,
                    buildHarborProjectDeleteVerifyLink(projectIndex));

            Operation verifyProject = Operation.createGet(service, uri);

            return service.sendWithDeferredResult(verifyProject, HarborProjectDeleteResponse.class);
        });
    }

    private static String buildHarborProjectDeleteVerifyLink(String projectIndex) {
        return UriUtils.buildUriPath(Harbor.ENDPOINT_PROJECTS, projectIndex,
                Harbor.PROJECTS_DELETE_VERIFICATION_SUFFIX);
    }

    public static void validateProjectName(String name) {
        AssertUtil.assertNotNullOrEmpty(name, "name");

        if (!Harbor.PROJECT_NAME_PATTERN.matcher(name).matches()) {
            throw new LocalizableValidationException(PROJECT_NAME_CRITERIA_MESSAGE,
                    PROJECT_NAME_CRITERIA_CODE);
        }

        if (name.length() > 254) {
            throw new LocalizableValidationException(PROJECT_NAME_LENGTH_MESSAGE,
                    PROJECT_NAME_LENGTH_CODE);
        }
    }

}
