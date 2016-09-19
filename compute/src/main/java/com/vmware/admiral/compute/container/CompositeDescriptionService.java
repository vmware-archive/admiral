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

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;
import static com.vmware.admiral.common.util.PropertyUtils.mergeProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceDocumentTemplateUtil;
import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.container.CompositeComponentRegistry.ComponentMeta;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription.Status;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.content.Binding;
import com.vmware.admiral.compute.content.Binding.BindingPlaceholder;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Describes multiple container instances deployed at the same time. It represents a template
 * definition of related services or an application.
 */
public class CompositeDescriptionService extends StatefulService {
    public static final String SELF_LINK = ManagementUriParts.COMPOSITE_DESC;
    private static final String URI_PARAM_IMAGE_LINKS = "descriptionImages";

    public static class CompositeDescription extends
            com.vmware.admiral.service.common.MultiTenantDocument {

        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_DESCRIPTION_LINKS = "descriptionLinks";
        public static final String FIELD_NAME_PARENT_DESCRIPTION_LINK = "parentDescriptionLink";

        /** Name of composite description */
        public String name;
        /** Status of the composite description (PUBLISHED) */
        public Status status;
        /** Last published time in milliseconds */
        public Long lastPublished;
        /** Link to the parent composite description */
        public String parentDescriptionLink;
        /** List of all ContainerDescriptions as part of this composition description */
        public List<String> descriptionLinks;
        /** Custom properties. */
        public Map<String, String> customProperties;
        /** Bindings */
        public List<Binding.ComponentBinding> bindings;

        // mirror com.vmware.vcac.composition.domain.PublishStatus
        public static enum Status {
            DRAFT,
            PUBLISHED,
            RETIRED
        }
    }

    public static class CompositeDescriptionExpanded extends CompositeDescription {
        public List<ComponentDescription> componentDescriptions;

        private static CompositeDescriptionExpanded expand(CompositeDescription cd) {
            CompositeDescriptionExpanded cdExpanded = new CompositeDescriptionExpanded();
            cdExpanded.name = cd.name;
            cdExpanded.status = cd.status;
            cdExpanded.lastPublished = cd.lastPublished;
            cdExpanded.parentDescriptionLink = cd.parentDescriptionLink;
            cdExpanded.documentSelfLink = cd.documentSelfLink;
            cdExpanded.customProperties = cd.customProperties;
            cdExpanded.descriptionLinks = cd.descriptionLinks;
            cdExpanded.tenantLinks = cd.tenantLinks;
            cdExpanded.componentDescriptions = new ArrayList<>();
            cdExpanded.bindings = cd.bindings;
            return cdExpanded;
        }
    }

    public static class CompositeDescriptionImages extends MultiTenantDocument {
        public Map<String, String> descriptionImages;
    }

    public CompositeDescriptionService() {
        super(CompositeDescription.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleGet(Operation get) {
        CompositeDescription compositeDescription = getState(get);
        String query = get.getUri().getQuery();

        boolean doExpand = query != null && UriUtils.hasODataExpandParamValue(get.getUri());

        boolean descriptionImages = query != null && query.contains(URI_PARAM_IMAGE_LINKS);

        CompositeDescriptionExpanded cdExpanded = CompositeDescriptionExpanded
                .expand(compositeDescription);

        if (descriptionImages) {
            retrieveDescriptionImages(compositeDescription.descriptionLinks, get);
        } else if (!doExpand) {
            get.setBody(compositeDescription).complete();
        } else if (cdExpanded.descriptionLinks.isEmpty()) {
            get.setBody(cdExpanded).complete();
        } else {
            retrieveComponentDescriptions(compositeDescription, cdExpanded, get);
        }
    }

    @Override
    public void handleCreate(Operation startPost) {
        if (!checkForBody(startPost)) {
            return;
        }
        try {
            CompositeDescription state = startPost.getBody(CompositeDescription.class);
            logFine("Initial name is %s", state.name);
            validateStateOnStart(state);
            startPost.complete();
        } catch (Throwable e) {
            logSevere(e);
            startPost.fail(e);
        }
    }

    private void validateStateOnStart(CompositeDescription state) {
        assertNotNull(state.name, "name");
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        CompositeDescriptionExpanded body = put.getBody(CompositeDescriptionExpanded.class);
        validateStateOnStart(body);
        if (isExpanded(body)) {
            List<Operation> update = body.componentDescriptions
                    .stream()
                    .map(cd -> Operation
                            .createPut(this, cd.component.documentSelfLink)
                            .setBody(cd.component))
                    .collect(Collectors.toList());
            // The component descriptions may have changed. We need to persist them, so that the
            // other services can pick up the evaluated descriptions too
            OperationJoin.create(update).setCompletion((ops, failures) -> {
                if (failures != null) {
                    put.fail(failures.values().iterator().next());
                    return;
                }
                body.componentDescriptions = null;
                performPut(put, body);
            }).sendWith(this);
        } else {
            performPut(put, body);
        }
    }

    private void performPut(Operation put, CompositeDescription putBody) {
        try {
            this.setState(put, putBody);
            put.setBody(putBody).complete();
        } catch (Throwable e) {
            put.fail(e);
        }
    }

    private boolean isExpanded(CompositeDescriptionExpanded body) {
        boolean hasComponentDescriptionObjects = body.componentDescriptions != null
                && !body.componentDescriptions.isEmpty();

        return hasComponentDescriptionObjects;
    }

    @Override
    public void handlePatch(Operation patch) {
        CompositeDescription currentState = getState(patch);
        CompositeDescription patchBody = patch.getBody(CompositeDescription.class);

        ServiceDocumentDescription docDesc = getDocumentTemplate().documentDescription;
        String currentSignature = Utils.computeSignature(currentState, docDesc);

        currentState.name = mergeProperty(currentState.name, patchBody.name);
        currentState.status = mergeProperty(currentState.status, patchBody.status);
        currentState.lastPublished = mergeProperty(currentState.lastPublished,
                patchBody.lastPublished);
        currentState.descriptionLinks = mergeProperty(currentState.descriptionLinks,
                patchBody.descriptionLinks);
        currentState.customProperties = mergeCustomProperties(
                currentState.customProperties, patchBody.customProperties);

        String newSignature = Utils.computeSignature(currentState, docDesc);

        // if the signature hasn't change we shouldn't modify the state
        if (currentSignature.equals(newSignature)) {
            currentState = null;
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        }

        patch.setBody(currentState).complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        CompositeDescription template = (CompositeDescription) super.getDocumentTemplate();

        ServiceDocumentTemplateUtil.indexCustomProperties(template);
        ServiceDocumentTemplateUtil.indexProperty(template,
                CompositeDescription.FIELD_NAME_DESCRIPTION_LINKS);

        // FIXME this causes issues when the text contains non alpha characters
        // ServiceDocumentTemplateUtil.indexTextProperty(template,
        // CompositeDescription.FIELD_NAME_NAME);

        template.name = "name (string)";
        template.status = Status.PUBLISHED;
        template.lastPublished = System.currentTimeMillis();
        template.descriptionLinks = Arrays.asList("containerDescriptionLink (string)");
        template.customProperties = new HashMap<>(1);
        template.customProperties.put("propKey string", "customPropertyValue string");
        template.bindings = new ArrayList<>();
        Binding.ComponentBinding cb = new Binding.ComponentBinding("component",
                new ArrayList<>(Arrays.asList(new Binding(Arrays.asList("field"), "${expr}",
                        new BindingPlaceholder("expr", "1")))));
        template.bindings.add(cb);
        return template;
    }

    private List<String> getComponentDescriptionLinks(CompositeDescription compositeDescription) {
        List<String> descriptionLinks = new ArrayList<String>(
                compositeDescription.descriptionLinks);

        return descriptionLinks;
    }

    private void retrieveDescriptionImages(List<String> descriptionLinks, Operation op) {
        if (descriptionLinks == null || descriptionLinks.isEmpty()) {
            return;
        }

        QueryTask queryTask = QueryUtil.buildQuery(ContainerDescription.class, true);

        QueryUtil.addExpandOption(queryTask);

        QueryUtil.addListValueClause(queryTask,
                ContainerDescription.FIELD_NAME_SELF_LINK,
                descriptionLinks);

        Map<String, String> images = new HashMap<>();
        CompositeDescriptionImages result = new CompositeDescriptionImages();

        new ServiceDocumentQuery<>(getHost(), ContainerDescription.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        op.fail(r.getException());
                    } else if (r.hasResult()) {
                        images.put(r.getResult().documentSelfLink, r.getResult().image);
                        result.tenantLinks = r.getResult().tenantLinks;
                    } else {
                        result.descriptionImages = images;
                        op.setBody(result).complete();
                    }
                });
    }

    private void retrieveComponentDescriptions(CompositeDescription compositeDescription,
            CompositeDescriptionExpanded cdExpanded,
            Operation get) {
        List<String> componentDescriptionLinks = getComponentDescriptionLinks(compositeDescription);

        QueryTask componentDescriptionQueryTask = new QueryTask();
        componentDescriptionQueryTask.querySpec = new QueryTask.QuerySpecification();
        componentDescriptionQueryTask.taskInfo.isDirect = true;
        componentDescriptionQueryTask.documentExpirationTimeMicros = ServiceDocumentQuery
                .getDefaultQueryExpiration();

        QueryUtil.addExpandOption(componentDescriptionQueryTask);

        QueryUtil.addListValueClause(componentDescriptionQueryTask,
                ServiceDocument.FIELD_NAME_SELF_LINK,
                componentDescriptionLinks);

        sendRequest(Operation
                .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(componentDescriptionQueryTask)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        get.fail(ex);
                    } else {
                        QueryTask resultTask = o.getBody(QueryTask.class);
                        ServiceDocumentQueryResult result = resultTask.results;

                        if (result == null || result.documents == null) {
                            return;
                        }

                        List<ComponentDescription> componentDescriptions = new ArrayList<>();
                        result.documents.forEach((link, document) -> {
                            ComponentMeta meta = CompositeComponentRegistry
                                    .metaByDescriptionLink(link);
                            ResourceState description = Utils.fromJson(document,
                                    meta.descriptionClass);
                            if (description != null) {
                                ComponentDescription cd = new ComponentDescription(
                                        description,
                                        meta.resourceType,
                                        description.name,
                                        getBindingsForComponent(description.name, cdExpanded));
                                componentDescriptions.add(cd);
                            } else {
                                logWarning("Unexpected result type: %s", link);
                            }
                        });

                        cdExpanded.componentDescriptions = componentDescriptions;
                        get.setBody(cdExpanded).complete();
                    }
                }));
    }

    private List<Binding> getBindingsForComponent(String componentName,
            CompositeDescriptionExpanded compositeDescriptionExpanded) {
        if (compositeDescriptionExpanded.bindings == null) {
            return Collections.emptyList();
        }
        return compositeDescriptionExpanded.bindings.stream()
                .filter(cb -> cb.componentName.equals(componentName))
                .flatMap(cb -> cb.bindings.stream()).collect(Collectors.toList());
    }
}
