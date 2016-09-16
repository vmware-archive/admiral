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

package com.vmware.admiral.compute;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Configuration service that simplifies CRUD operations with elastic placement zones by handling
 * them together with the underlying resource pool instances.
 *
 * <p>Here are the supported actions and their behavior:<ul>
 *
 * <li>{@code GET}: Use the service URL to get all elastic placement zones.
 * Append the resource pool link to this service URL to retrieve the
 * {@link ElasticPlacementZoneConfigurationState} for a single existing resource pool. For example:
 * {@code http://host:port/resources/elastic-placement-zones-config/resources/pools/pool-1}.
 *
 * <li>{@code POST}: Post a valid {@link ElasticPlacementZoneConfigurationState} with no document
 * self links to create a resource pool and optionally a corresponding elastic placement zone. The
 * returned body contains the created {@link ElasticPlacementZoneConfigurationState}.
 *
 * <li>{@code PATCH}: Send a valid {@link ElasticPlacementZoneConfigurationState} to update the
 * resource pool and optionally the elastic placement zone. A {@code PATCH} is done for the
 * resource pool and a {@code PUT} is done for the elastic placement zone.
 * The response body contains the full updated state unless the resource pool is not changed.
 * In this case, the {@code resourcePoolState} field is {@code null}.
 *
 * <li>{@code DELETE}: Send a valid {@link ElasticPlacementZoneConfigurationState} to delete the
 * resource pool and optionally the elastic placement zone associated with it.
 * </ul>
 */
public class ElasticPlacementZoneConfigurationService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.ELASTIC_PLACEMENT_ZONE_CONFIGURATION;

    /**
     * State used for request and response bodies.
     */
    public static class ElasticPlacementZoneConfigurationState extends ServiceDocument {
        public ResourcePoolState resourcePoolState;
        public ElasticPlacementZoneState epzState;
    }

    public ElasticPlacementZoneConfigurationService() {
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void handleGet(Operation get) {
        // resolve the link to the requested resource pool
        String resourcePoolLink = null;
        if (get.getUri().getPath().startsWith(SELF_LINK)) {
            resourcePoolLink = get.getUri().getPath().substring(SELF_LINK.length());
        }

        if (resourcePoolLink == null || resourcePoolLink.isEmpty()) {
            // return the configuration state for all elastic placement zones
            doGetAll(get, UriUtils.hasODataExpandParamValue(get.getUri()));
        } else {
            // return the configuration state for the requested resource pool
            doGet(get, resourcePoolLink);
        }
    }

    @Override
    public void handlePost(Operation post) {
        ElasticPlacementZoneConfigurationState state = validateState(post);
        doCreate(post, state);
    }

    @Override
    protected void handleDeleteCompletion(Operation delete) {
        ElasticPlacementZoneConfigurationState state = validateState(delete);
        doDelete(delete, state);
    }

    @Override
    public void handlePatch(Operation patch) {
        ElasticPlacementZoneConfigurationState state = validateState(patch);
        doUpdate(patch, state);
    }

    private ElasticPlacementZoneConfigurationState validateState(Operation op) {
        if (!op.hasBody()) {
            throw new IllegalArgumentException("Body is required");
        }

        ElasticPlacementZoneConfigurationState state = op.getBody(
                ElasticPlacementZoneConfigurationState.class);
        if (state.resourcePoolState == null) {
            throw new IllegalArgumentException("Resource pool state is required");
        }
        return state;
    }

    private void doGetAll(Operation originalOp, boolean expand) {
        // get all RPs and EPZs in parallel
        List<Operation> operationsToJoin = new ArrayList<>();

        URI rpFactoryUri = UriUtils.buildUri(getHost(), ResourcePoolService.FACTORY_LINK);
        operationsToJoin.add(Operation
                .createGet(expand ? UriUtils.buildExpandLinksQueryUri(rpFactoryUri) : rpFactoryUri)
                .setReferer(getUri()));

        if (expand) {
            URI epzFactoryUri = UriUtils.buildUri(getHost(), ElasticPlacementZoneService.FACTORY_LINK);
            operationsToJoin.add(Operation
                    .createGet(UriUtils.buildExpandLinksQueryUri(epzFactoryUri))
                    .setReferer(getUri()));
        }

        OperationJoin.create(operationsToJoin)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        originalOp.fail(exs.values().iterator().next());
                        return;
                    }

                    // if no expanding the content, just return the RP links
                    if (!expand) {
                        originalOp.setBody(ops.get(operationsToJoin.get(0).getId()).getBodyRaw());
                        originalOp.complete();
                        return;
                    }

                    // extract RPs and EPZs from the response
                    Map<String, ResourcePoolState> rpByLink = QueryUtil.extractQueryResult(
                            ops.get(operationsToJoin.get(0).getId())
                                    .getBody(ServiceDocumentQueryResult.class),
                            ResourcePoolState.class);
                    Map<String, ElasticPlacementZoneState> epzByLink = QueryUtil.extractQueryResult(
                            ops.get(operationsToJoin.get(1).getId())
                                    .getBody(ServiceDocumentQueryResult.class),
                            ElasticPlacementZoneState.class);

                    // join EPZs to RPs
                    Map<String, ElasticPlacementZoneConfigurationState> foundStates = new HashMap<>();
                    rpByLink.values().forEach(rp -> {
                        ElasticPlacementZoneConfigurationState state =
                                new ElasticPlacementZoneConfigurationState();
                        state.documentSelfLink = rp.documentSelfLink;
                        state.resourcePoolState = rp;
                        foundStates.put(state.documentSelfLink, state);
                    });

                    epzByLink.values().forEach(epz -> {
                        ElasticPlacementZoneConfigurationState state =
                                foundStates.get(epz.resourcePoolLink);
                        if (state != null) {
                            state.epzState = epz;
                        }
                    });

                    // create a ServiceDocumentQueryResult to return
                    ServiceDocumentQueryResult resultToReturn = QueryUtil
                            .createQueryResult(foundStates.values());
                    originalOp.setBody(resultToReturn);
                    originalOp.complete();
                })
                .sendWith(getHost());
    }

    private void doGet(Operation originalOp, String resourcePoolLink) {
        // create a get operation for the resource pool
        Operation getRpOp = Operation
                .createGet(getHost(), resourcePoolLink)
                .setReferer(getUri());

        // create a query for the elastic placement zone based on the resource pool link
        Query epzQuery = Query.Builder.create()
                .addKindFieldClause(ElasticPlacementZoneState.class)
                .addFieldClause(ElasticPlacementZoneState.FIELD_NAME_RESOURCE_POOL_LINK,
                        resourcePoolLink)
                .build();
        QueryTask epzQueryTask = QueryTask.Builder.createDirectTask()
                .setQuery(epzQuery)
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();
        Operation queryEpzOp = Operation
                .createPost(getHost(), ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(epzQueryTask)
                .setReferer(getUri());

        // execute both operations in parallel
        OperationJoin.create(getRpOp, queryEpzOp)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        originalOp.fail(exs.values().iterator().next());
                        return;
                    }

                    // populate the resource pool state into the response
                    ElasticPlacementZoneConfigurationState state =
                            new ElasticPlacementZoneConfigurationState();
                    state.resourcePoolState = ops.get(getRpOp.getId()).getBody(
                            ResourcePoolState.class);

                    // populate the elastic placement zone state into the response
                    QueryTask returnedQueryTask = ops.get(queryEpzOp.getId())
                            .getBody(QueryTask.class);
                    if (returnedQueryTask.results != null
                            && returnedQueryTask.results.documents != null
                            && !returnedQueryTask.results.documents.isEmpty()) {
                        if (returnedQueryTask.results.documents.size() > 1) {
                            logWarning(
                                    "%d elastic placement zones found for resource pool '%s'",
                                    returnedQueryTask.results.documents.size(),
                                    state.resourcePoolState.documentSelfLink);
                        }
                        state.epzState = Utils.fromJson(
                                returnedQueryTask.results.documents.values().iterator().next(),
                                ElasticPlacementZoneState.class);
                    }

                    originalOp.setBody(state);
                    originalOp.complete();
                })
                .sendWith(getHost());
    }

    private void doCreate(Operation originalOp, ElasticPlacementZoneConfigurationState state) {
        // create post operation for the resource pool
        Operation createRpOp = Operation
                .createPost(getHost(), ResourcePoolService.FACTORY_LINK)
                .setBody(state.resourcePoolState)
                .setReferer(getUri());
        Operation[] createEpzOpHolder = { null };

        // create operation sequence
        OperationSequence operations = OperationSequence
                .create(createRpOp)
                .abortOnFirstFailure()
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        originalOp.fail(exs.values().iterator().next());
                        return;
                    }
                    state.resourcePoolState = ops.values().iterator().next()
                            .getBody(ResourcePoolState.class);

                    if (createEpzOpHolder[0] != null) {
                        state.epzState.resourcePoolLink = state.resourcePoolState.documentSelfLink;
                        createEpzOpHolder[0].setBody(state.epzState);
                    } else {
                        originalOp.complete();
                    }
                });

        // add an EPZ creation task to the sequence, if specified in the input state
        if (state.epzState != null && state.epzState.tagLinksToMatch != null
                && !state.epzState.tagLinksToMatch.isEmpty()) {
            createEpzOpHolder[0] = Operation
                    .createPost(getHost(), ElasticPlacementZoneService.FACTORY_LINK)
                    .setReferer(getUri());
            operations = operations.next(createEpzOpHolder[0])
                    .setCompletion(false, (ops, exs) -> {
                        if (exs != null) {
                            originalOp.fail(exs.values().iterator().next());
                            return;
                        }
                        state.epzState = ops.values().iterator().next()
                                .getBody(ElasticPlacementZoneState.class);
                        originalOp.complete();
                    });
        }

        // execute the sequence
        operations.sendWith(getHost());
    }

    private void doDelete(Operation originalOp, ElasticPlacementZoneConfigurationState state) {
        // validation
        if (state.resourcePoolState.documentSelfLink == null) {
            originalOp.fail(new IllegalArgumentException("Resource pool link is required"));
            return;
        }

        // create delete operations for EPZ and RP
        List<Operation> deleteOps = new ArrayList<>();
        if (state.epzState != null && state.epzState.documentSelfLink != null) {
            deleteOps.add(Operation
                    .createDelete(getHost(), state.epzState.documentSelfLink)
                    .setReferer(getUri()));
        }
        deleteOps.add(Operation
                .createDelete(getHost(), state.resourcePoolState.documentSelfLink)
                .setReferer(getUri()));

        // execute them in sequence - EPZ deletion (if any) has to be completed first
        OperationSequence opSequence = OperationSequence
                .create(deleteOps.get(0))
                .abortOnFirstFailure();
        for (int i = 1; i < deleteOps.size(); i++) {
            opSequence = opSequence.next(deleteOps.get(i));
        }

        opSequence.setCompletion((ops, exs) -> {
            // single completion handler for all the operations
            if (exs != null) {
                originalOp.fail(exs.values().iterator().next());
                return;
            }
            originalOp.complete();
        }).sendWith(getHost());
    }

    private void doUpdate(Operation originalOp, ElasticPlacementZoneConfigurationState state) {
        // validation
        if (state.resourcePoolState.documentSelfLink == null) {
            originalOp.fail(new IllegalArgumentException("Resource pool link is required"));
            return;
        }

        List<Operation> updateOps = new ArrayList<>();

        // patch the resource pool
        updateOps.add(Operation
                .createPatch(getHost(), state.resourcePoolState.documentSelfLink)
                .setBody(state.resourcePoolState)
                .setReferer(getUri()));

        if (state.epzState != null) {
            if (state.epzState.tagLinksToMatch == null) {
                state.epzState.tagLinksToMatch = new HashSet<>();
            }
            // use post for the elastic placement zone (will be translated to put if exists)
            updateOps.add(Operation
                    .createPost(getHost(), ElasticPlacementZoneService.FACTORY_LINK)
                    .setBody(state.epzState)
                    .setReferer(getUri()));
        }

        // update both in parallel
        OperationJoin.create(updateOps)
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        originalOp.fail(exs.values().iterator().next());
                        return;
                    }

                    // only return the updated RP state if changed, return null otherwise
                    Operation rpOp = ops.get(updateOps.get(0).getId());
                    state.resourcePoolState = rpOp.getStatusCode() == Operation.STATUS_CODE_OK
                            ? rpOp.getBody(ResourcePoolState.class) : null;

                    // always return the updated EPZ state, if any
                    if (updateOps.size() > 1 && state.epzState.documentSelfLink == null) {
                        state.epzState = ops.get(updateOps.get(1).getId())
                                .getBody(ElasticPlacementZoneState.class);
                    }
                    originalOp.complete();
                })
                .sendWith(getHost());
    }
}
