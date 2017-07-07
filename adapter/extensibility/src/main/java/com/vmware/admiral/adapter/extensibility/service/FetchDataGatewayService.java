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

package com.vmware.admiral.adapter.extensibility.service;

import java.net.URI;
import java.net.URISyntaxException;

import com.vmware.admiral.service.common.UrlEncodedReverseProxyService;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapters.registry.FetchDataRequest;
import com.vmware.photon.controller.model.adapters.registry.FetchDataRequest.RequestType;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Endpoint adapter proxy service to forward requests to 3rd party services.
 */
public class FetchDataGatewayService extends StatelessService {
    public static final String SELF_LINK = "/adapter-extensibility/fetch-data";

    public FetchDataGatewayService() {
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            patch.fail(new IllegalArgumentException("body is required"));
            return;
        }
        FetchDataRequest fetchDataRequest = patch.getBody(FetchDataRequest.class);

        String servicePath = extractServicePath(patch.getUri());

        getEndpointType(fetchDataRequest)
                .thenCompose(this::getAdapterConfig)
                .whenComplete((cfg, err) -> {
                    if (err != null) {
                        logSevere(() -> Utils.toString(err));
                        patch.fail(err);
                    } else {
                        String link = cfg.adapterEndpoints
                                .get(AdapterTypePath.ENDPOINT_CONFIG_ADAPTER.key);
                        URI uri = URI.create(link);
                        String scheme = uri.getScheme();
                        String authority = uri.getAuthority();
                        URI forwardUri = null;
                        if (scheme != null && authority != null) {
                            try {
                                forwardUri = new URI(scheme, authority, null, null, null);
                            } catch (URISyntaxException e) {
                                logSevere(e);
                            }
                        }
                        if (forwardUri == null) {
                            logFine("No custom host for endpointType {}. "
                                            + "Use default. Endpoint config adapter URI is {}.",
                                    cfg.id, link);
                            forwardUri = getHost().getUri();
                        }
                        URI backendURI = forwardUri.resolve(servicePath);
                        String shallStartWith = UriUtils.buildUriPath(UriPaths.ADAPTER, cfg.id);
                        String pathToCheck = backendURI.getPath();
                        if (pathToCheck.startsWith(shallStartWith)) {
                            sendRequest(UrlEncodedReverseProxyService
                                    .createForwardOperation(patch, backendURI,
                                            Operation::createPatch));
                        } else {
                            patch.fail(new IllegalArgumentException(
                                    "Requested servicePath shall start with: " + shallStartWith
                                            + ". Actual: " + pathToCheck));
                        }
                    }
                });
    }

    private DeferredResult<PhotonModelAdapterConfig> getAdapterConfig(
            String endpointType) {
        String endpointTypeLink = UriUtils.buildUriPath(
                PhotonModelAdaptersRegistryService.FACTORY_LINK, endpointType);
        return sendWithDeferredResult(Operation.createGet(this, endpointTypeLink),
                PhotonModelAdapterConfig.class);
    }

    private DeferredResult<String> getEndpointType(FetchDataRequest fetchDataRequest) {
        AssertUtil.assertNotNull(fetchDataRequest, "'fetchDataRequest' must be set.");
        RequestType requestType = fetchDataRequest.requestType;
        AssertUtil.assertNotNull(requestType, "'fetchDataRequest.requestType' must be set.");
        switch (requestType) {
        case EndpointType: {
            return DeferredResult.completed(fetchDataRequest.entityId);
        }
        case Endpoint: {
            String entityId = fetchDataRequest.entityId;
            AssertUtil.assertNotNull(entityId, "'fetchDataRequest.entityId' must be set.");
            return getEndpointType(entityId);
        }
        case ResourceDetails:
        case ResourceOperation: {
            String entityId = fetchDataRequest.entityId;
            AssertUtil.assertNotNull(entityId, "'fetchDataRequest.entityId' must be set.");
            return sendWithDeferredResult(Operation.createGet(this, entityId),
                    EndpointLinkAware.class)
                    .thenCompose(epla -> getEndpointType(epla.endpointLink));
        }
        default:
            return DeferredResult
                    .failed(new IllegalArgumentException("requestType: " + requestType));
        }
    }

    private DeferredResult<String> getEndpointType(String endpointLink) {
        return sendWithDeferredResult(Operation.createGet(this, endpointLink), EndpointState.class)
                .thenApply(ep -> ep.endpointType);
    }

    public static class EndpointLinkAware {
        public String endpointLink;
    }

    private static String extractServicePath(URI uri) {
        String uriPath = uri.getPath();

        int rpIndex = uriPath.indexOf(SELF_LINK);
        if (rpIndex == -1) {
            // no additional data provided!
            return null;
        }
        String opPath = uriPath.substring(rpIndex + SELF_LINK.length());

        String path = opPath;
        String query = uri.getRawQuery();
        if (query != null) {
            path = path + "?" + query;
        }
        String fragment = uri.getFragment();
        if (fragment != null) {
            path = path + "#" + fragment;
        }

        return path;
    }

}
