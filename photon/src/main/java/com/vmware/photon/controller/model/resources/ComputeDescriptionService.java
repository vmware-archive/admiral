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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;
import org.apache.commons.lang3.tuple.Pair;

import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Describes a compute resource. The same description service instance can be re-used across many
 * compute resources acting as a shared template.
 */
public class ComputeDescriptionService extends StatefulService {
    public static final String FACTORY_LINK = UriPaths.RESOURCES
            + "/compute-descriptions";

    /**
     * This class represents the document state associated with a {@link ComputeDescriptionService}
     * task.
     */
    public static class ComputeDescription extends ResourceState {

        public static final String CUSTOM_PROPERTY_KEY_TEMPLATE = "Template";
        public static final String FIELD_NAME_RESOURCE_POOL_ID = "resourcePoolId";
        public static final String FIELD_NAME_SUPPORTED_CHILDREN = "supportedChildren";
        public static final String FIELD_NAME_ZONE_ID = "zoneId";
        public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";
        public static final String FIELD_NAME_ENVIRONMENT_NAME = "environmentName";
        public static final String ENVIRONMENT_NAME_ON_PREMISE = "On premise";
        public static final String ENVIRONMENT_NAME_VCLOUD_AIR = "VMware vCloud Air";
        public static final String ENVIRONMENT_NAME_GCP = "Google Cloud Platform";
        @Deprecated
        public static final String ENVIRONMENT_NAME_GCE = "Google Compute Engine";
        public static final String ENVIRONMENT_NAME_AWS = "Amazon Web Services";
        public static final String ENVIRONMENT_NAME_AZURE = "Microsoft Azure";

        /**
         * Identifier of the zone associated with this compute host.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String zoneId;

        /**
         * Environment/ Platform name this compute is provisioned on.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String environmentName;

        /**
         * List of compute types this host supports actuating.
         */
        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND })
        public List<String> supportedChildren;

        /**
         * List of Network interfaces descriptions to attach to this compute.
         */
        @PropertyOptions(usage = PropertyUsageOption.LINKS)
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_1)
        public List<String> networkInterfaceDescLinks;

        /**
         * Disks descrptions associated with this compute instance.
         */
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @PropertyOptions(usage = PropertyUsageOption.LINKS)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_11)
        public List<String> diskDescLinks;

        /**
         * Self-link to the AuthCredentialsService used to access this compute host.
         */
        public String authCredentialsLink;

        /**
         * The type of the compute instance, as understood by the provider. E.g. the type of
         * instance determines your instance’s CPU capacity, memory, and storage.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String instanceType;

        /**
         * Desired number of CPU cores in this compute. {@code 0} when not applicable.
         */
        public long cpuCount;

        /**
         * Desired clock speed (in MHz) per CPU core. {@code 0} when not applicable.
         */
        public long cpuMhzPerCore;

        /**
         * Desired number of GPU cores in this compute. {@code 0} when not applicable.
         */
        public long gpuCount;

        /**
         * Desired total amount of memory (in bytes) available on this compute. {@code 0} when not
         * applicable.
         */
        public long totalMemoryBytes;

        /**
         * Desired power state of this compute instance.
         */
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_11)
        public PowerState powerState;

        /**
         * Constraints of this compute resource to other resources. Different services can specify
         * their specific constraints by using different keys in the map, so that multiple
         * constraints are supported for different purposes - e.g. placement constraints, grouping
         * constraints, etc.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_1)
        public Map<String, Constraint> constraints;

        /**
         * URI reference to the adapter used to create an instance of this host.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI instanceAdapterReference;

        /**
         * URI reference to the adapter used to power-on this host.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI powerAdapterReference;

        /**
         * URI reference to the adapter used to boot this host.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI bootAdapterReference;

        /**
         * URI reference to the adapter used to get the health status of this host.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI healthAdapterReference;

        /**
         * URI reference to the adapter used to get the stats info of this host.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI statsAdapterReference;

        @Documentation(description = "Set of URIs for stats adapters of this host")
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Set<URI> statsAdapterReferences;

        /**
         * URI reference to the adapter used to enumerate instances of this host.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI enumerationAdapterReference;

        /**
         * Link to the cloud account endpoint the compute belongs to.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_7)
        public String endpointLink;

        /**
         * Pricing associated with this host (measured per minute).
         */
        @Deprecated
        public double costPerMinute;

        /**
         * Currency unit used for pricing.
         */
        @Deprecated
        public String currencyUnit;

        /**
         * Identifier of the data store associated with this compute host. This field will be
         * deprecated
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String dataStoreId;

        /**
         * Types of Compute hosts.
         */
        public enum ComputeType {
            VM_HOST, VM_GUEST, DOCKER_CONTAINER, PHYSICAL, OS_ON_PHYSICAL, ZONE
        }
    }

    public ComputeDescriptionService() {
        super(ComputeDescription.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    private static void validateBootAdapterReference(ComputeDescription state) {
        if (state.supportedChildren == null) {
            return;
        }

        for (String supportedChild : state.supportedChildren) {
            ComputeType type = ComputeType.valueOf(supportedChild);
            switch (type) {
            case PHYSICAL:
            case VM_HOST:
                if (state.bootAdapterReference == null) {
                    throw new IllegalArgumentException(
                            "bootAdapterReference is required");
                }
                if (state.powerAdapterReference == null) {
                    throw new IllegalArgumentException(
                            "powerAdapterReference is required");
                }
                break;
            case DOCKER_CONTAINER:
                break;
            case OS_ON_PHYSICAL:
                break;
            case VM_GUEST:
                break;
            default:
                break;
            }
        }
    }

    private static void validateInstanceAdapterReference(
            ComputeDescription state) {
        if (state.supportedChildren == null) {
            return;
        }
        for (String supportedChild : state.supportedChildren) {
            ComputeType type = ComputeType.valueOf(supportedChild);
            switch (type) {
            case VM_HOST:
                if (state.instanceAdapterReference == null) {
                    throw new IllegalArgumentException(
                            "instanceAdapterReference is required");
                }
                break;
            case DOCKER_CONTAINER:
                break;
            case OS_ON_PHYSICAL:
                break;
            case PHYSICAL:
                break;
            case VM_GUEST:
                if (state.instanceAdapterReference == null) {
                    throw new IllegalArgumentException(
                            "instanceAdapterReference is required");
                }
                break;
            default:
                break;
            }
        }
    }

    @Override
    public void handleDelete(Operation delete) {
        logInfo("Deleting ComputeDescription, Path: %s, Operation ID: %d, Referrer: %s",
                delete.getUri().getPath(), delete.getId(),
                delete.getRefererAsString());
        super.handleDelete(delete);
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
            ComputeDescription returnState = processInput(put);
            returnState.copyTenantLinks(getState(put));
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private ComputeDescription processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        ComputeDescription state = op.getBody(ComputeDescription.class);
        validateState(state);
        return state;
    }

    public void validateState(ComputeDescription state) {
        Utils.validateState(getStateDescription(), state);

        if (state.environmentName == null) {
            state.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        }
        if (state.powerState == null) {
            state.powerState = PowerState.UNKNOWN;
        }

        validateBootAdapterReference(state);
        validateInstanceAdapterReference(state);
    }

    @Override
    public void handlePatch(Operation patch) {
        ComputeDescription currentState = getState(patch);
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                ComputeDescription.class, op -> {
                    ComputeDescription patchBody = op.getBody(ComputeDescription.class);
                    boolean hasChanged = false;

                    if (patchBody.cpuCount != 0) {
                        hasChanged |= currentState.cpuCount != patchBody.cpuCount;
                        currentState.cpuCount = patchBody.cpuCount;
                    }
                    if (patchBody.cpuMhzPerCore != 0) {
                        hasChanged |= currentState.cpuMhzPerCore != patchBody.cpuMhzPerCore;
                        currentState.cpuMhzPerCore = patchBody.cpuMhzPerCore;
                    }
                    if (patchBody.gpuCount != 0) {
                        hasChanged |= currentState.gpuCount != patchBody.gpuCount;
                        currentState.gpuCount = patchBody.gpuCount;
                    }
                    if (patchBody.totalMemoryBytes != 0) {
                        hasChanged |= currentState.totalMemoryBytes != patchBody.totalMemoryBytes;
                        currentState.totalMemoryBytes = patchBody.totalMemoryBytes;
                    }

                    if (patchBody.regionId != null && currentState.regionId == null) {
                        hasChanged = true;
                        currentState.regionId = patchBody.regionId;
                    }

                    // make sure the supportChildren is patched with new values only
                    Pair<List<String>, Boolean> supportedChildrenMergeResult = PhotonModelUtils
                            .mergeLists(currentState.supportedChildren,
                                    patchBody.supportedChildren);
                    currentState.supportedChildren = supportedChildrenMergeResult.getLeft();
                    hasChanged = hasChanged || supportedChildrenMergeResult.getRight();

                    // make sure the diskDescLinks is patched with new values only
                    Pair<List<String>, Boolean> diskDescLinksMergeResult = PhotonModelUtils
                            .mergeLists(currentState.diskDescLinks, patchBody.diskDescLinks);
                    currentState.diskDescLinks = diskDescLinksMergeResult.getLeft();
                    hasChanged = hasChanged || diskDescLinksMergeResult.getRight();

                    // make sure the networkInterfaceDescLinks is patched with new values only
                    Pair<List<String>, Boolean> networkInterfaceDescLinksMergeResult = PhotonModelUtils
                            .mergeLists(
                                    currentState.networkInterfaceDescLinks,
                                    patchBody.networkInterfaceDescLinks);
                    currentState.networkInterfaceDescLinks = networkInterfaceDescLinksMergeResult
                            .getLeft();
                    hasChanged = hasChanged || networkInterfaceDescLinksMergeResult.getRight();

                    return Boolean.valueOf(hasChanged);
                });
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        ComputeDescription template = (ComputeDescription) td;

        template.bootAdapterReference = UriUtils.buildUri(this.getHost(),
                "/bootAdapterReference");
        template.powerAdapterReference = UriUtils.buildUri(this.getHost(),
                "/powerAdapterReference");
        template.instanceAdapterReference = UriUtils.buildUri(this.getHost(),
                "/instanceAdapterReference");
        template.healthAdapterReference = UriUtils.buildUri(this.getHost(),
                "/healthAdapterReference");
        template.statsAdapterReference = UriUtils.buildUri(this.getHost(),
                "/statsAdapterReference");
        template.statsAdapterReferences = Collections.singleton(UriUtils.buildUri(
                this.getHost(), "/customStatsAdapterReference"));
        template.enumerationAdapterReference = UriUtils.buildUri(
                this.getHost(), "/enumerationAdapterReference");

        template.dataStoreId = null;

        ArrayList<String> children = new ArrayList<>();
        for (ComputeDescription.ComputeType type : ComputeDescription.ComputeType
                .values()) {
            children.add(type.name());
        }
        template.supportedChildren = children;
        template.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        template.cpuCount = 2;
        template.gpuCount = 1;
        template.totalMemoryBytes = Integer.MAX_VALUE;
        template.id = UUID.randomUUID().toString();
        template.name = "friendly-name";
        template.regionId = "provider-specific-regions";
        template.zoneId = "provider-specific-zone";
        template.instanceType = "provider-specific-instance-type";
        return template;
    }
}
