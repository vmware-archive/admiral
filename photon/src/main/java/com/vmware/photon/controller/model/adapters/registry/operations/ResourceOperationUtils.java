/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.adapters.registry.operations;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Various {@link ResourceOperationSpec} related Utilities.
 */
public class ResourceOperationUtils {
    public static final String SCRIPT_ENGINE_NAME_JS = "js";
    public static final String SCRIPT_CONTEXT_RESOURCE = "resource";

    public static final String COMPUTE_KIND = Utils.buildKind(ComputeState.class);
    public static final String NETWORK_KIND = Utils.buildKind(NetworkState.class);

    public static enum TargetCriteria {
        RESOURCE_POWER_STATE_ON("resource.powerState.equals('ON')"),

        // ComputeProperties.CUSTOM_PROP_COMPUTE_HAS_SNAPSHOTS = "__hasSnapshot". So "__hasSnapshot"
        // is used here. Any modification in that custom property requires manual changes here as
        // well.
        RESOURCE_HAS_SNAPSHOTS("resource.customProperties != null && " +
                " (resource.customProperties.get('__hasSnapshot') != null) && " +
                "('true' == (resource.customProperties.get('__hasSnapshot')).toLowerCase())");

        private final String criteria;

        private TargetCriteria(String criteria) {
            this.criteria = criteria;
        }

        public String getCriteria() {
            return this.criteria;
        }
    }

    /**
     * Lookup for {@link ResourceOperationSpec} by given {@code endpointType}, {@code resourceType}
     * and {@code operation}
     *
     * @param host
     *            host to use to create operation
     * @param refererURI
     *            the referer to use when send the operation
     * @param endpointType
     *            the resource's endpoint type
     * @param resourceType
     *            the resource type
     * @param operation
     *            the operation
     * @return
     */
    public static DeferredResult<ResourceOperationSpec> lookUpByEndpointType(
            ServiceHost host,
            URI refererURI,
            String endpointType,
            ResourceType resourceType,
            String operation) {

        return lookUp(host, refererURI, endpointType, resourceType, operation)
                .thenApply(specs -> specs.isEmpty() ? null : specs.iterator().next());
    }

    /**
     * Lookup for {@link ResourceOperationSpec} by given {@code endpointLink}, {@code resourceType}
     * and {@code operation}
     *
     * @param host
     *            host to use to create operation
     * @param refererURI
     *            the referer to use when send the operation
     * @param endpointLink
     *            the endpoint link of the resource
     * @param resourceType
     *            the resource type
     * @param operation
     *            the operation
     * @return
     */
    public static DeferredResult<ResourceOperationSpec> lookUpByEndpointLink(
            ServiceHost host,
            URI refererURI,
            String endpointLink,
            ResourceType resourceType,
            String operation) {

        return host.sendWithDeferredResult(Operation.createGet(host, endpointLink)
                .setReferer(refererURI))
                .thenCompose(o -> lookUpByEndpointType(host, refererURI,
                        (o.getBody(EndpointState.class)).endpointType,
                        resourceType, operation));
    }

    /**
     * Lookup for {@link ResourceOperationSpec} by given {@code resourceState} and {@code operation}
     *
     * @param host
     *            host to use to create operation
     * @param refererURI
     *            the referer to use when send the operation
     * @param resourceState
     *            the resource state specialization for which to lookup the spec
     * @param operation
     *            the operation
     * @return
     */
    public static <T extends ResourceState> DeferredResult<List<ResourceOperationSpec>> lookupByResourceState(
            ServiceHost host,
            URI refererURI,
            T resourceState,
            String operation) {
        AssertUtil.assertNotNull(resourceState, "'resourceState' must be set.");
        String endpointLink;
        ResourceType resourceType;
        if (resourceState instanceof ComputeState) {
            ComputeState compute = (ComputeState) resourceState;
            endpointLink = compute.endpointLink;
            resourceType = ResourceType.COMPUTE;
        } else if (resourceState instanceof NetworkState) {
            NetworkState network = (NetworkState) resourceState;
            endpointLink = network.endpointLink;
            resourceType = ResourceType.NETWORK;
        } else {
            throw new IllegalArgumentException("Unsupported resource state: "
                    + resourceState.getClass().getName());
        }
        AssertUtil.assertNotNull(endpointLink, "'endpointLink' must be set.");

        return host.sendWithDeferredResult(
                Operation.createGet(host, endpointLink).setReferer(refererURI),
                EndpointState.class)
                .thenCompose(ep -> lookUp(
                        host, refererURI, (ep).endpointType, resourceType, operation));
    }

    /**
     * Evaluates provided {@code spec}'s target criteria against the specified {@code resourceState}
     * and returns if the {@link ResourceOperationSpec} is applicable for the given
     * {@link ResourceState}
     *
     * @param resourceState
     *            the resource state for which to check whether given {@code spec} is available
     * @param spec
     *            the {@link ResourceOperationSpec} which to check whether is available for the
     *            given {@code resourceState}
     * @return {@literal true} only in case there is targetCriteria, and the targetCriteria is
     *         evaluated to {@literal true} for the given {@code resourceState}
     */
    public static boolean isAvailable(ResourceState resourceState, ResourceOperationSpec spec) {
        AssertUtil.assertNotNull(spec, "'spec' must be set.");
        if (spec.targetCriteria == null) {
            return true;
        }

        ScriptEngine engine = new ScriptEngineManager().getEngineByName(SCRIPT_ENGINE_NAME_JS);

        if (resourceState != null) {
            // Clone original object to avoid changing props of original object from vulnerable
            // targetCriteria
            ResourceState clone = Utils.cloneObject(resourceState);
            engine.getBindings(ScriptContext.ENGINE_SCOPE).put(SCRIPT_CONTEXT_RESOURCE, clone);
        }
        try {
            Object res = engine.eval(spec.targetCriteria);
            if (res instanceof Boolean) {
                return ((Boolean) res).booleanValue();
            } else {
                Utils.log(ResourceOperationUtils.class, "isAvailable",
                        Level.WARNING,
                        "Expect boolean result when evaluate targetCriteria \"%s\" of "
                                + "endpointType: %s, resourceType: %s, operation: %s, "
                                + "adapterReference: %s. Result: %s",
                        spec.targetCriteria,
                        spec.endpointType, spec.resourceType, spec.operation,
                        spec.adapterReference, res);
            }
        } catch (ScriptException e) {
            Utils.log(ResourceOperationUtils.class, "isAvailable",
                    Level.SEVERE,
                    "Cannot evaluate targetCriteria '%s' of "
                            + "endpointType: %s, resourceType: %s, operation: %s, "
                            + "adapterReference: %s. Cause: %s",
                    spec.targetCriteria,
                    spec.endpointType, spec.resourceType, spec.operation, spec.adapterReference,
                    Utils.toString(e));

        }
        return false;
    }

    /**
     * A generic utility method to register any Day 2 Operation service/adapter with the framework
     * as a ResourceOperationSpecService. It accepts a list of {@code specs} which a service can
     * handle as input and submits them to the ResourceOperationSpecService's Factory. This call
     * should generally be part of handleStart method of the adapter/service, preferably near the
     * end after any service specification configuration settings.
     *
     * @param service
     *            the resourceOperation service/adapter
     * @param handler
     *            the operation completion handler for making the success/failure actions
     * @param specs
     *            list of intended the ResourceOperationSpec's to register with the service
     */
    public static void registerResourceOperation(Service service, CompletionHandler handler,
            ResourceOperationSpec... specs) {
        if (specs == null || specs.length == 0) {
            service.getHost().log(Level.FINE,
                    "No ResourceOperationSpec to register by %s",
                    service.getSelfLink());
            handler.handle(null, null);
            return;
        }

        service.getHost().registerForServiceAvailability((op, ex) -> {
            if (ex != null) {
                service.getHost().log(Level.SEVERE, Utils.toString(ex));
                handler.handle(op, ex);
            } else {
                List<Operation> operations = Arrays.stream(specs)
                        .map(spec -> createOperation(service, spec))
                        .collect(Collectors.toList());

                JoinedCompletionHandler jh = (ops, err) -> {
                    if (err != null) {
                        service.getHost().log(Level.SEVERE, "Error: %s", Utils.toString(err));
                        handler.handle(ops.values().iterator().next(),
                                err.values().iterator().next());
                    } else {
                        service.getHost().log(Level.FINE,
                                "Successfully registered operations.");
                        handler.handle(null, null);
                    }
                };
                OperationJoin.create(operations).setCompletion(jh).sendWith(service);
            }
        }, ResourceOperationSpecService.FACTORY_LINK);
    }

    /**
     * Lookup for {@link ResourceOperationSpec}s by given {@code endpointType}, {@code resourceType}
     * and optionally {@code operation}
     * <p>
     * If operation not specified then return all resource operation specs for the given
     * {@code endpointType} and {@code resourceType}
     *
     * @param host
     *            host to use to create operation
     * @param refererURI
     *            the referer to use when send the operation
     * @param endpointType
     *            the resource's endpoint type
     * @param resourceType
     *            the resource type
     * @param operation
     *            optional operation id argument
     * @return
     */
    private static DeferredResult<List<ResourceOperationSpec>> lookUp(
            ServiceHost host,
            URI refererURI,
            String endpointType,
            ResourceType resourceType,
            String operation) {

        Query.Builder builder = Query.Builder.create()
                .addKindFieldClause(ResourceOperationSpec.class)
                .addFieldClause(
                        ResourceOperationSpec.FIELD_NAME_ENDPOINT_TYPE,
                        endpointType)
                .addFieldClause(
                        ResourceOperationSpec.FIELD_NAME_RESOURCE_TYPE,
                        resourceType);
        if (operation != null) {
            builder.addFieldClause(
                    ResourceOperationSpec.FIELD_NAME_OPERATION,
                    operation);
        }
        Query query = builder.build();

        QueryTop<ResourceOperationSpec> top = new QueryTop<>(
                host, query, ResourceOperationSpec.class, null);
        if (operation != null) {
            // resource operation spec id and selfLink are built from the endpoint type, resource
            // type and operation id, so the query result is guaranteed to return at most 1 element
            top.setMaxResultsLimit(1);
        }
        top.setReferer(refererURI);
        return top.collectDocuments(Collectors.toList());
    }

    private static Operation createOperation(Service service, ResourceOperationSpec spec) {
        service.getHost().log(Level.FINE,
                "Going to register Resource Operation name=%s, operation='%s'",
                spec.name, spec.operation);
        return Operation.createPost(service, ResourceOperationSpecService.FACTORY_LINK)
                .setBody(spec);
    }

}
