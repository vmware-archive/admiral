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

package com.vmware.admiral.compute.profile;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * CRUD service for managing image profiles.
 */
public class InstanceTypeService extends StatefulService {
    /**
     * Describes an image profile - configuration and mapping for a specific endpoint that allows
     * compute provisioning that is agnostic on the target endpoint type.
     */
    public static class InstanceTypeState extends ResourceState {
        public static final String FIELD_NAME_ENDPOINT_TYPE = "endpointType";

        @Documentation(description = "The endpoint type if this profile is not for a specific endpoint ")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public String endpointType;

        @Documentation(description = "Link to the endpoint this profile is associated with")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public String endpointLink;

        /**
         * Instance types provided by the particular endpoint. Keyed by global instance type
         * identifiers used to unify instance types among heterogeneous set of endpoint types.
         */
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public Map<String, InstanceTypeDescription> instanceTypeMapping;

        @Override
        public void copyTo(ResourceState target) {
            super.copyTo(target);
            if (target instanceof InstanceTypeState) {
                InstanceTypeState targetState = (InstanceTypeState) target;
                targetState.endpointLink = this.endpointLink;
                targetState.endpointType = this.endpointType;
                targetState.instanceTypeMapping = this.instanceTypeMapping;
            }
        }
    }

    public static class InstanceTypeFactoryService extends FactoryService {
        public static final String SELF_LINK = ManagementUriParts.INSTANCE_TYPE_PROFILES;

        public InstanceTypeFactoryService() {
            super(InstanceTypeState.class);
        }

        @Override
        public Service createServiceInstance() throws Throwable {
            return new InstanceTypeService();
        }

        @Override
        public void handleRequest(Operation op) {
            if (op.getAction() == Action.GET && UriUtils.hasODataExpandParamValue(op.getUri())) {
                op.nestCompletion(this::expandGetResults);
            }

            super.handleRequest(op);
        }

        private void expandGetResults(Operation op, Throwable ex) {
            if (ex != null) {
                op.fail(ex);
            }

            ServiceDocumentQueryResult body = op.getBody(ServiceDocumentQueryResult.class);
            if (body.documents != null) {
                List<DeferredResult<InstanceTypeStateExpanded>> deferredExpands = body.documents.values()
                        .stream()
                        .map((jsonInstanceType) -> {
                            InstanceTypeState instanceType = Utils.fromJson(jsonInstanceType, InstanceTypeState.class);
                            return InstanceTypeService.getExpandedState(instanceType, this);
                        }).collect(Collectors.toList());
                DeferredResult.allOf(deferredExpands).thenAccept((expandedStates) -> {
                    expandedStates.forEach((expandedState) -> {
                        body.documents.put(expandedState.documentSelfLink, expandedState);
                    });
                }).thenAccept((ignore) -> op.setBodyNoCloning(body))
                        .whenCompleteNotify(op);
            } else {
                op.complete();
            }
        }
    }

    public InstanceTypeService() {
        super(InstanceTypeState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation post) {
        processInput(post);
        post.complete();
    }

    @Override
    public void handlePut(Operation put) {
        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            logFine("Ignoring converted PUT.");
            put.complete();
            return;
        }

        InstanceTypeState newState = processInput(put);
        setState(put, newState);
        put.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        InstanceTypeState currentState = getState(patch);
        try {
            Utils.mergeWithStateAdvanced(getStateDescription(), currentState,
                    InstanceTypeState.class, patch);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            patch.fail(e);
            return;
        }
        patch.setBody(currentState);
        patch.complete();
    }

    @Override
    public void handleGet(Operation get) {
        InstanceTypeState currentState = getState(get);
        boolean doExpand = get.getUri().getQuery() != null &&
                UriUtils.hasODataExpandParamValue(get.getUri());

        if (!doExpand) {
            get.setBody(currentState).complete();
            return;
        }

        getExpandedState(currentState, this)
            .thenAccept((expandedState) ->
                get.setBodyNoCloning(expandedState))
                    .whenCompleteNotify(get);
    }


    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

    private InstanceTypeState processInput(Operation op) {
        if (!op.hasBody()) {
            throw new IllegalArgumentException("body is required");
        }
        InstanceTypeState state = op.getBody(InstanceTypeState.class);
        AssertUtil.assertNotNull(state.name, "name");
        Utils.validateState(getStateDescription(), state);
//        if (state.endpointType == null) {
//            throw new LocalizableValidationException("Endpoint type must be specified",
//                    "compute.endpoint.type.required");
//        }
        return state;
    }

    public static class InstanceTypeStateExpanded extends InstanceTypeState {
        public List<TagState> tags;
        public EndpointState endpoint;
    }

    private static DeferredResult<InstanceTypeStateExpanded> getExpandedState(InstanceTypeState instanceType, Service service) {
        InstanceTypeStateExpanded expanded = new InstanceTypeStateExpanded();
        instanceType.copyTo(expanded);

        return DeferredResult
            .allOf(
                InstanceTypeService.getDr(instanceType.endpointLink, EndpointState.class, service)
                        .thenAccept(endpointState -> expanded.endpoint = endpointState),
                InstanceTypeService.getDr(instanceType.tagLinks, TagState.class, ArrayList::new, service)
                        .thenAccept(tags -> expanded.tags = tags)).thenApply((all) -> {
                            return expanded;
                        });
    }

    private static <T> DeferredResult<T> getDr(String link, Class<T> type, Service service) {
        if (link == null) {
            return DeferredResult.completed(null);
        }
        return service.sendWithDeferredResult(Operation.createGet(service, link), type);
    }

    private static <T, C extends Collection<T>> DeferredResult<C> getDr(Collection<String> links,
            Class<T> type, Supplier<C> collectionFactory, Service service) {
        if (links == null) {
            return DeferredResult.completed(null);
        }
        return DeferredResult
                .allOf(links.stream().map(link -> getDr(link, type, service)).collect(Collectors.toList()))
                .thenApply(items -> items.stream()
                        .collect(Collectors.toCollection(collectionFactory)));
    }
}
