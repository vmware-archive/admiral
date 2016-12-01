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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.compute.Composable;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.maintenance.ContainerHealthEvaluator;
import com.vmware.admiral.compute.container.maintenance.ContainerMaintenance;
import com.vmware.admiral.compute.container.maintenance.ContainerStats;
import com.vmware.admiral.compute.container.util.ContainerUtil;
import com.vmware.admiral.compute.content.EnvDeserializer;
import com.vmware.admiral.compute.content.EnvSerializer;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Describes a container instance. The same description service instance can be re-used across many
 * container instances acting as a shared template.
 */
public class ContainerService extends StatefulService {

    private volatile ContainerMaintenance containerMaintenance;

    public static class ContainerState
            extends com.vmware.photon.controller.model.resources.ResourceState
            implements Composable {
        public static final String FIELD_NAME_NAMES = "names";
        public static final String FIELD_NAME_COMMAND = "command";
        public static final String FIELD_NAME_PORTS = "ports";
        public static final String FIELD_NAME_IMAGE = "image";
        public static final String FIELD_NAME_DESCRIPTION_LINK = "descriptionLink";
        public static final String FIELD_NAME_COMPOSITE_COMPONENT_LINK = "compositeComponentLink";
        public static final String FIELD_NAME_PARENT_LINK = "parentLink";
        public static final String FIELD_NAME_RESOURCE_POOL_LINK = "resourcePoolLink";
        public static final String FIELD_NAME_POWER_STATE = "powerState";
        public static final String CONTAINER_ALLOCATION_STATUS = "allocation";
        public static final String CONTAINER_DEGRADED_STATUS = "degraded";
        public static final String CONTAINER_ERROR_STATUS = "error";
        public static final String CONTAINER_RUNNING_STATUS = "running";
        public static final String FIELD_NAME_SYSTEM = "system";
        public static final String FIELD_NAME_VOLUME_DRIVER = "volumeDriver";

        public static enum PowerState {
            UNKNOWN,
            PROVISIONING,
            RUNNING,
            PAUSED,
            STOPPED,
            RETIRED,
            ERROR;

            public static PowerState transform(
                    com.vmware.photon.controller.model.resources.ComputeService.PowerState powerState) {
                switch (powerState) {
                case ON:
                    return PowerState.RUNNING;
                case OFF:
                    return PowerState.STOPPED;
                case UNKNOWN:
                default:
                    return PowerState.UNKNOWN;
                }
            }

            public boolean isUnmanaged() {
                return this == PROVISIONING || this == RETIRED;
            }
        }

        /** The list of names of a given container host. */
        @Documentation(description = "The list of names of a given container host.")
        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND }, usage = {
                PropertyUsageOption.OPTIONAL })
        public List<String> names;

        /** Defines the description of the container */
        @Documentation(description = "Defines the description of the container.")
        @UsageOption(option = PropertyUsageOption.LINK)
        public String descriptionLink;

        @Documentation(description = "Link to CompositeComponent when a container is part of App/Composition request.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL, PropertyUsageOption.LINK })
        public String compositeComponentLink;

        /** Defines the address of the container */
        @Documentation(description = "Defines the address of the container")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String address;

        /** Defines which adapter which serve the provision request */
        @Documentation(description = "Defines which adapter which serve the provision request")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public URI adapterManagementReference;

        /** Container state indicating runtime state of a container instance. */
        @Documentation(description = "Container state indicating runtime state of a container instance.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public PowerState powerState;

        /**
         * Port bindings in the format ip:hostPort:containerPort | ip::containerPort |
         * hostPort:containerPort | containerPort where range of ports can also be provided
         */
        @Documentation(description = "Port bindings in the format ip:hostPort:containerPort | ip::containerPort |+"
                + "hostPort:containerPort | containerPort where range of ports can also be provided")
        @PropertyOptions(usage = PropertyUsageOption.OPTIONAL,
                indexing = PropertyIndexingOption.EXPAND)
        public List<PortBinding> ports;

        /** Joined networks and the configuration with which they are joined. */
        @Documentation(description = "Joined networks.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, ServiceNetwork> networks;

        /** (Required) The docker image */
        @Documentation(description = "The docker image.")
        public String image;

        /** Commands to run. */
        @Documentation(description = "Commands to run.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] command;

        /** Volumes from the specified container(s) of the format <container name>[:<ro|rw>] */
        @Documentation(description = "Volumes from the specified container(s) of the format <container name>[:<ro|rw>]")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] volumesFrom;

        /** Specify volume driver name. */
        @Documentation(description = "Specify volume driver name (default \"local\")")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String volumeDriver;

        /** Mount a volume e.g /host:/container[:ro] or just named volume like 'vol1' */
        @Documentation(description = "Mount a volume e.g /host:/container[:ro] or just named volume like 'vol1'")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] volumes;

        /** A list of services (in a blueprint) the container depends on */
        @Documentation(description = "A list of services (in a blueprint) the container depends on.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] links;

        /** A list of environment variables in the form of VAR=value. */
        @JsonSerialize(contentUsing = EnvSerializer.class)
        @JsonDeserialize(contentUsing = EnvDeserializer.class)
        @Documentation(description = "A list of environment variables in the form of VAR=value.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] env;

        /** Add a custom host-to-IP mapping (host:ip) */
        @Documentation(description = "Add a custom host-to-IP mapping (host:ip)")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] extraHosts;

        /** Container host link */
        @Documentation(description = "Container host link")
        @PropertyOptions(usage = { PropertyUsageOption.LINK, PropertyUsageOption.OPTIONAL })
        public String parentLink;

        /**
         * Link to the resource placement associated with a given container instance. Null if no
         * placement
         */
        @Documentation(description = "Link to the resource placement associated with a given container instance. Null if no placement")
        @PropertyOptions(usage = { PropertyUsageOption.LINK, PropertyUsageOption.OPTIONAL })
        public String groupResourcePlacementLink;

        /** Status of the container */
        @Documentation(description = "Status of the container")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String status;

        /** Container created time in milliseconds */
        @Documentation(description = "Container created time in milliseconds")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Long created;

        /** Container started time in milliseconds */
        @Documentation(description = "Container started time in milliseconds")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Long started;

        /** Effective memory limit */
        @Documentation(description = "Effective memory limit")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Long memoryLimit;

        /** Storage limit in bytes */
        @Documentation(description = "Storage limit in bytes")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Long storageLimit;

        /** Is system container */
        @Documentation(description = "Is system container")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Boolean system;

        /**
         * Percentages of the relative CPU sharing in a given resource pool. This is not an actual
         * limit but a guideline of how much CPU should be divided among all containers running at a
         * given time.
         */
        @Documentation(description = "Percentages of the relative CPU sharing in a given resource pool. This is not an actual"
                + "limit but a guideline of how much CPU should be divided among all containers running at"
                + "a given time.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Integer cpuShares;

        /** Unmodeled container attributes */
        @Documentation(description = "Unmodeled container attributes")
        @PropertyOptions(indexing = { PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE,
                PropertyIndexingOption.STORE_ONLY }, usage = { PropertyUsageOption.OPTIONAL })
        public Map<String, String> attributes;

        @Override
        public String retrieveCompositeComponentLink() {
            return compositeComponentLink;
        }
    }

    public ContainerService() {
        super(ContainerState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.setMaintenanceIntervalMicros(ContainerMaintenance.MAINTENANCE_INTERVAL_MICROS);
    }

    @Override
    public void handleCreate(Operation startPost) {
        if (startPost.hasBody()) {
            ContainerState body = startPost.getBody(ContainerState.class);
            logFine("Initial name is %s", body.id);
            if (body.powerState == null) {
                body.powerState = PowerState.UNKNOWN;
            }

            // start the container stats service instance for this container
            startMonitoringContainerState(body);
        }

        startPost.complete();
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        ContainerState putBody = put.getBody(ContainerState.class);

        this.setState(put, putBody);
        put.setBody(putBody).complete();

    }

    @Override
    public void handlePatch(Operation patch) {
        ContainerState currentState = getState(patch);
        ContainerState patchBody = patch.getBody(ContainerState.class);

        if (ContainerStats.KIND.equals(patchBody.documentKind)) {
            patchContainerStats(patch, currentState);
            return;
        }

        ServiceDocumentDescription docDesc = getDocumentTemplate().documentDescription;
        String currentSignature = Utils.computeSignature(currentState, docDesc);

        PropertyUtils.mergeServiceDocuments(currentState, patchBody);

        String newSignature = Utils.computeSignature(currentState, docDesc);

        // if the signature hasn't change we shouldn't modify the state
        if (currentSignature.equals(newSignature)) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        }

        if (ContainerUtil.isDiscoveredContainer(currentState)) {
            ContainerUtil.ContainerDescriptionHelper.createInstance(this)
                    .updateDiscoveredContainerDesc(currentState, patchBody, null);
        }

        patch.complete();
    }

    private void patchContainerStats(Operation patch, ContainerState currentState) {
        ContainerStats patchStatsBody = patch.getBody(ContainerStats.class);

        ContainerStats containerStats = ContainerStats.transform(this);
        ContainerHealthEvaluator.create(getHost(), currentState)
                .calculateHealthStatus(containerStats, patchStatsBody);
        patchStatsBody.setStats(this);

        patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        patch.complete();
    }

    @Override
    public void handleDelete(Operation delete) {
        super.handleDelete(delete);
    }

    @Override
    public void handlePeriodicMaintenance(Operation post) {
        if (getProcessingStage() != ProcessingStage.AVAILABLE) {
            logFine("Skipping maintenance since service is not available: %s ", getUri());
            return;
        }

        if (containerMaintenance == null) {
            containerMaintenance = ContainerMaintenance.create(getHost(), getSelfLink());
        }
        containerMaintenance.handlePeriodicMaintenance(post);
    }

    private void startMonitoringContainerState(ContainerState body) {
        if (body.id != null) {
            // perform maintenance on startup to refresh the container attributes
            // but only for containers that already exist (and have and ID)
            getHost().registerForServiceAvailability((o, ex) -> {
                if (ex != null) {
                    logWarning("Skipping maintenance because service failed to start: "
                            + ex.getMessage());

                } else {
                    handlePeriodicMaintenance(o);
                }
            }, getSelfLink());
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ContainerState template = (ContainerState) super.getDocumentTemplate();

        template.names = new ArrayList<>(2);
        template.names.add("name1 (string)");
        template.names.add("name2 (string)");
        template.image = "library/hello-world";
        template.command = new String[] { "cat (string)" };
        template.volumesFrom = new String[] { "volumeFrom[:ro] (string)" };
        template.volumes = new String [] {"host-volume-dir:/container-volume-dir"};
        template.extraHosts = new String[] { "hostname:ip" };
        template.env = new String[] {
                "ENV_VAR=value (string)",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/local/go/bin"
                        + ":/go/bin" };

        PortBinding portBinding = new PortBinding();
        portBinding.containerPort = "5000";
        portBinding.hostPort = "5000";
        template.ports = Collections.singletonList(portBinding);

        template.customProperties = new HashMap<String, String>(1);
        template.customProperties.put("propKey (string)", "customPropertyValue (string)");
        template.adapterManagementReference = URI.create("https://esxhost-01:443/provision-docker");
        template.powerState = ContainerState.PowerState.UNKNOWN;
        template.descriptionLink = UriUtils.buildUriPath(ContainerDescriptionService.FACTORY_LINK,
                "docker-nginx");
        template.attributes = new HashMap<>();
        template.attributes.put("Hostname (string)", "nginx (string)");

        template.networks = new LinkedHashMap<>();
        template.links = new String[] { "service:alias" };

        return template;
    }
}
