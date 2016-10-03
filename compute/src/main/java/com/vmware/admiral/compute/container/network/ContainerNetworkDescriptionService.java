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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.ServiceDocumentTemplateUtil;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.CloneableResource;
import com.vmware.admiral.compute.content.YamlMapper;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

public class ContainerNetworkDescriptionService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.CONTAINER_NETWORK_DESC;

    @JsonFilter(YamlMapper.SERVICE_DOCUMENT_FILTER)
    @JsonIgnoreProperties({ "customProperties" })
    public static class ContainerNetworkDescription extends ResourceState
            implements CloneableResource {

        public static String CONTAINER_NETWORK_TYPE = "CONTAINER_NETWORK";

        public static final String NETWORK_DRIVER_BRIDGE = "bridge";
        public static final String NETWORK_DRIVER_OVERLAY = "overlay";

        public static final String FIELD_NAME_IPAM = "ipam";
        public static final String FIELD_NAME_DRIVER = "driver";
        public static final String FIELD_NAME_OPTIONS = "options";

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
         * Composite Template use only. If set to true, specifies that this network exists outside
         * of the Composite Template.
         */
        @Documentation(description = "Composite Template use only. If set to true, specifies that "
                + "this network exists outside of the Composite Template.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Boolean external;

        /**
         * Composite Template use only. The name of the external network. If set then the value of
         * the attribute 'external' is considered 'true'.
         */
        @Documentation(description = "Composite Template use only. The name of the external network."
                + " If set then the value of the attribute 'external' is considered 'true'.")
        @JsonProperty("external_name")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String externalName;

        /** Instance Adapter reference for provisioning of containers */
        @Documentation(description = "Instance Adapter reference for provisioning of containers.")
        @JsonIgnore
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public URI instanceAdapterReference;

        /** Link to the parent network description */
        @JsonProperty("parent_description_link")
        @Documentation(description = "Link to the parent network description.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String parentDescriptionLink;

        @JsonAnySetter
        private void putProperty(String key, String value) {
            if (customProperties == null) {
                customProperties = new HashMap<>();
            }
            customProperties.put(key, value);
        }

        @JsonAnyGetter
        private Map<String, String> getProperties() {
            return customProperties;
        }

        @Override
        public Operation createCloneOperation(Service sender) {
            this.parentDescriptionLink = this.documentSelfLink;
            this.documentSelfLink = null;
            return Operation.createPost(sender, FACTORY_LINK)
                    .setBody(this);
        }
    }

    public ContainerNetworkDescriptionService() {
        super(ContainerNetworkDescription.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation startPost) {
        try {
            ContainerNetworkDescription state = getValidInputFrom(startPost, false);
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
            ContainerNetworkDescription putState = getValidInputFrom(put, false);
            setState(put, putState);
            put.setBody(putState).complete();
        } catch (Throwable e) {
            logSevere(e);
            put.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ContainerNetworkDescription currentState = getState(patch);
        ContainerNetworkDescription patchBody = getValidInputFrom(patch, true);

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
     * Validates the specified {@link ContainerNetworkDescription}. If the validation fails, an
     * Exception will be thrown.
     *
     * @param isUpdate
     *            on updates the check for non <code>null</code> required fields is skipped.
     *            <code>null</code> values in that case represent no change. PATCH method is
     *            considered an update. PUT is not an update.
     */
    public void validateState(ContainerNetworkDescription state, boolean isUpdate) {
        if (!isUpdate) {
            // check that all required fields are not null.
            // Skip this step on updates (null = no update)
            Utils.validateState(getStateDescription(), state);
            NetworkUtils.validateNetworkName(state.name);
        }

        if (state.instanceAdapterReference == null) {
            state.instanceAdapterReference = UriUtilsExtended.buildUri(getHost(),
                    ManagementUriParts.ADAPTER_DOCKER_NETWORK);
        }

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
        ContainerNetworkDescription template = (ContainerNetworkDescription) super.getDocumentTemplate();

        template.name = "name (string)";

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
                ContainerNetworkDescription.FIELD_NAME_OPTIONS);

        return template;
    }

    /**
     * Returns valid {@link ContainerNetworkDescription} instance for the specified operation or
     * throws an Exception if validation fails.
     */
    private ContainerNetworkDescription getValidInputFrom(Operation op, boolean isUpdate) {
        checkForBody(op);
        ContainerNetworkDescription incomingState = op.getBody(ContainerNetworkDescription.class);
        validateState(incomingState, isUpdate);
        return incomingState;
    }

}
