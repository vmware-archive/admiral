/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.resources;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class StorageDescriptionService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/storage-descriptions";

    public StorageDescriptionService() {
        super(StorageDescription.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    /**
     * This class represents the document state associated with a
     * {@link com.vmware.photon.controller.model.resources.StorageDescriptionService} task.
     */
    public static class StorageDescription extends ResourceState {
        public static final String FIELD_NAME_ADAPTER_REFERENCE = "adapterManagementReference";
        public static final String FIELD_NAME_COMPUTE_HOST_LINK = "computeHostLink";

        /**
         * Type of Storage.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String type;

        /**
         * Self-link to the AuthCredentialsService used to access this compute host.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String authCredentialsLink;

        /**
         * The pool which this resource is a part of.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String resourcePoolLink;

        /**
         * Reference to the management endpoint of the compute provider.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI adapterManagementReference;

        /**
         * Total capacity of the storage.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Long capacityBytes;

        /**
         * Reference to compute host instance.
         */
        public String computeHostLink;

        /**
         * Link to the cloud account endpoint the disk belongs to.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_7)
        public String endpointLink;

        /**
         * Indicates whether this storage description supports encryption or not.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_16)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Boolean supportsEncryption;

        @Override
        public void copyTo(ResourceState target) {
            super.copyTo(target);
            if (target instanceof StorageDescription) {
                StorageDescription targetState = (StorageDescription) target;
                targetState.type = this.type;
                targetState.authCredentialsLink = this.authCredentialsLink;
                targetState.resourcePoolLink = this.resourcePoolLink;
                targetState.adapterManagementReference = this.adapterManagementReference;
                targetState.capacityBytes = this.capacityBytes;
                targetState.computeHostLink = this.computeHostLink;
                targetState.endpointLink = this.endpointLink;
                targetState.supportsEncryption = this.supportsEncryption;
            }
        }
    }

    /**
     * Expanded storage description along with its resource group states.
     */
    public static class StorageDescriptionExpanded extends StorageDescription {
        /**
         * Set of resource group states to which this storage description belongs to.
         */
        public Set<ResourceGroupState> resourceGroupStates;

        public static URI buildUri(URI sdUri) {
            return UriUtils.buildExpandLinksQueryUri(sdUri);
        }
    }

    @Override
    public void handleStart(Operation start) {
        try {
            processInput(start);
            start.complete();
        } catch (Throwable t) {
            start.fail(t);
        }
    }

    @Override
    public void handlePut(Operation put) {
        try {
            StorageDescription returnState = processInput(put);
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private StorageDescription processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        StorageDescription state = op.getBody(StorageDescription.class);
        Utils.validateState(getStateDescription(), state);
        if (state.name == null) {
            throw new IllegalArgumentException("name is required.");
        }
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        StorageDescription currentState = getState(patch);
        Function<Operation, Boolean> customPatchHandler = t -> {
            boolean hasStateChanged = false;
            StorageDescription patchBody = patch.getBody(StorageDescription.class);
            if (patchBody.creationTimeMicros != null && currentState.creationTimeMicros == null &&
                    currentState.creationTimeMicros != patchBody.creationTimeMicros) {
                currentState.creationTimeMicros = patchBody.creationTimeMicros;
                hasStateChanged = true;
            }
            return hasStateChanged;
        };
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                StorageDescription.class, customPatchHandler);
    }

    @Override
    public void handleGet(Operation get) {
        StorageDescription currentState = getState(get);
        boolean doExpand = get.getUri().getQuery() != null &&
                UriUtils.hasODataExpandParamValue(get.getUri());

        if (!doExpand) {
            get.setBody(currentState).complete();
            return;
        }

        StorageDescriptionExpanded sdExpanded = new StorageDescriptionExpanded();
        currentState.copyTo(sdExpanded);

        List<Operation> getOps = new ArrayList<>();
        if (currentState.groupLinks != null) {
            sdExpanded.resourceGroupStates = new HashSet<>(currentState.groupLinks.size());
            currentState.groupLinks.stream().forEach(rgLink -> {
                getOps.add(Operation.createGet(this, rgLink)
                        .setReferer(this.getUri())
                        .setCompletion((o, e) -> {
                            if (e == null) {
                                sdExpanded.resourceGroupStates
                                        .add(o.getBody(ResourceGroupState.class));
                            } else {
                                logFine("Could not fetch resource group state %s due to %s",
                                        rgLink, e.getMessage());
                            }
                        }));
            });
            if (!getOps.isEmpty()) {
                OperationJoin.create(getOps)
                        .setCompletion((ops, exs) -> {
                            if (exs != null) {
                                get.fail(new IllegalStateException(Utils.toString(exs)));
                            } else {
                                get.setBody(sdExpanded).complete();
                            }
                        }).sendWith(this);
            } else {
                get.setBody(sdExpanded).complete();
            }
        } else {
            get.setBody(sdExpanded).complete();
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(template);
        return template;
    }
}
