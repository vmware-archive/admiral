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

package com.vmware.admiral.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class RegistryUtil {

    public static final String DEFAULT_DOCKER_REGISTRY_ADDRESS = "registry.hub.docker.com";

    public static void isProvisioningAllowedByRegistryWhitelist(ServiceHost serviceHost,
            Collection<String> requestTenantLinks,
            String containerImage,
            BiConsumer<Boolean, Throwable> completionHandler) {

        serviceHost.log(Level.INFO, "Checking registry whitelist for image %s", containerImage);
        // extract the registry hostname from the image.
        // If no hostname is set, default to Docker Hub
        DockerImage parsedImage;
        try {
            parsedImage = DockerImage.fromImageName(containerImage);
        } catch (Throwable ex) {
            serviceHost.log(Level.SEVERE, "Failed to parse docker image from String '%s': %s",
                    containerImage, Utils.toString(ex));
            completionHandler.accept(null, ex);
            return;
        }

        String registryHost = nonNullValue(parsedImage.getHost(), DEFAULT_DOCKER_REGISTRY_ADDRESS);

        // filter only important tenant links
        Collection<String> filteredTenantLinks = filterWhitelistRelatedTenantLinks(
                requestTenantLinks);
        serviceHost.log(Level.INFO,
                "Searching for registries with tenantLinks [%s] (Request tenant links are [%s])",
                filteredTenantLinks == null ? null : String.join(", ", filteredTenantLinks),
                requestTenantLinks == null ? null : String.join(", ", requestTenantLinks));

        // Try to find a definition of global and/or project specific registries with this hostname
        findRegistriesByHostname(serviceHost, registryHost, filteredTenantLinks,
                (registries, errors) -> {
                    if (errors != null && !errors.isEmpty()) {
                        Throwable firstError = errors.iterator().next();
                        serviceHost.log(Level.SEVERE,
                                "Failed to query registries with address [%s]: %s", registryHost,
                                Utils.toString(firstError));
                        completionHandler.accept(null, firstError);
                        return;
                    }

                    Collection<RegistryState> filteredRegistries = filterRegistriesByPath(serviceHost, registries, parsedImage);

                    // if there is at least one matching registry
                    // then the provisioning should be permitted
                    serviceHost.log(Level.INFO, "Found %s matching registries.",
                            filteredRegistries == null ? 0 : filteredRegistries.size());
                    boolean allowRequest = filteredRegistries != null && filteredRegistries.size() > 0;
                    completionHandler.accept(allowRequest, null);
                });
    }

    /**
     * Retrieve all the registries available to the given group (and global registries)
     *
     * @param tenantLinks
     * @param registryFilter
     * @param consumer
     */
    public static void findRegistries(ServiceHost serviceHost, Collection<String> tenantLinks,
            String registryFilter, BiConsumer<Collection<RegistryState>, Collection<Throwable>> consumer) {

        List<QueryTask> queryTasks = new ArrayList<>();

        if (registryFilter != null && !registryFilter.isEmpty()) {
            // add query for a registry with a specific name and group
            queryTasks.add(buildRegistryQueryByNameAndTenantLinks(registryFilter, tenantLinks));
        } else if (tenantLinks != null && !tenantLinks.isEmpty()) {
            // add query for global groups
            queryTasks.add(buildRegistryQueryByTenantLinks(null));
            // add query for registries of a specific tenant
            queryTasks.add(buildRegistryQueryByTenantLinks(tenantLinks));
        } else {
            // add query for all registries if no tenant
            queryTasks.add(buildAllRegistriesQuery());
        }

        queryForRegistries(serviceHost, queryTasks, consumer);
    }

    /**
     * Retrieve the registries available to the given group for the given host (and global registries)
     * @param serviceHost
     * @param hostname
     * @param tenantLinks
     * @param consumer
     */
    public static void findRegistriesByHostname(ServiceHost serviceHost, String hostname,
            Collection<String> tenantLinks,
            BiConsumer<Collection<RegistryState>, Collection<Throwable>> consumer) {

        List<QueryTask> queryTasks = new ArrayList<>();

        if (tenantLinks != null && !tenantLinks.isEmpty()) {
            // add query for global groups
            queryTasks.add(buildRegistryQuery(buildQueryByTenantLinks(null),
                    buildQueryByHostname(hostname)));
            // add query for registries of a specific tenant
            queryTasks.add(buildRegistryQuery(buildQueryByTenantLinks(tenantLinks),
                    buildQueryByHostname(hostname)));
        } else {
            // add query for all registries if no tenant
            queryTasks.add(buildRegistryQuery(buildQueryByHostname(hostname)));
        }

        queryForRegistries(serviceHost, queryTasks, consumer);
    }

    /**
     * Filter registries by image path.
     * @param host
     * @param registries
     * @param image
     * @return
     */
    public static List<RegistryState> filterRegistriesByPath(ServiceHost host, Collection<RegistryState> registries, DockerImage image) {
        String imageHost = nonNullValue(image.getHost(), DEFAULT_DOCKER_REGISTRY_ADDRESS);
        String imagePath = image.getNamespace();
        host.log(Level.FINE, "Image path: %s.", imagePath);

        List<String> imagePathSegments = new ArrayList<>();
        if (imagePath != null && !imagePath.isEmpty()) {
            imagePathSegments = Arrays.asList(imagePath.split("/"));
        }

        if (registries != null && !registries.isEmpty()) {
            List<String> finalPathSegments = imagePathSegments;
            return registries.stream().filter(r -> {
                if (r.address != null && !r.address.isEmpty()) {
                    // strip schema from registry address
                    String registryAddress = r.address.replaceFirst("^http[s]?:\\/\\/", "");
                    String imageAddress = imageHost;

                    if (imageAddress.equals(registryAddress)) {
                        return true;
                    }

                    for (String segment: finalPathSegments) {
                        imageAddress = imageAddress + "/" + segment;
                        if (imageAddress.equals(registryAddress)) {
                            return true;
                        }
                    }
                }

                return false;
            }).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    /**
     * Executes the given query tasks
     * @param serviceHost
     * @param queryTasks
     * @param consumer
     */
    private static void queryForRegistries(ServiceHost serviceHost, Collection<QueryTask> queryTasks,
            BiConsumer<Collection<RegistryState>, Collection<Throwable>> consumer) {

        List<Operation> queryOperations = new ArrayList<>();
        for (QueryTask queryTask : queryTasks) {
            queryOperations.add(Operation
                    .createPost(UriUtils.buildUri(serviceHost, ServiceUriPaths.CORE_QUERY_TASKS))
                    .setBody(queryTask)
                    .setReferer(serviceHost.getUri()));
        }

        if (!queryOperations.isEmpty()) {
            OperationJoin.create(queryOperations.toArray(new Operation[0]))
                    .setCompletion((ops, failures) -> {
                        if (failures != null) {
                            consumer.accept(null, failures.values());
                            return;
                        }

                        List<RegistryState> registryStates = new ArrayList<>();
                        for (Operation o : ops.values()) {
                            QueryTask result = o.getBody(QueryTask.class);

                            for (Map.Entry<String, Object> document : result.results.documents.entrySet()) {
                                RegistryState registryState = Utils.fromJson(document.getValue(), RegistryState.class);
                                registryStates.add(registryState);
                            }
                        }

                        consumer.accept(registryStates, null);
                    })
                    .sendWith(serviceHost);
        } else {
            // no registry links available
            consumer.accept(Collections.emptyList(), null);
        }
    }

    /**
     * Create a query to return all RegistryState links within a group, tenant or global
     * RegistryState links if the tenantLinks collection is null/empty.
     *
     * @param tenantLinks
     * @return QueryTask
     */
    private static Query buildQueryByTenantLinks(Collection<String> tenantLinks) {
        return QueryUtil.addTenantGroupAndUserClause(tenantLinks);
    }

    /**
     * Create a query to return all RegistryState links.
     *
     * @return
     */
    private static QueryTask buildAllRegistriesQuery() {
        return buildRegistryQuery();
    }

    /**
     * Create a query to return all RegistryState links within a group, tenant or global
     * RegistryState links if the tenantLinks collection is null/empty.
     *
     * @param tenantLinks
     * @return QueryTask
     */
    private static QueryTask buildRegistryQueryByTenantLinks(Collection<String> tenantLinks) {
        return buildRegistryQuery(buildQueryByTenantLinks(tenantLinks));
    }

    /**
     * Create a query to return all RegistryState links matching a given name. Results are filtered
     * within a specified group/tenant or are global RegistryState links if the tenantLinks
     * collection is null/empty.
     *
     * @param registryName
     * @param tenantLinks
     * @return QueryTask
     */
    private static QueryTask buildRegistryQueryByNameAndTenantLinks(String registryName,
            Collection<String> tenantLinks) {
        Query nameClause = new Query()
                .setTermPropertyName(RegistryState.FIELD_NAME_NAME)
                .setTermMatchValue(registryName);

        if (tenantLinks == null || tenantLinks.isEmpty()) {
            return buildRegistryQuery(nameClause);
        }

        Query tenantsClause = buildQueryByTenantLinks(tenantLinks);
        return buildRegistryQuery(nameClause, tenantsClause);
    }

    /**
     * Builds a query task from the given queries.
     * @param additionalClauses
     * @return
     */
    private static QueryTask buildRegistryQuery(Query... additionalClauses) {
        List<Query> clauses = new ArrayList<>();
        if (additionalClauses != null) {
            clauses.addAll(Arrays.asList(additionalClauses));
        }

        Query excludeDisabledClause = new Query()
                .setTermPropertyName(RegistryState.FIELD_NAME_DISABLED)
                .setTermMatchValue(Boolean.TRUE.toString());
        excludeDisabledClause.occurance = Occurance.MUST_NOT_OCCUR;
        clauses.add(excludeDisabledClause);

        QueryTask queryTask = QueryUtil.buildQuery(RegistryState.class, true,
                clauses.toArray(new Query[clauses.size()]));
        QueryUtil.addExpandOption(queryTask);

        return queryTask;
    }

    /**
     * For the purposes of registry whitelist checks, only /projects/{id} and /tenants/{id} tenant
     * links are important. /tenants/{id}/groups/{group}, /users/{principal} and other custom tenant
     * links will be removed in the result.
     */
    private static Collection<String> filterWhitelistRelatedTenantLinks(
            Collection<String> allTenantLinks) {
        if (allTenantLinks == null) {
            return null;
        }

        return allTenantLinks.stream().filter((tenantLink) -> {
            if (tenantLink.startsWith(MultiTenantDocument.PROJECTS_IDENTIFIER)) {
                return true;
            }

            return tenantLink.startsWith(MultiTenantDocument.TENANTS_PREFIX)
                    && !tenantLink.contains(MultiTenantDocument.GROUP_IDENTIFIER);
        }).collect(Collectors.toSet());
    }

    private static Query buildQueryByHostname(String hostname) {
        String noSchemaPattern = String.format("%s*", hostname);
        String httpPattern = String.format("http://%s*", hostname);
        String httpsPattern = String.format("https://%s*", hostname);

        return Query.Builder.create(Occurance.MUST_OCCUR)
                .addFieldClause(RegistryState.FIELD_NAME_ADDRESS,
                        noSchemaPattern,
                        MatchType.WILDCARD,
                        Occurance.SHOULD_OCCUR)
                .addFieldClause(RegistryState.FIELD_NAME_ADDRESS,
                        httpPattern,
                        MatchType.WILDCARD,
                        Occurance.SHOULD_OCCUR)
                .addFieldClause(RegistryState.FIELD_NAME_ADDRESS,
                        httpsPattern,
                        MatchType.WILDCARD,
                        Occurance.SHOULD_OCCUR)
                .build();
    }

    private static String nonNullValue(String desiredValue, String defaultValue) {
        if (desiredValue != null && !desiredValue.isEmpty()) {
            return desiredValue;
        }

        return defaultValue;
    }
}
