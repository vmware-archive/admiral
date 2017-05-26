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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Endpoint storage profile.
 */
public class StorageProfileService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.STORAGE_PROFILES;

    public static class StorageItem {
        /**
         * Link to the resource group associated with this storage item.
         */
        public String resourceGroupLink;
        /**
         * Link to the Storage description associated with this storage item.
         */
        public String storageDescriptionLink;
        /**
         * Name of the storage properties defined for Jason's reference.
         */
        public String name;
        /**
         * Tags to be specified in the blueprint against each disk. To be used in filtering which
         * storage item is to be used
         */
        public Set<String> tagLinks;
        /**
         * Map of storage properties that are to be used by the provider when provisioning disks
         */
        public Map<String, String> diskProperties;
        /**
         * defines if this particular storage item contains default storage properties
         */
        public boolean defaultItem;
        /**
         * Indicates whether this storage item supports encryption or not.
         */
        public Boolean supportsEncryption;

        public void copyTo(StorageItem target) {
            target.resourceGroupLink = this.resourceGroupLink;
            target.storageDescriptionLink = this.storageDescriptionLink;
            target.name = this.name;
            target.tagLinks = this.tagLinks;
            target.diskProperties = this.diskProperties;
            target.defaultItem = this.defaultItem;
            target.supportsEncryption = this.supportsEncryption;
        }
    }

    public static class StorageProfile extends ResourceState {

        @Documentation(description = "Link to the endpoint this profile is associated with")
        @PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String endpointLink;

        @Documentation(description = "Contains storageItems that define disk properties to be "
                + "used by providers")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL })
        public List<StorageItem> storageItems;

        @Override
        public void copyTo(ResourceState target) {
            super.copyTo(target);
            if (target instanceof StorageProfile) {
                StorageProfile targetState = (StorageProfile) target;
                targetState.storageItems = this.storageItems;
                targetState.endpointLink = this.endpointLink;
            }
        }
    }

    public static class StorageProfileExpanded extends StorageProfile {
        public List<StorageItemExpanded> storageItemsExpanded;

        public static URI buildUri(URI storageProfileUri) {
            return UriUtils.buildExpandLinksQueryUri(storageProfileUri);
        }
    }

    public static class StorageItemExpanded extends StorageItem {
        public StorageDescription storageDescription;
        public ResourceGroupState resourceGroupState;
    }

    public StorageProfileService() {
        super(StorageProfile.class);
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

        StorageProfile newState = processInput(put);
        setState(put, newState);
        put.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        StorageProfile currentState = getState(patch);
        try {
            Utils.mergeWithStateAdvanced(getStateDescription(), currentState,
                    StorageProfile.class, patch);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            patch.fail(e);
            return;
        }
        patch.setBody(currentState);
        patch.complete();
    }

    @Override
    public void handleGet(Operation get) {
        StorageProfile currentState = getState(get);
        boolean doExpand = get.getUri().getQuery() != null &&
                UriUtils.hasODataExpandParamValue(get.getUri());

        if (!doExpand) {
            get.setBody(currentState).complete();
            return;
        }

        StorageProfileExpanded spExpanded = new StorageProfileExpanded();
        currentState.copyTo(spExpanded);

        if (spExpanded.storageItems == null || spExpanded.storageItems.isEmpty()) {
            get.setBody(spExpanded).complete();
            return;
        }

        spExpanded.storageItemsExpanded = new ArrayList<>(spExpanded.storageItems.size());
        List<Operation> getOps = new ArrayList<>(spExpanded.storageItems.size());
        Map<String, StorageDescription> storageDescriptions = new HashMap<>(spExpanded.storageItems.size());
        Map<String, ResourceGroupState> resourceGroupStates = new HashMap<>(spExpanded.storageItems.size());
        spExpanded.storageItems.stream().forEach(si -> {
            StorageItemExpanded sIExpanded = new StorageItemExpanded();
            si.copyTo(sIExpanded);
            spExpanded.storageItemsExpanded.add(sIExpanded);
            if (sIExpanded.storageDescriptionLink != null && !storageDescriptions.containsKey
                    (sIExpanded.storageDescriptionLink)) {
                getOps.add(Operation.createGet(this, sIExpanded.storageDescriptionLink)
                        .setReferer(this.getUri())
                        .setCompletion((o, e) -> {
                            if (e == null) {
                                storageDescriptions.put(sIExpanded.storageDescriptionLink, o
                                        .getBody(StorageDescription.class));
                            } else {
                                logFine("Could not load storage description %s due to %s",
                                        sIExpanded.storageDescriptionLink, e.getMessage());
                            }
                        }));
                storageDescriptions.put(sIExpanded.storageDescriptionLink, null);
            }

            if (sIExpanded.resourceGroupLink != null && !resourceGroupStates.containsKey
                    (sIExpanded.resourceGroupLink)) {
                getOps.add(Operation.createGet(this, sIExpanded.resourceGroupLink)
                        .setReferer(this.getUri())
                        .setCompletion((o, e) -> {
                            if (e == null) {
                                resourceGroupStates.put(sIExpanded.resourceGroupLink, o
                                        .getBody(ResourceGroupState.class));
                            } else {
                                logFine("Could not load Resource group state %s due to %s",
                                        sIExpanded.resourceGroupLink, e.getMessage());
                            }
                        }));
                resourceGroupStates.put(sIExpanded.resourceGroupLink, null);
            }
        });

        if (!getOps.isEmpty()) {
            OperationJoin.create(getOps)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            get.fail(new Throwable(Utils.toString(exs)));
                        } else {
                            // Update storage description entries in the expanded storage item
                            spExpanded.storageItemsExpanded.stream().forEach(si ->  {
                                if (si.storageDescriptionLink != null) {
                                    si.storageDescription = storageDescriptions.get(si
                                            .storageDescriptionLink);
                                }
                                if (si.resourceGroupLink != null) {
                                    si.resourceGroupState = resourceGroupStates.get(si
                                            .resourceGroupLink);
                                }
                            });
                            get.setBody(spExpanded).complete();
                        }
                    }).sendWith(this);
        } else {
            get.setBody(spExpanded).complete();
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

    private StorageProfile processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        StorageProfile state = op.getBody(StorageProfile.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }
}
