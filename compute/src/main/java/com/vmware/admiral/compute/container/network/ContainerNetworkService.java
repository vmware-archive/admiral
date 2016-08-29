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
import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.ServiceDocumentTemplateUtil;
import com.vmware.admiral.service.common.MultiTenantDocument;
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

    public static class ContainerNetworkState extends MultiTenantDocument {

        public static final String FIELD_NAME_ID = "id";
        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_DESCRIPTION_LINK = "descriptionLink";
        public static final String FIELD_NAME_IPAM = "ipam";
        public static final String FIELD_NAME_DRIVER = "driver";
        public static final String FIELD_NAME_OPTIONS = "options";
        public static final String FIELD_NAME_ORIGINATIONG_HOST_REFERENCE = "originatingHostReference";
        public static final String FIELD_NAME_ADAPTER_MANAGEMENT_REFERENCE = "adapterManagementReference";
        public static final String FIELD_NAME_COMPOSITE_COMPONENT_LINK = "compositeComponentLink";

        /** Network id provided by the docker host. */
        @Documentation(description = "Network id provider by the docker host.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL, PropertyUsageOption.ID })
        public String id;

        /** The name of a given network. */
        @Documentation(description = "The name of a given network.")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String name;

        /** Defines the description of the network */
        @Documentation(description = "Defines the description of the network.")
        @UsageOption(option = PropertyUsageOption.LINK)
        public String descriptionLink;

        /** Reference to the host that this network was created on. */
        @Documentation(description = "Reference to the host that this network was created on.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public URI originatingHostReference;

        @Documentation(description = "Link to CompositeComponent when a network is part of App/Composition request.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL, PropertyUsageOption.LINK })
        public String compositeComponentLink;

        /** Defines which adapter will serve the provision request */
        @Documentation(description = "Defines which adapter will serve the provision request")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public URI adapterManagementReference;

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

        /**
         * A map of field-value pairs for a given network. These are used to specify network option
         * that are to be used by the network drivers.
         */
        @Documentation(description = "A map of field-value pairs for a given network. These are used"
                + "to specify network options that are used by the network drivers.")
        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND }, usage = {
                PropertyUsageOption.OPTIONAL, PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Map<String, String> options;

        /**
         * A map of field-value pairs for a given network. These key/value pairs are custom tags,
         * properties or attributes that could be used to add additional data or tag the network
         * instance for query and policy purposes.
         */
        @Documentation(description = "A map of field-value pairs for a given network. These key/value pairs are custom tags,"
                + " properties or attributes that could be used to add additional data or tag the network"
                + " instance for query and policy purposes.")
        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND }, usage = {
                PropertyUsageOption.OPTIONAL })
        public Map<String, String> customProperties;

    }

    public ContainerNetworkService() {
        super(ContainerNetworkState.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation startPost) {
        try {
            ContainerNetworkState state = getValidInputFrom(startPost, false);
            logFine("Initial name is %s", state.name);
            startPost.complete();
        } catch (Throwable e) {
            logSevere(e);
            startPost.fail(e);
        }
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
        ContainerNetworkState patchBody = getValidInputFrom(patch, true);

        ServiceDocumentDescription docDesc = getDocumentTemplate().documentDescription;
        String currentSignature = Utils.computeSignature(currentState, docDesc);

        PropertyUtils.mergeServiceDocuments(currentState, patchBody,
                NetworkUtils.SHALLOW_MERGE_SKIP_MAPS_STRATEGY);
        PropertyUtils.mergeCustomProperties(currentState.options, patchBody.options);

        String newSignature = Utils.computeSignature(currentState, docDesc);

        boolean changed = !newSignature.equals(currentSignature);
        if (!changed) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        }
        patch.complete();
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
        }

        NetworkUtils.validateNetworkName(state.name);

        if (state.ipam != null) {
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

    @Override
    public ServiceDocument getDocumentTemplate() {
        ContainerNetworkState template = (ContainerNetworkState) super.getDocumentTemplate();

        template.name = "name (string)";
        template.descriptionLink = UriUtils.buildUriPath(
                ContainerNetworkDescriptionService.FACTORY_LINK,
                "docker-network");

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

        ServiceDocumentTemplateUtil.indexProperty(template,
                ContainerNetworkState.FIELD_NAME_OPTIONS);

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
