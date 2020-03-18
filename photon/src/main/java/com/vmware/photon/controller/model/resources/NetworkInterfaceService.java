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

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.NETWORK_SUBTYPE_NETWORK_INTERFACE_STATE;

import java.net.URI;
import java.util.List;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;
import org.apache.commons.validator.routines.InetAddressValidator;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Represents a network interface.
 */
public class NetworkInterfaceService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_NETWORK_INTERFACES;

    /**
     * Represents the state of a network interface.
     */
    public static class NetworkInterfaceState extends ResourceState {

        public static final String FIELD_NAME_NETWORK_LINK = "networkLink";
        public static final String FIELD_NAME_SUBNET_LINK = "subnetLink";
        public static final String FIELD_NAME_DESCRIPTION_LINK = "networkInterfaceDescriptionLink";

        /**
         * Link to the network this nic is connected to.
         */
        public String networkLink;

        /**
         * Subnet in which this network interface will be created.
         */
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_1)
        public String subnetLink;

        /**
         * [Output only]. Holds the public IP of this interface after provisioning.
         */
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String address;

        /**
         * Firewalls with which this network interface is associated.
         *
         * @deprecated Use {@link #securityGroupLinks} instead.
         */
        @PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL,
                PropertyUsageOption.LINKS })
        @Deprecated
        public List<String> firewallLinks;

        @PropertyOptions(usage = PropertyUsageOption.LINKS)
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_8)
        public List<String> securityGroupLinks;

        /**
         * Holds the device index of this network interface.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_6)
        public int deviceIndex;

        /**
         * The link to the desire state, from which this Network interface was created.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_1)
        public String networkInterfaceDescriptionLink;

        /**
         * Link to the cloud account endpoint the network interface belongs to.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_7)
        public String endpointLink;

        /**
         * Network resource sub-type
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_18)
        @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT)
        public String type = NETWORK_SUBTYPE_NETWORK_INTERFACE_STATE;
    }

    /**
     * Network interface state with in-line, expanded description.
     */
    public static class NetworkInterfaceStateWithDescription extends NetworkInterfaceState {

        /**
         * Network interface description associated with this network interface instance.
         */
        public NetworkInterfaceDescription description;

        public static URI buildUri(URI nicStateUri) {
            return UriUtils.extendUriWithQuery(
                    nicStateUri,
                    UriUtils.URI_PARAM_ODATA_EXPAND,
                    NetworkInterfaceState.FIELD_NAME_DESCRIPTION_LINK);
        }

        public static NetworkInterfaceStateWithDescription create(
                NetworkInterfaceDescription description,
                NetworkInterfaceState state) {

            NetworkInterfaceStateWithDescription stateWithDesc = new NetworkInterfaceStateWithDescription();

            state.copyTo(stateWithDesc);

            // Populate 'stateWithDesc' from 'state'
            stateWithDesc.address = state.address;
            stateWithDesc.networkLink = state.networkLink;
            stateWithDesc.subnetLink = state.subnetLink;
            stateWithDesc.securityGroupLinks = state.securityGroupLinks;
            stateWithDesc.deviceIndex = state.deviceIndex;

            // Then extend with 'description' data
            stateWithDesc.networkInterfaceDescriptionLink = description.documentSelfLink;
            stateWithDesc.description = description;

            return stateWithDesc;
        }

    }

    public NetworkInterfaceService() {
        super(NetworkInterfaceState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
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
    public void handleGet(Operation get) {

        NetworkInterfaceState currentState = getState(get);

        boolean doExpand = get.getUri().getQuery() != null &&
                UriUtils.hasODataExpandParamValue(get.getUri());

        if (!doExpand) {
            get.setBody(currentState).complete();
            return;
        }

        // retrieve the description and include in an augmented version of our
        // state.
        Operation getDesc = Operation
                .createGet(this, currentState.networkInterfaceDescriptionLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                get.fail(e);
                                return;
                            }
                            NetworkInterfaceDescription desc = o
                                    .getBody(NetworkInterfaceDescription.class);

                            NetworkInterfaceStateWithDescription stateWithDesc = NetworkInterfaceStateWithDescription
                                    .create(desc, currentState);

                            get.setBody(stateWithDesc).complete();
                        });
        sendRequest(getDesc);
    }

    @Override
    public void handlePut(Operation put) {
        try {
            NetworkInterfaceState returnState = processInput(put);
            returnState.copyTenantLinks(getState(put));
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        NetworkInterfaceState currentState = getState(patch);
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                NetworkInterfaceState.class, t -> {
                    NetworkInterfaceState patchBody = patch.getBody(NetworkInterfaceState.class);
                    boolean hasStateChanged = false;
                    if (patchBody.endpointLink != null && currentState.endpointLink == null) {
                        currentState.endpointLink = patchBody.endpointLink;
                        hasStateChanged = true;
                    }
                    if (patchBody.securityGroupLinks != null) {
                        if (currentState.securityGroupLinks == null) {
                            currentState.securityGroupLinks = patchBody.securityGroupLinks;
                            hasStateChanged = true;
                        } else {
                            for (String link : patchBody.securityGroupLinks) {
                                if (!currentState.securityGroupLinks.contains(link)) {
                                    currentState.securityGroupLinks.add(link);
                                    hasStateChanged = true;
                                }
                            }
                        }
                    }
                    return hasStateChanged;
                });
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(template);
        return template;
    }

    private NetworkInterfaceState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        NetworkInterfaceState state = op.getBody(NetworkInterfaceState.class);
        validateState(state);
        return state;
    }

    private void validateState(NetworkInterfaceState state) {
        Utils.validateState(getStateDescription(), state);

        if (state.address != null) {
            if (state.subnetLink == null) {
                throw new IllegalArgumentException(
                        "SubnetLink must not be null when address is set");
            }
            if (!InetAddressValidator.getInstance().isValidInet4Address(
                    state.address)) {
                throw new IllegalArgumentException("IP address is invalid");
            }
        }

        if (state.networkLink == null && state.subnetLink == null) {
            throw new IllegalArgumentException(
                    "Either subnetLink or networkLink must be set");
        }
    }
}
