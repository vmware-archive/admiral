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

package com.vmware.admiral.compute.container;

import static com.vmware.admiral.common.util.QueryUtil.createAnyPropertyClause;
import static com.vmware.admiral.common.util.QueryUtil.createKindClause;
import static com.vmware.admiral.common.util.ServiceDocumentQuery.error;
import static com.vmware.admiral.common.util.ServiceDocumentQuery.noResult;
import static com.vmware.admiral.common.util.ServiceDocumentQuery.result;
import static com.vmware.admiral.common.util.UriUtilsExtended.flattenQueryParams;
import static com.vmware.admiral.common.util.UriUtilsExtended.parseBooleanParam;
import static com.vmware.xenon.common.UriUtils.URI_WILDCARD_CHAR;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import com.vmware.admiral.adapter.registry.service.RegistryAdapterService;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse.Result;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceDocumentQuery.ServiceDocumentQueryElementResult;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.TemplateSpec.TemplateType;
import com.vmware.admiral.image.service.ContainerImageService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Search for templates (CompositeDescriptions and container images)
 */
public class TemplateSearchService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.TEMPLATES;
    public static final String QUERY_PARAM = RegistryAdapterService.SEARCH_QUERY_PROP_NAME;
    public static final String GROUP_PARAM = CompositeDescription.FIELD_NAME_TENANT_LINKS;

    public static final String TEMPLATES_ONLY_PARAM = "templatesOnly";
    public static final String TEMPLATES_PARENT_ONLY_PARAM = "templatesParentOnly";
    public static final String IMAGES_ONLY_PARAM = "imagesOnly";

    public static class Response {
        public Collection<TemplateSpec> results;
        public boolean isPartialResult;
    }

    @Override
    public void handleGet(Operation get) {
        Map<String, String> queryParams = UriUtils.parseUriQueryParams(get.getUri());
        String query = queryParams.get(QUERY_PARAM);
        AssertUtil.assertNotEmpty(query, QUERY_PARAM);

        // FIXME search doesn't work when the text contain non alpha chars so disable for now
        // searching fields indexed as TEXT, so case insensitive
        // query = query.toLowerCase();

        boolean templatesOnly = parseBooleanParam(queryParams.remove(TEMPLATES_ONLY_PARAM));
        boolean imagesOnly = parseBooleanParam(queryParams.remove(IMAGES_ONLY_PARAM));

        if (templatesOnly && imagesOnly) {
            throw new IllegalArgumentException("Can't use both templatesOnly and imagesOnly");
        }

        Set<TemplateSpec> results = Collections.newSetFromMap(new ConcurrentHashMap<>());

        // shared callback called by individual queries as they finish (successfully or not)
        AtomicInteger queriesCountdown = new AtomicInteger(2);
        if (templatesOnly || imagesOnly) {
            queriesCountdown.decrementAndGet();
        }

        BiConsumer<ServiceDocumentQueryElementResult<TemplateSpec>, Boolean> resultConsumer =
                (r, isPartialResult) -> {
                    if (r.hasException() || !r.hasResult()) {
                        if (r.hasException()) {
                            Utils.logWarning("Query failure: %s", Utils.toString(r.getException()));
                        }

                        if (queriesCountdown.decrementAndGet() == 0) {
                            Response response = new Response();
                            response.results = prependOfficialResults(new ArrayList<>(results));
                            if (isPartialResult != null) {
                                response.isPartialResult = isPartialResult;
                            }
                            get.setBody(response);
                            get.complete();
                        }

                    } else if (r.hasResult()) {
                        results.add(r.getResult());
                    }
                };

        if (!imagesOnly) {
            executeTemplateQuery(query, queryParams, resultConsumer);
        }
        if (!templatesOnly) {
            executeImageQuery(queryParams, resultConsumer);
        }
    }

    private void executeTemplateQuery(String query, Map<String, String> queryParams,
            BiConsumer<ServiceDocumentQueryElementResult<TemplateSpec>, Boolean> resultConsumer) {

        if (!query.startsWith(URI_WILDCARD_CHAR)) {
            query = URI_WILDCARD_CHAR + query;
        }

        if (!query.endsWith(URI_WILDCARD_CHAR)) {
            query = query + URI_WILDCARD_CHAR;
        }

        String tenantLink = queryParams.get(GROUP_PARAM);
        List<String> tenantLinks = null;
        if (tenantLink != null) {
            tenantLinks = Arrays.asList(tenantLink.split("\\s*,\\s*"));
        }

        QueryTask queryTask = new QueryTask();
        queryTask.querySpec = new QueryTask.QuerySpecification();
        queryTask.taskInfo.isDirect = true;

        QueryUtil.addExpandOption(queryTask);

        boolean templatesParentOnly = parseBooleanParam(queryParams
                .remove(TEMPLATES_PARENT_ONLY_PARAM));

        QueryTask.Query compositeDescClause = createCompositeDescClause(query, tenantLinks,
                templatesParentOnly);
        compositeDescClause.occurance = Occurance.SHOULD_OCCUR;

        QueryTask.Query containerDescClause = createContainerDescClause(query, tenantLinks,
                templatesParentOnly);
        containerDescClause.occurance = Occurance.SHOULD_OCCUR;

        queryTask.querySpec.query.addBooleanClause(compositeDescClause);
        queryTask.querySpec.query.addBooleanClause(containerDescClause);

        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        resultConsumer.accept(error(ex), null);

                    } else {
                        QueryTask resultTask = o.getBody(QueryTask.class);
                        ServiceDocumentQueryResult result = resultTask.results;

                        if (result == null || result.documents == null) {
                            resultConsumer.accept(noResult(), null);
                            return;
                        }

                        List<String> compositeDescriptionLinks = new ArrayList<>();
                        List<String> containerDescriptionLinks = new ArrayList<>();
                        result.documents.forEach((link, document) -> {
                            if (link.startsWith(CompositeDescriptionFactoryService.SELF_LINK)) {
                                compositeDescriptionLinks.add(link);
                                resultConsumer.accept(result(createTemplateFromCompositeDesc(
                                        document), result.documents.size()), null);

                            } else if (link.startsWith(
                                    ContainerDescriptionService.FACTORY_LINK)) {

                                containerDescriptionLinks.add(link);

                            } else {
                                logWarning("Unexpected result type: %s", link);
                            }
                        });

                        if (containerDescriptionLinks.isEmpty()) {
                            resultConsumer.accept(noResult(), null);
                            return;
                        }

                        logFine("Querying for CompositeDescriptions containing: %s",
                                containerDescriptionLinks);

                        // query for CompositeDescriptions that contain the found
                        // ContainerDescriptions and add to the results
                        QueryTask compositeQueryTask = QueryUtil
                                .buildQuery(CompositeDescription.class, true);

                        String descriptionLinksItemField = QueryTask.QuerySpecification
                                .buildCollectionItemName(
                                CompositeDescription.FIELD_NAME_DESCRIPTION_LINKS);

                        QueryUtil.addExpandOption(compositeQueryTask);
                        QueryUtil.addListValueClause(compositeQueryTask,
                                descriptionLinksItemField, containerDescriptionLinks);

                        // exclude results already found by the previous query
                        QueryUtil.addListValueExcludeClause(compositeQueryTask,
                                CompositeDescription.FIELD_NAME_SELF_LINK,
                                compositeDescriptionLinks);

                        QueryUtil.addListValueExcludeClause(compositeQueryTask,
                                CompositeDescription.FIELD_NAME_PARENT_DESCRIPTION_LINK,
                                compositeDescriptionLinks);

                        new ServiceDocumentQuery<TemplateSpec>(getHost(), TemplateSpec.class)
                                .query(compositeQueryTask,
                                        (r) -> {
                                            if (r.hasResult()) {
                                                // mark the template type before passing to the results
                                                r.getResult().templateType = TemplateType.COMPOSITE_DESCRIPTION;
                                            }
                                            resultConsumer.accept(r, null);
                                        });
                    }
                }));
    }

    private void executeImageQuery(Map<String, String> queryParams,
            BiConsumer<ServiceDocumentQueryElementResult<TemplateSpec>, Boolean> resultConsumer) {

        URI imageSearchUri = UriUtils.buildUri(getHost(), ContainerImageService.SELF_LINK);

        // remove leading asterisks as the registry will reject them
        String query = queryParams.get(QUERY_PARAM);
        query = query.replaceAll("^\\*+", "");
        queryParams.put(QUERY_PARAM, query);

        // pass on the query parameters to the image search service
        imageSearchUri = UriUtils.extendUriWithQuery(imageSearchUri, flattenQueryParams(
                queryParams));

        sendRequest(Operation.createGet(imageSearchUri)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        resultConsumer.accept(error(ex), null);

                    } else {
                        RegistrySearchResponse response = o.getBody(RegistrySearchResponse.class);
                        if (response.results != null) {
                            for (Result result : response.results) {
                                resultConsumer.accept(result(createTemplateFromImageResult(
                                        result), response.results.size()), null);
                            }
                        }
                        resultConsumer.accept(noResult(), response.isPartialResult);
                    }
                }));
    }

    private TemplateSpec createTemplateFromImageResult(Result result) {
        TemplateSpec template = new TemplateSpec();
        template.templateType = TemplateType.CONTAINER_IMAGE_DESCRIPTION;
        template.name = result.name;
        template.description = result.description;
        template.registry = result.registry;
        template.automated = result.automated;
        template.trusted = result.trusted;
        template.official = result.official;
        template.starCount = result.starCount;

        return template;
    }

    private TemplateSpec createTemplateFromCompositeDesc(Object document) {
        TemplateSpec template = Utils.fromJson(document, TemplateSpec.class);
        template.templateType = TemplateType.COMPOSITE_DESCRIPTION;

        return template;
    }

    private QueryTask.Query createCompositeDescClause(String query, List<String> tenantLinks,
            boolean templatesParentOnly) {
        QueryTask.Query compositeDescClause = new QueryTask.Query();
        compositeDescClause.addBooleanClause(createKindClause(CompositeDescription.class));

        if (templatesParentOnly) {
            QueryTask.Query propClause = new QueryTask.Query()
                    .setTermPropertyName(CompositeDescription.FIELD_NAME_PARENT_DESCRIPTION_LINK)
                    .setTermMatchType(MatchType.WILDCARD)
                    .setTermMatchValue("*");

            propClause.occurance = Occurance.MUST_NOT_OCCUR;
            compositeDescClause.addBooleanClause(propClause);
        }

        compositeDescClause.addBooleanClause(createAnyPropertyClause(query,
                CompositeDescription.FIELD_NAME_NAME));

        // if tenant is null, do a global search, if not search in tenant
        if (tenantLinks != null && !tenantLinks.isEmpty()) {
            compositeDescClause
                    .addBooleanClause(QueryUtil.addTenantGroupAndUserClause(tenantLinks));
        }

        return compositeDescClause;
    }

    private QueryTask.Query createContainerDescClause(String query, List<String> tenantLinks,
            boolean templatesParentOnly) {
        QueryTask.Query containerDescClause = new QueryTask.Query();
        containerDescClause.addBooleanClause(createKindClause(ContainerDescription.class));
        containerDescClause.addBooleanClause(createAnyPropertyClause(query,
                ContainerDescription.FIELD_NAME_NAME, ContainerDescription.FIELD_NAME_IMAGE));

        if (templatesParentOnly) {
            QueryTask.Query propClause = new QueryTask.Query()
                    .setTermPropertyName(ContainerDescription.FIELD_NAME_PARENT_DESCRIPTION_LINK)
                    .setTermMatchType(MatchType.WILDCARD)
                    .setTermMatchValue("*");

            propClause.occurance = Occurance.MUST_NOT_OCCUR;
            containerDescClause.addBooleanClause(propClause);
        }

        // if tenant is null, do a global search, if not search in tenant
        if (tenantLinks != null && !tenantLinks.isEmpty()) {
            containerDescClause
                    .addBooleanClause(QueryUtil.addTenantGroupAndUserClause(tenantLinks));
        }

        return containerDescClause;
    }

    private List<TemplateSpec> prependOfficialResults(List<TemplateSpec> results) {
        results.sort((t1, t2) -> {
            boolean t1Official = Boolean.TRUE.equals(t1.official);
            boolean t2Official = Boolean.TRUE.equals(t2.official);
            return t1Official == t2Official ? 0 : (t2Official ? 1 : -1);
        });
        return results;
    }
}
