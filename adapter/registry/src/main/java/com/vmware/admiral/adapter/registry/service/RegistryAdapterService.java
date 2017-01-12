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

package com.vmware.admiral.adapter.registry.service;

import static java.net.HttpURLConnection.HTTP_OK;

import static com.vmware.admiral.service.common.RegistryService.API_VERSION_PROP_NAME;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.docker.util.DockerImage;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse.Result;
import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.security.EncryptionUtils;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.service.common.RegistryService.ApiVersion;
import com.vmware.admiral.service.common.RegistryService.RegistryAuthState;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Service for fulfilling image tasks backed by a registry server
 */
public class RegistryAdapterService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_REGISTRY;
    public static final String DEFAULT_API_VERSION = System.getProperty(
            "com.vmware.admiral.adapter.registry.service.RegistryAdapterService.default.api.version",
            ApiVersion.V1.toString());
    public static final String SEARCH_QUERY_PROP_NAME = "q";
    public static final String LIMIT_PROP_NAME = "limit";

    public static final String SSL_TRUST_CERT_PROP_NAME = "sslTrustCertificate";
    public static final String SSL_TRUST_ALIAS_PROP_NAME = "sslTrustAlias";

    private static final String LINK_HEADER = "Link";
    private static final Pattern URL_LINK_PATTERN = Pattern.compile("<(.*?)>");
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String WWW_AUTHENTICATE_HEADER = "www-authenticate";
    private static final Pattern KV_PATTERN = Pattern.compile("[^=,]+=\"[^\"]+\"");
    private static final String BEARER_TOKEN_PREFIX = "Bearer";
    private static final String BEARER_REALM_WWW_AUTH_PROP = "Bearer realm";
    private static final String SERVICE_WWW_AUTH_PROP = "service";
    private static final String SCOPE_WWW_AUTH_PROP = "scope";

    private static final String DEFAULT_NAMESPACE = "library";
    private static final String V1_PING_ENDPOINT = "/v1/_ping";

    // Use catalog endpoint instead of the API Version Check endpoint (/v2) because some solutions
    // like JFrog Artifactory (cse-artifactory.eng.vmware.com) does not support it.
    private static final String V2_PING_ENDPOINT = "/v2/_catalog?n=1";

    private ServiceClient serviceClient;
    private ServerX509TrustManager trustManager;

    public class RegistryPingResponse {
        public ApiVersion apiVersion;
    }

    private static class V2RegistryCatalogResponse {
        String[] repositories;
    }

    private static class TokenServiceResponse {
        @SuppressWarnings("unused")
        String expires_in;
        @SuppressWarnings("unused")
        String issued_at;
        String token;
    }

    private static class V2ImageTagsResponse {
        @SuppressWarnings("unused")
        String name;
        String[] tags;
    }

    @Override
    public void handleStart(Operation post) {
        this.trustManager = ServerX509TrustManager.create(getHost());
        this.serviceClient = ServiceClientFactory.createServiceClient(
                trustManager, null);

        super.handleStart(post);
    }

    @Override
    public void handleDelete(Operation delete) {
        if (this.serviceClient != null) {
            this.serviceClient.stop();
        }
        super.handleDelete(delete);
    }

    private static class RequestContext {
        public Operation operation;
        public ImageRequest request;
        public RegistryAuthState registryState;
    }

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() != Action.PATCH) {
            getHost().failRequestActionNotSupported(op);
            return;
        }

        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        handlePatch(op);
    }

    @Override
    public void handlePatch(Operation op) {
        RequestContext context = new RequestContext();
        context.operation = op;

        context.request = op.getBody(ImageRequest.class);
        context.request.validate();// validate the request
        logInfo("Processing operation request for resource %s", context.request.resourceReference);

        processRequest(context);
    }

    private void processRequest(RequestContext context) {

        switch (context.request.getOperationType()) {
        case SEARCH:
            fetchRegistry(context, () -> processSearchRequest(context));
            break;

        case PING:
            fetchAuthCredentials(context, () -> processPingRequest(context));
            break;

        case LIST_TAGS:
            fetchRegistry(context, () -> processListImageTagsRequest(context));
            break;

        default:
            context.operation.fail(new IllegalArgumentException(
                    "Unexpected request type: " + context.request.getOperationType()));

            break;
        }

    }

    private void fetchRegistry(RequestContext context, Runnable callback) {
        URI registryStateUri =  UriUtils.extendUriWithQuery(context.request.resourceReference,
                UriUtils.URI_PARAM_ODATA_EXPAND, Boolean.TRUE.toString());

        Operation getRegistry = Operation.createGet(registryStateUri)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        context.operation.fail(ex);
                        return;
                    }

                    context.registryState = o.getBody(RegistryAuthState.class);

                    processAuthentication(context, context.registryState.authCredentials);

                    callback.run();
                });

        sendRequest(getRegistry);
    }

    private void fetchAuthCredentials(RequestContext context, Runnable callback) {
        String authCredentialsLink = context.request.customProperties.get(
                RegistryState.FIELD_NAME_AUTH_CREDENTIALS_LINK);

        if (authCredentialsLink == null) {
            callback.run();
            return;
        }

        sendRequest(Operation.createGet(this, authCredentialsLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        context.operation.fail(e);
                        return;
                    }

                    AuthCredentialsServiceState authCredentialsState =
                            o.getBody(AuthCredentialsServiceState.class);
                    processAuthentication(context, authCredentialsState);

                    callback.run();
                }));
    }

    private void processAuthentication(RequestContext context,
            AuthCredentialsServiceState authState) {
        if (authState != null) {
            String authorizationHeaderValue = createAuthorizationHeader(authState);
            if (authorizationHeaderValue != null) {
                context.request.customProperties.put(AUTHORIZATION_HEADER,
                        authorizationHeaderValue);
            }
        }
    }

    private String createAuthorizationHeader(AuthCredentialsServiceState authState) {
        AuthCredentialsType authCredentialsType = AuthCredentialsType.valueOf(authState.type);
        if (AuthCredentialsType.Password.equals(authCredentialsType)) {
            String username = authState.userEmail;
            String password = EncryptionUtils.decrypt(authState.privateKey);

            String code = new String(Base64.getEncoder().encode(
                    new StringBuffer(username).append(":").append(password).toString().getBytes()));
            String headerValue = new StringBuffer("Basic ").append(code).toString();

            return headerValue;
        }

        return null;
    }

    private void processSearchRequest(RequestContext context) {
        String apiVersion = getApiVersion(context.registryState);
        if (ApiVersion.V1.toString().equals(apiVersion)) {
            processV1SearchRequest(context);
        } else if (ApiVersion.V2.toString().equals(apiVersion)) {
            processV2SearchRequest(context);
        } else {
            String errorMsg = String.format("Unsupported registry version '%s'.", apiVersion);
            context.operation.fail(new LocalizableValidationException(errorMsg, "adapter.unsupported.registry.version", apiVersion));
        }
    }

    private void processV1SearchRequest(RequestContext context) {
        try {
            URI searchUri = URI.create(context.registryState.address);
            searchUri = UriUtils.extendUri(searchUri, "/v1/search");
            searchUri = UriUtils.extendUriWithQuery(searchUri, SEARCH_QUERY_PROP_NAME,
                    context.request.customProperties.get(SEARCH_QUERY_PROP_NAME));

            logInfo("Performing registry search: %s", searchUri);
            Operation search = Operation.createGet(searchUri)
                    .setReferer(getHost().getUri())
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            context.operation.fail(ex);
                            return;
                        } else {
                            RegistrySearchResponse body = o.getBody(RegistrySearchResponse.class);

                            // set the source registry in the results
                            if (body != null && body.results != null) {
                                for (Result result : body.results) {
                                    result.registry = context.registryState.address;
                                    ensureNamespaceExists(result);
                                }
                            }

                            context.operation.setBody(body);
                            context.operation.complete();
                        }
                    });

            String authorization = context.request.customProperties.get(AUTHORIZATION_HEADER);
            if (authorization != null) {
                search.addRequestHeader(AUTHORIZATION_HEADER, authorization);
            }

            this.serviceClient.send(search);

        } catch (Exception x) {
            context.operation.fail(x);
        }
    }

    /**
     * Ensures that an image name has a namespace prefix.
     *
     * An image pushed to a V1 registry without namespace prefix is processed in the
     * following way: <registry_name>/<repository_name> -> <registry_name>/library/<repository_name>
     * Results from V1 queries, however, do not contain the default prefix. This could be a problem
     * later when we instantiate containers based on these images. An image pushed to a V2 registry
     * does not get the default prefix.
     */
    private void ensureNamespaceExists(Result result) {
        DockerImage image = DockerImage.fromImageName(result.name);
        if (image.getNamespace() == null) {
            result.name = String.format("%s/%s", DEFAULT_NAMESPACE, image.getRepository());
        }
    }

    private void processV2SearchRequest(RequestContext context) {
        try {
            URI searchUri = URI.create(context.registryState.address);

            searchUri = UriUtils.extendUri(searchUri, "/v2/_catalog");

            String searchTerm = context.request.customProperties.get(SEARCH_QUERY_PROP_NAME)
                    .toLowerCase();

            RegistrySearchResponse response = new RegistrySearchResponse();
            List<Result> results = new ArrayList<>();
            response.results = results;

            logInfo("Performing registry search: %s", searchUri);
            sendV2SearchRequest(searchUri, searchTerm, response, context);

        } catch (Exception x) {
            context.operation.fail(x);
        }
    }

    private void sendV2SearchRequest(URI searchUri, String searchTerm,
            RegistrySearchResponse response, RequestContext context) {
        Operation search = Operation.createGet(searchUri)
                .setReferer(getHost().getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        if (o.getStatusCode() == 401) {
                            String wwwAuthHeader = getHeader(WWW_AUTHENTICATE_HEADER,
                                    o.getResponseHeaders());

                            if (wwwAuthHeader != null) {
                                requestAuthorizationToken(wwwAuthHeader, context,
                                        () -> sendV2SearchRequest(searchUri, searchTerm, response, context),
                                        (t) -> context.operation.fail(t));
                                return;
                            }
                        }

                        context.operation.fail(ex);
                        return;
                    } else {
                        V2RegistryCatalogResponse body =
                                o.getBody(V2RegistryCatalogResponse.class);

                        if (body.repositories != null) {
                            for (String repository: body.repositories) {
                                if (repository.toLowerCase().contains(searchTerm)) {
                                    Result r = new Result();
                                    r.name = repository;
                                    r.registry = context.registryState.address;
                                    response.results.add(r);
                                }
                            }
                        }

                        String linkHeader = o.getResponseHeader(LINK_HEADER);
                        if (linkHeader != null) {
                            String nextPagePath = extractUrl(linkHeader);
                            if (nextPagePath == null) {
                                context.operation.fail(new LocalizableValidationException(
                                        "Unexpected link header format: " + linkHeader, "adapter.link.header.format", linkHeader));
                                return;
                            }
                            URI nextPageUri = UriUtils.extendUri(
                                    URI.create(context.registryState.address), nextPagePath);
                            sendV2SearchRequest(nextPageUri, searchTerm, response, context);
                        } else {
                            response.numResults = response.results.size();
                            context.operation.setBody(response);
                            context.operation.complete();
                        }
                    }
                });

        String authorization = context.request.customProperties.get(AUTHORIZATION_HEADER);
        if (authorization != null) {
            search.addRequestHeader(AUTHORIZATION_HEADER, authorization);
        }

        this.serviceClient.send(search);
    }

    private String extractUrl(String linkHeader) {
        Matcher matcher = URL_LINK_PATTERN.matcher(linkHeader);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private String getApiVersion(RegistryState registryState) {
        if (registryState.customProperties != null) {
            String apiVersion = registryState.customProperties.get(API_VERSION_PROP_NAME);
            if (apiVersion != null) {
                return apiVersion;
            }
        }

        return DEFAULT_API_VERSION;
    }

    private void processPingRequest(RequestContext context) {
        doPing(ApiVersion.V1, V1_PING_ENDPOINT, context, (t1) -> {
            doPing(ApiVersion.V2, V2_PING_ENDPOINT, context, (t2) -> {
                String error = String.format("Ping attempts failed with errors: %s -- %s",
                        t1.getMessage(), t2.getMessage());
                context.operation.fail(new Exception(error));
            });
        });
    }

    private void doPing(ApiVersion apiVersion, String pingEndpoint,
            RequestContext context, Consumer<Throwable> failureCallback) {
        URI registryUri = context.request.resourceReference;
        URI pingUri = UriUtils.extendUri(registryUri, pingEndpoint);

        logInfo("Pinging registry: %s", pingUri);

        String sslTrust = context.request.customProperties.get(SSL_TRUST_CERT_PROP_NAME);
        if (sslTrust != null) {
            String trustAlias = context.request.customProperties.get(SSL_TRUST_ALIAS_PROP_NAME);
            trustManager.putDelegate(trustAlias, sslTrust);
        }

        Operation pingOp = Operation.createGet(pingUri)
                .setReferer(URI.create("/"))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        if (ApiVersion.V2.equals(apiVersion) && o.getStatusCode() == 401) {
                            String wwwAuthHeader = getHeader(WWW_AUTHENTICATE_HEADER,
                                    o.getResponseHeaders());

                            if (wwwAuthHeader != null) {
                                requestAuthorizationToken(wwwAuthHeader, context,
                                        () -> doPing(apiVersion, pingEndpoint, context, failureCallback),
                                        failureCallback);
                                return;
                            }
                        }
                        // ping requests typically return status code 200 and "true" as the body
                        // the status code is already handled by the operation as an exception
                        failureCallback.accept(ex);
                        return;
                    } else {
                        if (o.getStatusCode() != HTTP_OK) {
                            String errMsg = String.format("Pinging registry endpoint [%s] failed"
                                    + " with status code %s. Expected %s.",
                                    pingUri, o.getStatusCode(), HTTP_OK);
                            failureCallback.accept(new Exception(errMsg));
                            return;
                        }

                        RegistryPingResponse pingResponse = new RegistryPingResponse();
                        pingResponse.apiVersion = apiVersion;
                        context.operation.setBody(pingResponse);
                        context.operation.complete();
                    }
                });

        String authorization = context.request.customProperties.get(AUTHORIZATION_HEADER);
        if (authorization != null) {
            pingOp.addRequestHeader(AUTHORIZATION_HEADER, authorization);
        }

        serviceClient.send(pingOp);
    }

    private void processListImageTagsRequest(RequestContext context) {
        // Docker Hub list tags requests are expected to use the v2 endpoint,
        // otherwise an incomplete list of tags is returned. Also use the 'library' namespace
        // for image without one in order for the request to succeed
        String imageName = context.request.customProperties.get(SEARCH_QUERY_PROP_NAME);
        boolean isDockerHubImage = DockerImage.fromImageName(imageName).isDockerHubImage();
        if (isDockerHubImage) {
            processV2ListImageTagsRequest(context);
            return;
        }

        String apiVersion = getApiVersion(context.registryState);
        if (ApiVersion.V1.toString().equals(apiVersion)) {
            processV1ListImageTagsRequest(context);
        } else if (ApiVersion.V2.toString().equals(apiVersion)) {
            processV2ListImageTagsRequest(context);
        } else {
            String errorMsg = String.format("Unsupported registry version '%s'.", apiVersion);
            context.operation.fail(new LocalizableValidationException(errorMsg, "adapter.unsupported.registry.version", apiVersion));
        }
    }

    private void processV1ListImageTagsRequest(RequestContext context) {
        try {
            String imageName = context.request.customProperties.get(SEARCH_QUERY_PROP_NAME);
            imageName = DockerImage.fromImageName(imageName).getNamespaceAndRepo();

            URI searchUri = URI.create(context.registryState.address);
            String path = UriUtils.buildUriPath("/v1/repositories", imageName, "/tags");
            searchUri = UriUtils.extendUri(searchUri, path);

            logInfo("Performing container image list tags: %s", searchUri);
            Operation search = Operation.createGet(searchUri)
                    .setReferer(getHost().getUri())
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            context.operation.fail(ex);
                            return;
                        }

                        @SuppressWarnings("unchecked")
                        Map<String, String> response = o.getBody(Map.class);
                        List<String> tags = new ArrayList<String>(response.keySet());

                        context.operation.setBody(tags);
                        context.operation.complete();
                    });

            String authorization = context.request.customProperties.get(AUTHORIZATION_HEADER);
            if (authorization != null) {
                search.addRequestHeader(AUTHORIZATION_HEADER, authorization);
            }

            this.serviceClient.send(search);

        } catch (Exception x) {
            context.operation.fail(x);
        }
    }

    private void processV2ListImageTagsRequest(RequestContext context) {
        try {
            String imageName = context.request.customProperties.get(SEARCH_QUERY_PROP_NAME);
            // Use the 'library' namespace for images from docker hub that does not have one
            // in order for the request to succeed
            imageName = DockerImage.fromImageName(imageName).getNamespaceAndRepo();

            URI searchUri = URI.create(context.registryState.address);
            String path = UriUtils.buildUriPath("/v2", imageName, "/tags/list");
            searchUri = UriUtils.extendUri(searchUri, path);

            logInfo("Performing container image list tags: %s", searchUri);
            Operation search = Operation.createGet(searchUri)
                    .setReferer(getHost().getUri())
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            if (o.getStatusCode() == 401) {
                                String wwwAuthHeader = getHeader(WWW_AUTHENTICATE_HEADER,
                                        o.getResponseHeaders());

                                if (wwwAuthHeader != null) {
                                    requestAuthorizationToken(wwwAuthHeader, context,
                                            () -> processV2ListImageTagsRequest(context),
                                            (t) -> context.operation.fail(t));
                                    return;
                                }
                            }

                            context.operation.fail(ex);
                            return;
                        }

                        V2ImageTagsResponse response = o.getBody(V2ImageTagsResponse.class);

                        context.operation.setBody(response.tags);
                        context.operation.complete();
                    });

            String authorization = context.request.customProperties.get(AUTHORIZATION_HEADER);
            if (authorization != null) {
                search.addRequestHeader(AUTHORIZATION_HEADER, authorization);
            }

            this.serviceClient.send(search);

        } catch (Exception x) {
            context.operation.fail(x);
        }
    }

    /*
     * Official documentation describes WWW-Authenticate header but some registry implementations
     * like Harbor return www-authenticate.
     */
    private String getHeader(String header, Map<String, String> headers) {
        header = header.toLowerCase();
        for (Entry<String, String> entry: headers.entrySet()) {
            if (header.equals(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Map<String, String> parseWwwAuthHeader(String wwwAuthHeader) {
        List<String> kvPairs = new ArrayList<>();
        Matcher m = KV_PATTERN.matcher(wwwAuthHeader);
        while (m.find()) {
            kvPairs.add(m.group());
        }
        return kvPairs.stream()
                .map(elem -> elem.split("="))
                .filter(elem -> elem.length == 2)
                .collect(Collectors.toMap(e -> e[0], e -> e[1].substring(1, e[1].length() - 1)));
    }

    private void requestAuthorizationToken(String wwwAuthHeader, RequestContext context,
            Runnable successCallback, Consumer<Throwable> failureCallback) {
        try {
            Map<String, String> kvs = parseWwwAuthHeader(wwwAuthHeader);

            String bearerRealm = kvs.get(BEARER_REALM_WWW_AUTH_PROP);
            String service = kvs.get(SERVICE_WWW_AUTH_PROP);
            String scope = kvs.get(SCOPE_WWW_AUTH_PROP);
            URI tokenServiceUri = new URI(bearerRealm);
            tokenServiceUri = UriUtils.extendUriWithQuery(tokenServiceUri,
                    SERVICE_WWW_AUTH_PROP, service, SCOPE_WWW_AUTH_PROP, scope);

            logInfo("Requesting token from %s", tokenServiceUri.toString());
            Operation getTokenOp = Operation.createGet(tokenServiceUri)
                    .setReferer(getUri())
                    .setCompletion((op, ex) -> {
                        if (ex != null) {
                            failureCallback.accept(ex);
                            return;
                        }

                        TokenServiceResponse tokenServiceResponse =
                                op.getBody(TokenServiceResponse.class);
                        String authorizationHeaderValue = String.format("%s %s",
                                BEARER_TOKEN_PREFIX, tokenServiceResponse.token);
                        context.request.customProperties.put(AUTHORIZATION_HEADER,
                                authorizationHeaderValue);

                        successCallback.run();
                    });

            String authorization = context.request.customProperties.get(AUTHORIZATION_HEADER);
            if (authorization != null) {
                getTokenOp.addRequestHeader(AUTHORIZATION_HEADER, authorization);
            }

            serviceClient.send(getTokenOp);
        } catch (Exception e) {
            failureCallback.accept(e);
        }
    }
}
