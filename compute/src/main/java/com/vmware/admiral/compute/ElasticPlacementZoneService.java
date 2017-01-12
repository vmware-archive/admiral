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

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState.ResourcePoolProperty;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.Utils.MergeResult;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

/**
 * Describes an elastic placement zone where the computes contributing capacity to the resource
 * pool are identified by matching tags instead of explicitly attaching them to the pool.
 *
 * <p>It is based on photon-model's {@link ResourcePoolService} with added tags to match computes
 * against. {@code ElasticPlacementZoneService} configures the underlying resource pool by
 * adding a resource query based on the defined tags to match. It is also responsible to remove
 * the tag-based query on the resource pool when an elastic placement zone is deleted.
 */
public class ElasticPlacementZoneService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.ELASTIC_PLACEMENT_ZONES;

    public static enum PlacementPolicy {

        /** place on random host. */
        DEFAULT,

        /** place on host with minimum resources - either computes or containers. */
        SPREAD,

        /** place on most loaded host until it reach the limit of resource. */
        BINPACK
    }

    /**
     * Represents a document associated with a {@link ElasticPlacementZoneService}.
     */
    public static class ElasticPlacementZoneState extends MultiTenantDocument {
        public static final String FIELD_NAME_RESOURCE_POOL_LINK = "resourcePoolLink";
        public static final String FIELD_NAME_TAG_LINKS_TO_MATCH = "tagLinksToMatch";

        @Documentation(description = "Advanced placement policy.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public PlacementPolicy placementPolicy;

        @Documentation(description = "Link to the elastic resource pool")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT)
        public String resourcePoolLink;

        @Documentation(description = "Links to tags that must be set on the computes in order"
                + " to add them to the elastic resource pool")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.LINKS)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<String> tagLinksToMatch;
    }

    public ElasticPlacementZoneService() {
        super(ElasticPlacementZoneState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation createPost) {
        handleCreateOrPut(createPost);
    }

    @Override
    public void handlePut(Operation put) {
        handleCreateOrPut(put);
    }

    @Override
    public void handleDelete(Operation delete) {
        // revert the underlying resource pool to a non-elastic one
        removeResourcePoolElasticity(getState(delete), (t) -> {
            if (t != null) {
                delete.fail(t);
            } else {
                super.handleDelete(delete);
            }
        });
    }

    @Override
    public void handlePatch(Operation patch) {
        ElasticPlacementZoneState currentState = getState(patch);

        boolean hasStateChanged = false;
        try {
            EnumSet<Utils.MergeResult> mergeResult = Utils.mergeWithStateAdvanced(
                    getStateDescription(), currentState, ElasticPlacementZoneState.class, patch);
            hasStateChanged = mergeResult.contains(MergeResult.STATE_CHANGED);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            patch.fail(e);
            return;
        }

        if (!hasStateChanged) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            patch.complete();
        } else {
            // update the underlying resource pool
            setOrRemoveResourcePoolElasticity(currentState, (t) -> {
                if (t != null) {
                    patch.fail(t);
                } else {
                    patch.complete();
                }
            });
        }
    }

    private void handleCreateOrPut(Operation op) {
        try {
            ElasticPlacementZoneState state = processInput(op);

            // configure the underlying resource pool
            setOrRemoveResourcePoolElasticity(state, (t) -> {
                if (t != null) {
                    op.fail(t);
                } else {
                    setState(op, state);
                    op.complete();
                }
            });
        } catch (Throwable t) {
            op.fail(t);
        }
    }

    private ElasticPlacementZoneState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new LocalizableValidationException("body is required", "compute.elastic.placement.body.required"));
        }
        ElasticPlacementZoneState state = op.getBody(ElasticPlacementZoneState.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }

    /**
     * Configures the elasticity of the linked resource pool.
     */
    private void setResourcePoolElasticity(ElasticPlacementZoneState epz,
            Consumer<Throwable> callback) {
        ResourcePoolState patchRpState = new ResourcePoolState();
        patchRpState.properties = EnumSet.of(ResourcePoolProperty.ELASTIC);
        patchRpState.query = generateRpQuery(epz);
        sendRequest(Operation
                .createPatch(getHost(), epz.resourcePoolLink)
                .setBody(patchRpState)
                .setCompletion((op, ex) -> callback.accept(ex)));
    }

    /**
     * Removes the elasticity of the linked resource pool.
     */
    private void removeResourcePoolElasticity(ElasticPlacementZoneState epz,
            Consumer<Throwable> callback) {
        // compose a collection update to remove the ELASTIC property
        List<Object> propertiesToRemove = new ArrayList<>();
        propertiesToRemove.add(ResourcePoolProperty.ELASTIC);
        Map<String, Collection<Object>> itemsToRemove = new HashMap<>();
        itemsToRemove.put(ResourcePoolState.FIELD_NAME_PROPERTIES, propertiesToRemove);

        sendRequest(Operation
                .createPatch(getHost(), epz.resourcePoolLink)
                .setBody(ServiceStateCollectionUpdateRequest.create(null, itemsToRemove))
                .setCompletion((op, ex) -> {
                    if (op.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        // the resource pool is already deleted, we're good
                        callback.accept(null);
                    } else {
                        callback.accept(ex);
                    }
                }));
    }

    /**
     * Sets or removes the elasticity of the linked resource pool based on whether there are tags
     * in the given EPZ state.
     */
    private void setOrRemoveResourcePoolElasticity(ElasticPlacementZoneState epz,
            Consumer<Throwable> callback) {
        if (epz.tagLinksToMatch != null && !epz.tagLinksToMatch.isEmpty()) {
            setResourcePoolElasticity(epz, callback);
        } else {
            removeResourcePoolElasticity(epz, callback);
        }
    }

    /**
     * Generates a ComputeState query based on the tag links defined in the elastic placement zone.
     *
     * The query includes computes matched by tags and computes explicitly assigned to the RP:
     * is-compute AND (match-tags OR explicitly-assigned)
     */
    private static Query generateRpQuery(ElasticPlacementZoneState epz) {
        Query.Builder tagQueryBuilder = Query.Builder.create();
        for (String tagLink : epz.tagLinksToMatch) {
            // all tagLinksToMatch must be set on the compute
            tagQueryBuilder.addCollectionItemClause(ResourceState.FIELD_NAME_TAG_LINKS, tagLink);
        }

        Query assignmentClause = Query.Builder.create()
                .addClause(tagQueryBuilder.build().setOccurance(Occurance.SHOULD_OCCUR))
                .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK, epz.resourcePoolLink,
                        Occurance.SHOULD_OCCUR)
                .build();
        Query kindClause = Query.Builder.create().addKindFieldClause(ComputeState.class).build();

        Query epzQuery = Query.Builder.create().addClauses(assignmentClause, kindClause).build();
        return epzQuery;
    }
}
