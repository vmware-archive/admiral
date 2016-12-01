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

package com.vmware.admiral.compute.container.network;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.compute.container.maintenance.ContainerNetworkMaintenance;
import com.vmware.admiral.compute.container.util.CompositeComponentNotifier;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerNetworkService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.CONTAINER_NETWORKS;

    private volatile ContainerNetworkMaintenance containerNetworkMaintenance;

    public static class ContainerNetworkState extends ResourceState {

        public static final String FIELD_NAME_DESCRIPTION_LINK = "descriptionLink";
        public static final String FIELD_NAME_POWER_STATE = "powerState";
        public static final String FIELD_NAME_PARENT_LINKS = "parentLinks";
        public static final String FIELD_NAME_IPAM = "ipam";
        public static final String FIELD_NAME_DRIVER = "driver";
        public static final String FIELD_NAME_OPTIONS = "options";
        public static final String FIELD_NAME_ORIGINATIONG_HOST_REFERENCE = "originatingHostReference";
        public static final String FIELD_NAME_ADAPTER_MANAGEMENT_REFERENCE = "adapterManagementReference";
        public static final String FIELD_NAME_COMPOSITE_COMPONENT_LINKS = "compositeComponentLinks";

        public static enum PowerState {
            UNKNOWN,
            PROVISIONING,
            CONNECTED,
            RETIRED,
            ERROR;

            public boolean isUnmanaged() {
                return this == PROVISIONING || this == RETIRED;
            }
        }

        /** Defines the description of the network */
        @Documentation(description = "Defines the description of the network.")
        @UsageOption(option = PropertyUsageOption.LINK)
        public String descriptionLink;

        /** Reference to the host that this network was created on. */
        @Documentation(description = "Reference to the host that this network was created on.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL, PropertyUsageOption.LINK })
        public String originatingHostLink;

        @Documentation(description = "Links to CompositeComponents when a network is part of App/Composition request.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL })
        public List<String> compositeComponentLinks;

        /** Defines which adapter will serve the provision request */
        @Documentation(description = "Defines which adapter will serve the provision request")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public URI adapterManagementReference;

        /** Network state indicating runtime state of a network instance. */
        @Documentation(description = "Network state indicating runtime state of a network instance.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public PowerState powerState;

        /** Container host links */
        @Documentation(description = "Container host links")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public List<String> parentLinks;

        /** An IPAM configuration for a given network. */
        @Documentation(description = "An IPAM configuration for a given network.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Ipam ipam;

        /** The name of the driver for this network. Can be bridge, host, overlay, none. */
        @Documentation(description = "The name of the driver for this network. "
                + "Can be bridge, host, overlay, none.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String driver;

        /** Network connected time in milliseconds */
        @Documentation(description = "Network connected time in milliseconds")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Long connected;

        /**
         * If set to true, specifies that this network exists independently of any application.
         */
        @Documentation(description = "If set to true, specifies that this network exists independently "
                + "of any application.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Boolean external;

        /**
         * Runtime property that will be populated during network inspections. Contains the number
         * of containers that are connected to this container network.
         */
        @Documentation(description = "Runtime property that will be populated during network inspections. "
                + "Contains the number of containers that are connected to this container network.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Integer connectedContainersCount;

        /**
         * A map of field-value pairs for a given network. These are used to specify network option
         * that are to be used by the network drivers.
         */
        @Documentation(description = "A map of field-value pairs for a given network. These are used"
                + "to specify network options that are used by the network drivers.")
        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND }, usage = {
                PropertyUsageOption.OPTIONAL, PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Map<String, String> options;

    }

    /**
     * Custom body to perform atomic increment operations on the connectedContainersCount via PATCH.
     */
    public static class ConnectedContainersCountIncrement {
        public Integer increment;
    }

    public ContainerNetworkService() {
        super(ContainerNetworkState.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
        toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.setMaintenanceIntervalMicros(ContainerNetworkMaintenance.MAINTENANCE_INTERVAL_MICROS);
    }

    @Override
    public void handleCreate(Operation create) {
        ContainerNetworkState body = getValidInputFrom(create, false);

        if (body.powerState == null) {
            body.powerState = ContainerNetworkState.PowerState.UNKNOWN;
        }

        body.connected = new Date().getTime();

        // start the monitoring service instance for this network
        startMonitoringContainerNetworkState(body);

        CompositeComponentNotifier.notifyCompositionComponents(this,
                body.compositeComponentLinks, create.getAction());

        create.complete();
    }

    @Override
    public void handlePut(Operation put) {
        try {
            ContainerNetworkState putState = getValidInputFrom(put, false);
            setState(put, putState);
            put.setBody(putState).complete();
        } catch (Throwable e) {
            logSevere(e);
            put.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ContainerNetworkState currentState = getState(patch);

        ConnectedContainersCountIncrement incrementPatch = patch
                .getBody(ConnectedContainersCountIncrement.class);
        if (incrementPatch != null && incrementPatch.increment != null) {
            currentState.connectedContainersCount += incrementPatch.increment;
            patch.complete();
            return;
        }

        ContainerNetworkState patchBody = getValidInputFrom(patch, true);

        ServiceDocumentDescription docDesc = getDocumentTemplate().documentDescription;
        String currentSignature = Utils.computeSignature(currentState, docDesc);
        List<String> currentCompositeComponentLinks = currentState.compositeComponentLinks;

        PropertyUtils.mergeServiceDocuments(currentState, patchBody,
                NetworkUtils.SHALLOW_MERGE_SKIP_MAPS_STRATEGY);
        PropertyUtils.mergeCustomProperties(currentState.options, patchBody.options);

        String newSignature = Utils.computeSignature(currentState, docDesc);

        boolean changed = !newSignature.equals(currentSignature);
        if (!changed) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        } else {
            CompositeComponentNotifier.notifyCompositionComponentsOnChange(this, patch.getAction(),
                    currentState.compositeComponentLinks, currentCompositeComponentLinks);
        }

        patch.complete();
    }

    @Override
    public void handleDelete(Operation delete) {
        ContainerNetworkState currentState = getState(delete);
        CompositeComponentNotifier.notifyCompositionComponents(this,
                currentState.compositeComponentLinks, delete.getAction());

        super.handleDelete(delete);
    }

    @Override
    public void handleMaintenance(Operation post) {
        if (getProcessingStage() != ProcessingStage.AVAILABLE) {
            logFine("Skipping maintenance since service is not available: %s ", getUri());
            return;
        }

        if (containerNetworkMaintenance == null) {
            sendRequest(Operation.createGet(getUri()).setCompletion((o, e) -> {
                if (e == null) {
                    ContainerNetworkState currentState = o.getBody(ContainerNetworkState.class);
                    containerNetworkMaintenance = ContainerNetworkMaintenance.create(getHost(),
                            getSelfLink(),
                            currentState.descriptionLink != null
                                    && !currentState.descriptionLink.startsWith(
                                            ContainerNetworkDescriptionService.DISCOVERED_DESCRIPTION_LINK));
                    containerNetworkMaintenance.handleMaintenance(post);
                }
            }));
            return;
        }
        containerNetworkMaintenance.handleMaintenance(post);
    }

    private void startMonitoringContainerNetworkState(ContainerNetworkState body) {
        if (body.id != null) {
            // perform maintenance on startup to refresh the network attributes
            // but only for networks that already exist (and have and ID)
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

    /**
     * Validates the specified {@link ContainerNetworkState}. If the validation fails, an Exception
     * will be thrown.
     *
     * @param isUpdate
     *            on updates the check for non <code>null</code> required fields is skipped.
     *            <code>null</code> values in that case represent no change. PATCH method is
     *            considered an update. PUT is not an update.
     */
    public void validateState(ContainerNetworkState state, boolean isUpdate) {
        if (!isUpdate) {
            // check that all required fields are not null.
            // Skip this step on updates (null = no update)
            Utils.validateState(getStateDescription(), state);
            NetworkUtils.validateNetworkName(state.name);
        }

        if (state.ipam != null) {
            if (state.ipam.config != null && state.ipam.config.length > 0) {
                for (IpamConfig ipamConfig : state.ipam.config) {
                    if (ipamConfig != null) {
                        NetworkUtils.validateIpCidrNotation(ipamConfig.subnet);
                        NetworkUtils.validateIpCidrNotation(ipamConfig.ipRange);
                        NetworkUtils.validateIpAddress(ipamConfig.gateway);
                        if (ipamConfig.auxAddresses != null) {
                            ipamConfig.auxAddresses.values().stream().forEach((address) -> {
                                NetworkUtils.validateIpAddress(address);
                            });
                        }
                    }
                }
            }
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ContainerNetworkState template = (ContainerNetworkState) super.getDocumentTemplate();

        template.name = "name (string)";
        template.descriptionLink = UriUtils.buildUriPath(
                ContainerNetworkDescriptionService.FACTORY_LINK,
                "docker-network");

        template.powerState = ContainerNetworkState.PowerState.UNKNOWN;

        Ipam ipam = new Ipam();
        ipam.driver = "default";

        IpamConfig ipamConfig = new IpamConfig();

        ipamConfig.subnet = "127.17.0.0/16";
        ipamConfig.ipRange = "127.17.5.0/24";
        ipamConfig.gateway = "127.17.0.1";

        ipamConfig.auxAddresses = new HashMap<>();
        ipamConfig.auxAddresses.put("host1", "127.17.1.5");
        ipamConfig.auxAddresses.put("host2", "127.17.1.6");

        ipam.config = new IpamConfig[] { ipamConfig };
        template.ipam = ipam;

        template.options = new HashMap<>(1);
        template.options.put("com.docker.network.bridge.enable_icc (string)",
                "true (boolean)");

        template.customProperties = new HashMap<>(1);
        template.customProperties.put("key (string)", "value (string)");

        template.compositeComponentLinks = new ArrayList<String>(0);
        template.connectedContainersCount = 0;

        template.parentLinks = new ArrayList<String>(0);

        return template;
    }

    /**
     * Returns valid {@link ContainerNetworkState} instance for the specified operation or throws an
     * Exception if validation fails.
     */
    private ContainerNetworkState getValidInputFrom(Operation op, boolean isUpdate) {
        checkForBody(op);
        ContainerNetworkState incomingState = op.getBody(ContainerNetworkState.class);
        validateState(incomingState, isUpdate);
        return incomingState;
    }

}
