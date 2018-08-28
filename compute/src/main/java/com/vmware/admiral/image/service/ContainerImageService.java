/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.image.service;

import static com.vmware.admiral.adapter.registry.service.RegistryAdapterService.SEARCH_QUERY_PROP_NAME;
import static com.vmware.admiral.common.util.ServiceUtils.addServiceRequestRoute;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ImageOperationType;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse.Result;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.DockerImage;
import com.vmware.admiral.common.util.RegistryUtil;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.host.HostInitRegistryAdapterServiceConfig;
import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.admiral.log.EventLogService.EventLogState.EventLogType;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Service for providing image search
 */
public class ContainerImageService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.IMAGES;

    public static final String REGISTRY_FILTER_QUERY_PARAM_NAME = "registry";
    public static final String TENANT_LINKS_PARAM_NAME = MultiTenantDocument.FIELD_NAME_TENANT_LINKS;
    public static final String TENANT_LINKS_SEPARATOR = ",";

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() != Action.GET) {
            Operation.failActionNotSupported(op);
            return;
        }

        handleGet(op);
    }

    @Override
    public void handleGet(Operation op) {
        try {
            handleSearchRequest(op);
        } catch (Exception x) {
            logSevere(x);
            op.fail(x);
        }
    }

    /**
     * Perform an image search using the registry adapter
     *
     * The group query parameter is used to limit the registries to search to the ones visible the
     * given group (including global groups) while the rest of the request parameters (search query,
     * sort, etc.) are passed as custom properties directly to the adapter
     *
     * @param op
     */
    private void handleSearchRequest(Operation op) {
        URI registryAdapterUri = HostInitRegistryAdapterServiceConfig.registryAdapterReference;
        AssertUtil.assertNotNull(registryAdapterUri, "registryAdapterReference");

        // get the group parameter to build the registry query
        // pass the rest of the query parameters as custom properties directly to the adapter
        Map<String, String> queryParams = UriUtils.parseUriQueryParams(op.getUri());

        String searchTerm = queryParams.get(SEARCH_QUERY_PROP_NAME);
        AssertUtil.assertNotNullOrEmpty(searchTerm, SEARCH_QUERY_PROP_NAME);

        String registryFilter = queryParams.get(REGISTRY_FILTER_QUERY_PARAM_NAME);
        String rawTenantLinks = queryParams.remove(TENANT_LINKS_PARAM_NAME);

        logFine("Searching container images for tenantLinks: " + rawTenantLinks);

        List<String> tenantLinks;
        if (rawTenantLinks == null || rawTenantLinks.isEmpty()) {
            tenantLinks = null;
        } else {
            tenantLinks = Arrays.asList(rawTenantLinks.split(TENANT_LINKS_SEPARATOR));
        }

        // query for registries and execute an adapter request for each one
        BiConsumer<Collection<RegistryState>, Collection<Throwable>> registriesConsumer = (registries, failures) -> {
            if (failures != null) {
                op.fail(failures.iterator()
                        .next());
                return;
            }

            if (registries != null) {
                handleSearchRequest(op, registryAdapterUri, queryParams, registries, rawTenantLinks);
            }
        };

        // limit the number of adapter requests if the search term contains a registry hostname
        // otherwise, query for registries and execute an adapter request for each one
        DockerImage image = parseDockerImage(searchTerm);
        if (image != null && image.getHost() != null && !image.getHost().isEmpty()) {
            RegistryUtil.findRegistriesByHostname(getHost(), image.getHost(), tenantLinks,
                    (registries, errors) -> {
                        if (errors != null && !errors.isEmpty()) {
                            op.fail(errors.iterator().next());
                            return;
                        }

                        if (registries.isEmpty()) {
                            // failed to find a matching registry, create adapter request for each
                            // one
                            RegistryUtil.findRegistries(getHost(), tenantLinks, registryFilter,
                                    registriesConsumer);
                            return;
                        }

                        queryParams.put(SEARCH_QUERY_PROP_NAME, image.getNamespaceAndRepo());
                        registriesConsumer.accept(registries, null);
                    });
        } else {
            RegistryUtil.findRegistries(getHost(), tenantLinks, registryFilter, registriesConsumer);
        }
    }

    private void handleSearchRequest(Operation op, URI registryAdapterUri,
            Map<String, String> queryParams, Collection<RegistryState> registries,
            String tenantLink) {

        Map<String, String> registryLinks = new HashMap<>();
        // if same registry is repeated, return it only once
        registries.stream()
                .forEach(registryState -> {
                    String address = UriUtilsExtended.buildDockerRegistryUri(registryState.address).toString();
                    if (!registryLinks.containsKey(address)) {
                        registryLinks.put(address, registryState.documentSelfLink);
                    }
                });

        Collection<String> searchRegistryLinks = registryLinks.values();
        if (searchRegistryLinks.isEmpty()) {
            op.fail(new LocalizableValidationException("No registries found", "compute.registries.not.found"));
            return;
        }

        Integer parsedLimit = null;
        try {
            parsedLimit = Integer.parseInt(queryParams.get(UriUtils.URI_PARAM_ODATA_LIMIT));
        } catch (Exception e) {
        }

        final int limit = parsedLimit != null ? parsedLimit : 0;

        List<Operation> searchOperations = new ArrayList<Operation>(searchRegistryLinks.size());
        for (String registryLink : searchRegistryLinks) {
            Operation searchOp = createSearchOperation(registryAdapterUri, queryParams,
                    registryLink);

            searchOperations.add(searchOp);
        }

        OperationJoin join = OperationJoin.create(searchOperations
                .toArray(new Operation[searchOperations.size()]));

        join.setCompletion((searchOps, failures) -> {
            // failures are ignored, so search results will be returned even if only some of the
            // requests were successful
            RegistrySearchResponse mergedResponse = new RegistrySearchResponse();
            for (Operation searchOp : searchOps.values()) {
                if (searchOp.hasBody()) {
                    RegistrySearchResponse registryResponse = searchOp
                            .getBody(RegistrySearchResponse.class);

                    if (registryResponse.results == null) {
                        continue;
                    }

                    for (Result result : registryResponse.results) {
                        result.name = UriUtilsExtended.extractHostAndPort(result.registry) + "/"
                                + result.name;
                    }
                    mergedResponse.merge(registryResponse);
                }
            }

            logFailures(failures, tenantLink);

            mergedResponse.results = filterResultsByRegistryPath(mergedResponse.results,
                    registries, failures);

            if (failures != null && !failures.isEmpty()) {
                mergedResponse.isPartialResult = true;
            }

            if (limit > 0) {
                mergedResponse.limit(limit);
            }

            // pagination doesn't make sense when querying over multiple registries
            mergedResponse.page = -1;
            mergedResponse.pageSize = -1;
            mergedResponse.numPages = -1;

            op.setBody(mergedResponse);

            logFine("Search result: %s", Utils.toJsonHtml(mergedResponse));

            op.complete();
        });

        join.sendWith(this);
    }

    private List<Result> filterResultsByRegistryPath(Collection<Result> results,
            Collection<RegistryState> registries, Map<Long, Throwable> failures) {

        List<Result> filteredResults = new ArrayList<>();
        results.stream().forEach(res -> {
            String imageName = res.name;
            DockerImage parsedImage;
            try {
                parsedImage = DockerImage.fromImageName(imageName);
            } catch (Throwable ex) {
                log(Level.SEVERE, "Failed to parse docker image from String '%s': %s",
                        imageName, Utils.toString(ex));
                long nextFailureNumber = failures.keySet().iterator().next();
                failures.put(nextFailureNumber, ex);
                return;
            }

            List<RegistryState> filteredRegistryStates = RegistryUtil
                    .filterRegistriesByPath(getHost(), registries, parsedImage);

            log(Level.FINE, "Found %s matching registries.",
                    filteredRegistryStates == null ? 0 : filteredRegistryStates.size());
            if (filteredRegistryStates.size() > 0) {
                filteredResults.add(res);
            }
        });

        return filteredResults;
    }

    private Operation createSearchOperation(URI registryAdapterUri,
            Map<String, String> queryParams, String searchRegistryLink) {

        AdapterRequest imageRequest = new AdapterRequest();
        imageRequest.operationTypeId = ImageOperationType.SEARCH.id;
        imageRequest.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        imageRequest.resourceReference = UriUtils.buildPublicUri(getHost(), searchRegistryLink);
        imageRequest.customProperties = new HashMap<>(queryParams);

        Operation adapterOp = Operation
                .createPatch(registryAdapterUri)
                .setBody(imageRequest);

        return adapterOp;
    }

    private void logFailures(Map<Long, Throwable> failures, String tenantLink) {
        if (failures == null || failures.isEmpty()) {
            return;
        }

        List<String> errors = new ArrayList<>(failures.values().size());
        for (Throwable t: failures.values()) {
            logWarning("Failed to perform registry search: %s", t.getMessage());
            errors.add(t.getMessage());
        }

        EventLogState eventLog = new EventLogState();
        eventLog.resourceType = getClass().getName();
        eventLog.eventLogType = EventLogType.WARNING;
        eventLog.description = String.format("Error(s) while trying to perform registry search: %s",
                errors.toString());
        if (tenantLink != null) {
            eventLog.tenantLinks = Collections.singletonList(tenantLink);
        }

        Operation createEventLog = Operation.createPost(this, EventLogService.FACTORY_LINK)
                .setBody(eventLog)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error on publishing event log: %s", Utils.toString(e));
                    }
                });
        sendRequest(createEventLog);
    }

    private DockerImage parseDockerImage(String imageName) {
        try {
            return DockerImage.fromImageName(imageName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        addServiceRequestRoute(d, Action.GET,
                String.format("Search for images. Specify the name of the resource "
                                + "you are searching for with URI query with key \"%s\".",
                        SEARCH_QUERY_PROP_NAME), RegistrySearchResponse.class);
        return d;
    }
}
