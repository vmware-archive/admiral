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

package com.vmware.photon.controller.model.resources;

import static com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.validator.routines.InetAddressValidator;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Represents a compute resource.
 */
public class ComputeService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/compute";

    /**
     * Compute State document.
     */
    public static class ComputeState extends ResourceState {
        public static final String FIELD_NAME_DESCRIPTION_LINK = "descriptionLink";
        public static final String FIELD_NAME_RESOURCE_POOL_LINK = "resourcePoolLink";
        public static final String FIELD_NAME_ADDRESS = "address";
        public static final String FIELD_NAME_PRIMARY_MAC = "primaryMAC";
        public static final String FIELD_NAME_POWER_STATE = "powerState";
        public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";
        public static final String FIELD_NAME_PARENT_LINK = "parentLink";
        public static final String FIELD_NAME_LIFECYCLE_STATE = "lifecycleState";
        public static final String FIELD_NAME_NETWORK_INTERFACE_LINKS = "networkInterfaceLinks";
        public static final String FIELD_NAME_DISK_LINKS = "diskLinks";
        public static final String FIELD_NAME_TYPE = "type";

        /**
         * URI reference to corresponding ComputeDescription.
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String descriptionLink;

        /**
         * Optional URI reference to the non-elastic resource pool which this compute contributes
         * capacity to. Based on dynamic queries in elastic resource pools this compute may
         * participate in other pools too.
         *
         * <p>
         * It is recommended to use {@code ResourcePoolState.query} instead which works for both
         * elastic and non-elastic resource pools.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String resourcePoolLink;

        /**
         * URI reference to the adapter used to create an instance of this compute.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_14)
        public URI instanceAdapterReference;

        /**
         * URI reference to the adapter used to power-on this compute.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_14)
        public URI powerAdapterReference;

        /**
         * URI reference to the adapter used to boot this compute.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_14)
        public URI bootAdapterReference;

        /**
         * URI reference to the adapter used to get the health status of this compute.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_14)
        public URI healthAdapterReference;

        /**
         * URI reference to the adapter used to get the stats info of this compute.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI statsAdapterReference;

        @Documentation(description = "Set of URIs for stats adapters of this host")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_14)
        public Set<URI> statsAdapterReferences;

        /**
         * URI reference to the adapter used to enumerate instances of this compute.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_14)
        public URI enumerationAdapterReference;

        /**
         * Ip address of this compute instance.
         */
        @PropertyOptions(indexing = PropertyIndexingOption.CASE_INSENSITIVE)
        public String address;

        /**
         * The type of this compute resource.
         */
        @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT)
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_6)
        public ComputeType type;

        /**
         * Environment/ Platform name this compute is provisioned on.
         */
        @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT)
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_9)
        public String environmentName;

        /**
         * MAC address of this compute instance.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String primaryMAC;

        /**
         * The type of the compute instance, as understood by the provider. E.g. the type of
         * instance determines your instance’s CPU capacity, memory, and storage.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_11)
        public String instanceType;

        /**
         * Actual number of CPU cores in this compute. {@code 0} when not applicable.
         */
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_11)
        public Long cpuCount;

        /**
         * Actual clock speed (in MHz) per CPU core. {@code 0} when not applicable.
         */
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_11)
        public Long cpuMhzPerCore;

        /**
         * Actual number of GPU cores in this compute. {@code 0} when not applicable.
         */
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_11)
        public Long gpuCount;

        /**
         * Actual total amount of memory (in bytes) available on this compute. {@code 0} when not
         * applicable.
         */
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_11)
        public Long totalMemoryBytes;

        /**
         * Power state of this compute instance.
         */
        public PowerState powerState;

        /**
         * Identifier of the zone associated with this compute instance.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_11)
        public String zoneId;

        /** Lifecycle state indicating runtime state of a resource instance. */
        @Documentation(description = "Lifecycle state indicating runtime state of a resource instance.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public LifecycleState lifecycleState;

        /**
         * URI reference to parent compute instance.
         */
        public String parentLink;

        /**
         * Reference to the management endpoint of the compute provider.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI adapterManagementReference;

        /**
         * Disks associated with this compute instance.
         */
        @PropertyOptions(usage = PropertyUsageOption.LINKS)
        public List<String> diskLinks;

        /**
         * Network interfaces associated with this compute instance.
         */
        @PropertyOptions(usage = PropertyUsageOption.LINKS)
        public List<String> networkInterfaceLinks;

        /**
         * Link to the cloud account endpoint the compute belongs to.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_7)
        public String endpointLink;

        /**
         * Host name associated with this compute instance.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_1)
        public String hostName;
    }

    /**
     * State with in-line, expanded description.
     */
    public static class ComputeStateWithDescription extends ComputeState {
        /**
         * Compute description associated with this compute instance.
         */
        public ComputeDescription description;

        public static URI buildUri(URI computeHostUri) {
            return UriUtils.extendUriWithQuery(computeHostUri,
                    UriUtils.URI_PARAM_ODATA_EXPAND,
                    ComputeState.FIELD_NAME_DESCRIPTION_LINK);
        }

        public static ComputeStateWithDescription create(
                ComputeDescription desc, ComputeState currentState) {
            ComputeStateWithDescription chsWithDesc = new ComputeStateWithDescription();
            currentState.copyTo(chsWithDesc);

            chsWithDesc.address = currentState.address;
            chsWithDesc.diskLinks = currentState.diskLinks;
            chsWithDesc.parentLink = currentState.parentLink;
            chsWithDesc.powerState = currentState.powerState;
            chsWithDesc.primaryMAC = currentState.primaryMAC;
            chsWithDesc.type = currentState.type;
            chsWithDesc.environmentName = currentState.environmentName;
            chsWithDesc.resourcePoolLink = currentState.resourcePoolLink;
            chsWithDesc.adapterManagementReference = currentState.adapterManagementReference;
            chsWithDesc.networkInterfaceLinks = currentState.networkInterfaceLinks;
            chsWithDesc.description = desc;
            chsWithDesc.descriptionLink = desc.documentSelfLink;
            chsWithDesc.regionId = currentState.regionId;
            chsWithDesc.zoneId = currentState.zoneId;
            chsWithDesc.hostName = currentState.hostName;
            chsWithDesc.instanceType = currentState.instanceType;
            chsWithDesc.cpuCount = currentState.cpuCount;
            chsWithDesc.cpuMhzPerCore = currentState.cpuMhzPerCore;
            chsWithDesc.gpuCount = currentState.gpuCount;
            chsWithDesc.totalMemoryBytes = currentState.totalMemoryBytes;
            chsWithDesc.endpointLink = currentState.endpointLink;

            return chsWithDesc;
        }
    }

    /**
     * Power State.
     */
    public enum PowerState {
        ON,
        OFF,
        UNKNOWN,
        SUSPEND
    }

    /**
     * Resource lifecycle status.
     * <p>
     * This class is kept to keep the backward compatibility. Use
     * {@link com.vmware.photon.controller.model.support.LifecycleState} when introducing lifecycle
     * semantic to other resources.
     * </p>
     */
    public enum LifecycleState {
        PROVISIONING,
        READY,
        SUSPEND,
        STOPPED,
        RETIRED
    }

    /**
     * Power Transition.
     */
    public enum PowerTransition {
        SOFT,
        HARD
    }

    /**
     * Boot Device.
     */
    public enum BootDevice {
        CDROM,
        DISK,
        NETWORK
    }

    public ComputeService() {
        super(ComputeState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleDelete(Operation delete) {
        logInfo("Deleting Compute, Path: %s, Operation ID: %d, Referrer: %s",
                delete.getUri().getPath(), delete.getId(),
                delete.getRefererAsString());
        super.handleDelete(delete);
    }

    @Override
    public void handleGet(Operation get) {
        ComputeState currentState = getState(get);
        boolean doExpand = get.getUri().getQuery() != null &&
                UriUtils.hasODataExpandParamValue(get.getUri());

        if (!doExpand) {
            get.setBody(currentState).complete();
            return;
        }

        // retrieve the description and include in an augmented version of our
        // state.
        Operation getDesc = Operation
                .createGet(this, currentState.descriptionLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                get.fail(e);
                                return;
                            }
                            ComputeDescription desc = o
                                    .getBody(ComputeDescription.class);
                            ComputeStateWithDescription chsWithDesc = ComputeStateWithDescription
                                    .create(desc, currentState);
                            get.setBody(chsWithDesc).complete();
                        });
        sendRequest(getDesc);
    }

    @Override
    public void handleCreate(Operation start) {
        try {
            validateCreate(start);
            start.complete();
        } catch (Throwable t) {
            start.fail(t);
        }
    }

    @Override
    public void handlePut(Operation put) {
        try {
            ComputeState returnState = validatePut(put);
            returnState.copyTenantLinks(getState(put));
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private ComputeState validateCreate(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ComputeState state = op.getBody(ComputeState.class);
        if (state.lifecycleState == null) {
            state.lifecycleState = LifecycleState.READY;
        }

        if (state.powerState == null) {
            state.powerState = PowerState.UNKNOWN;
        }

        Utils.validateState(getStateDescription(), state);

        return state;
    }

    private ComputeState validatePut(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ComputeState state = op.getBody(ComputeState.class);
        ComputeState currentState = getState(op);
        if (state.type != null && currentState.type != null && state.type != currentState.type) {
            throw new IllegalArgumentException("Compute type can not be changed");
        }
        if (state.environmentName != null && currentState.environmentName != null
                && !state.environmentName.equals(currentState.environmentName)) {
            throw new IllegalArgumentException("Environment name can not be changed");
        }
        Utils.validateState(getStateDescription(), state);
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        ComputeState currentState = getState(patch);
        Function<Operation, Boolean> customPatchHandler = t -> {
            boolean hasStateChanged = false;
            ComputeState patchBody = patch.getBody(ComputeState.class);

            if (patchBody.type != null) {
                if (currentState.type == null) {
                    currentState.type = patchBody.type;
                    hasStateChanged = true;
                } else if (patchBody.type != currentState.type) {
                    throw new IllegalArgumentException("Compute type can not be changed");
                }
            }

            if (patchBody.environmentName != null) {
                if (currentState.environmentName == null) {
                    currentState.environmentName = patchBody.environmentName;
                    hasStateChanged = true;
                } else if (!patchBody.environmentName.equals(currentState.environmentName)) {
                    throw new IllegalArgumentException("Environment name can not be changed");
                }
            }

            if (patchBody.address != null
                    && !patchBody.address.equals(currentState.address)) {
                InetAddressValidator.getInstance().isValidInet4Address(
                        patchBody.address);
                currentState.address = patchBody.address;
                hasStateChanged = true;
            }

            if (patchBody.powerState != null
                    && patchBody.powerState != currentState.powerState) {
                currentState.powerState = patchBody.powerState;
                hasStateChanged = true;
            }

            // make sure the diskLinks is patched with new values only
            Pair<List<String>, Boolean> diskLinksMergeResult =
                    PhotonModelUtils.mergeLists(
                            currentState.diskLinks, patchBody.diskLinks);
            currentState.diskLinks = diskLinksMergeResult.getLeft();
            hasStateChanged = hasStateChanged || diskLinksMergeResult.getRight();

            if (patchBody.creationTimeMicros != null && currentState.creationTimeMicros == null &&
                    currentState.creationTimeMicros != patchBody.creationTimeMicros) {
                currentState.creationTimeMicros = patchBody.creationTimeMicros;
                hasStateChanged = true;
            }

            // make sure the networkInterfaceLinks is patched with new values only
            Pair<List<String>, Boolean> networkInterfaceLinksMergeResult =
                    PhotonModelUtils.mergeLists(
                            currentState.networkInterfaceLinks, patchBody.networkInterfaceLinks);
            currentState.networkInterfaceLinks = networkInterfaceLinksMergeResult.getLeft();
            hasStateChanged = hasStateChanged || networkInterfaceLinksMergeResult.getRight();

            if (patchBody.regionId != null && currentState.regionId == null) {
                hasStateChanged = true;
                currentState.regionId = patchBody.regionId;
            }
            return hasStateChanged;
        };
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                ComputeState.class, customPatchHandler);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);

        ComputeState template = (ComputeState) td;

        template.id = UUID.randomUUID().toString();
        template.primaryMAC = "01:23:45:67:89:ab";
        template.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK,
                "on-prem-one-cpu-vm-guest");
        template.resourcePoolLink = null;
        template.type = ComputeType.VM_GUEST;
        template.environmentName = ENVIRONMENT_NAME_ON_PREMISE;
        template.adapterManagementReference = URI
                .create("https://esxhost-01:443/sdk");

        return template;
    }
}
