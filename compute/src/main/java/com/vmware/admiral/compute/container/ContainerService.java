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
import java.util.concurrent.CancellationException;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.ServiceDocumentTemplateUtil;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.maintenance.ContainerHealthEvaluator;
import com.vmware.admiral.compute.container.maintenance.ContainerMaintenance;
import com.vmware.admiral.compute.container.maintenance.ContainerStats;
import com.vmware.admiral.compute.container.network.ContainerNetworkReconfigureService;
import com.vmware.admiral.compute.container.network.ContainerNetworkReconfigureService.ContainerNetworkReconfigureState;
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
            extends com.vmware.photon.controller.model.resources.ResourceState {
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
            UNKNOWN, PROVISIONING, RUNNING, PAUSED, STOPPED, RETIRED, ERROR;

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
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public List<PortBinding> ports;

        /** Joined networks and the configuration with which they are joined. */
        @Documentation(description = "Joined networks.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, ServiceNetwork> networks;

        /**
         * The link to the exposed service description if this container is part of an exposed
         * service. All container nodes of the service are automatically load balanced through the
         * access points that are configured in this description.
         */
        @Documentation(description = "The link to the exposed service description if this container is part of an exposed service."
                + " All container nodes of the service are automatically load balanced through the access points tha "
                + " are configured in this description.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String exposedServiceLink;

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

        /** Specify volume driver name.*/
        @Documentation(description = "Specify volume driver name (default \"local\")")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String volumeDriver;

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
         * Link to the resource policy associated with a given container instance. Null if no policy
         */
        @Documentation(description = "Link to the resource policy associated with a given container instance. Null if no policy")
        @PropertyOptions(usage = { PropertyUsageOption.LINK, PropertyUsageOption.OPTIONAL })
        public String groupResourcePolicyLink;

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
            notifyCompositionComponent(body.compositeComponentLink, startPost.getAction());
        }

        startPost.complete();
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        ContainerState currentState = getState(put);
        String currentCompositeComponentLink = currentState.compositeComponentLink;
        ContainerState putBody = put.getBody(ContainerState.class);

        boolean networkChanged = notEqualsRegardingNull(currentState.address, putBody.address) ||
                notEqualsRegardingNull(currentState.ports, putBody.ports);

        this.setState(put, putBody);
        put.setBody(putBody).complete();

        notifyCompositionComponentOnChange(put, putBody, currentCompositeComponentLink);

        if (networkChanged) {
            reconfigureNetwork(currentState);
        }

    }

    @Override
    public void handlePatch(Operation patch) {
        ContainerState currentState = getState(patch);
        ContainerState patchBody = patch.getBody(ContainerState.class);

        if (ContainerStats.KIND.equals(patchBody.documentKind)) {
            patchContainerStats(patch, currentState);
            return;
        }

        boolean networkChanged = notEqualsRegardingNull(currentState.address, patchBody.address) ||
                notEqualsRegardingNull(currentState.ports, patchBody.ports);

        ServiceDocumentDescription docDesc = getDocumentTemplate().documentDescription;
        String currentSignature = Utils.computeSignature(currentState, docDesc);
        String currentCompositeComponentLink = currentState.compositeComponentLink;

        PropertyUtils.mergeServiceDocuments(currentState, patchBody);

        String newSignature = Utils.computeSignature(currentState, docDesc);

        // if the signature hasn't change we shouldn't modify the state
        if (currentSignature.equals(newSignature)) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        } else {
            notifyCompositionComponentOnChange(patch, currentState, currentCompositeComponentLink);
        }

        if (ContainerUtil.isDiscoveredContainer(currentState)) {
            ContainerUtil.ContainerDescriptionHelper.createInstance(this)
                    .updateDiscoveredContainerDesc(currentState, patchBody, null);
        }

        patch.complete();

        if (networkChanged) {
            reconfigureNetwork(currentState);
        }
    }

    private static boolean notEqualsRegardingNull(Object obj1, Object obj2) {
        return obj1 != null && obj2 != null && !obj1.equals(obj2);
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
        // if this request is to delete the container state from the index, also remove the
        // container stats from the index
        ContainerState currentState = getState(delete);
        notifyCompositionComponent(currentState.compositeComponentLink, delete.getAction());

        super.handleDelete(delete);
    }

    @Override
    public void handleMaintenance(Operation post) {
        if (getProcessingStage() != ProcessingStage.AVAILABLE) {
            logFine("Skipping maintenance since service is not available: %s ", getUri());
            return;
        }

        if (containerMaintenance == null) {
            containerMaintenance = ContainerMaintenance.create(getHost(), getSelfLink());
        }
        containerMaintenance.handleMaintenance(post);
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
                    handleMaintenance(o);
                }
            }, getSelfLink());
        }
    }

    private void notifyCompositionComponent(String compositeComponentLink, Action action) {
        if (compositeComponentLink == null || compositeComponentLink.isEmpty()) {
            return;
        }

        sendRequest(Operation.createGet(this, compositeComponentLink)
                .setCompletion((o, e) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        logFine("CompositeComponent not found %s", compositeComponentLink);
                        return;
                    }
                    if (e != null) {
                        logWarning("Can't find composite component. Error: %s", Utils.toString(e));
                        return;
                    }

                    notify(compositeComponentLink, action);
                }));

    }

    private void notify(String compositeComponentLink, Action action) {
        CompositeComponent body = new CompositeComponent();
        body.documentSelfLink = compositeComponentLink;
        body.componentLinks = new ArrayList<>(1);
        body.componentLinks.add(getSelfLink());

        URI uri = UriUtils.buildUri(getHost(), compositeComponentLink);
        if (Action.DELETE == action) {
            uri = UriUtils.extendUriWithQuery(uri,
                    UriUtils.URI_PARAM_INCLUDE_DELETED,
                    Boolean.TRUE.toString());
        }

        sendRequest(Operation.createPatch(uri)
                .setBody(body)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logWarning("Error notifying CompositeContainer: %s. Exception: %s",
                                compositeComponentLink, ex instanceof CancellationException
                                        ? "CancellationException" : Utils.toString(ex));
                    }
                }));
    }

    private void notifyCompositionComponentOnChange(Operation put, ContainerState currentState,
            String currentCompositeComponentLink) {
        if (currentCompositeComponentLink != null && currentState.compositeComponentLink == null) {
            notifyCompositionComponent(currentCompositeComponentLink, Action.DELETE);
        } else if ((currentCompositeComponentLink == null
                && currentState.compositeComponentLink != null)
                || (currentCompositeComponentLink != null
                && !currentCompositeComponentLink
                        .equals(currentState.compositeComponentLink))) {
            notifyCompositionComponent(currentState.compositeComponentLink, put.getAction());
        }
    }

    private void reconfigureNetwork(ContainerState currentState) {
        ContainerNetworkReconfigureState body = new ContainerNetworkReconfigureState();
        body.containerState = currentState;

        sendRequest(Operation
                .createPost(this, ContainerNetworkReconfigureService.SELF_LINK)
                .setBody(body)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning(
                                        "Could not reconfigure network for container and it's dependencies %s. Error: %s",
                                        currentState.documentSelfLink, Utils.toString(e));
                                return;
                            }
                        }));
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ContainerState template = (ContainerState) super.getDocumentTemplate();

        ServiceDocumentTemplateUtil.indexCustomProperties(template);

        template.names = new ArrayList<>(2);
        template.names.add("name1 (string)");
        template.names.add("name2 (string)");
        template.image = "library/hello-world";
        template.command = new String[] { "cat (string)" };
        template.volumesFrom = new String[] { "volumeFrom[:ro] (string)" };
        template.extraHosts = new String[] { "hostname:ip" };
        template.env = new String[] {
                "ENV_VAR=value (string)",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/local/go/bin"
                        + ":/go/bin" };

        PortBinding portBinding = new PortBinding();
        portBinding.containerPort = "5000";
        portBinding.hostPort = "5000";
        template.ports = Collections.singletonList(portBinding);

        ServiceDocumentTemplateUtil.indexProperty(template, ContainerState.FIELD_NAME_PORTS);

        template.customProperties = new HashMap<String, String>(1);
        template.customProperties.put("propKey (string)", "customPropertyValue (string)");
        template.adapterManagementReference = URI.create("https://esxhost-01:443/provision-docker");
        template.powerState = ContainerState.PowerState.UNKNOWN;
        template.descriptionLink = UriUtils.buildUriPath(ContainerDescriptionService.FACTORY_LINK,
                "docker-nginx");
        template.attributes = new HashMap<>();
        template.attributes.put("Hostname (string)", "nginx (string)");

        template.networks = new LinkedHashMap<>();

        return template;
    }
}
