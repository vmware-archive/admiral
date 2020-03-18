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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;
import org.apache.commons.validator.routines.InetAddressValidator;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Represents a re-usable description for a network interface instance.
 */
public class NetworkInterfaceDescriptionService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_NETWORK_INTERFACE_DESCRIPTIONS;

    public enum IpAssignment {
        /**
         * Static networking allows you to specify a subnet, from which to choose available IP. By
         * default an automatically IP reservation is used. But one can use also a static IP
         * reservation.
         */
        STATIC,
        /**
         * Dynamic networking defers IP selection to the Infrastructure layer. The IPs are
         * automatically reservation.
         */
        DYNAMIC
    }

    /**
     * Represents the state of a network interface description.
     */
    public static class NetworkInterfaceDescription extends ResourceState {

        /**
         * The static IP of the interface. Optional.
         */
        @PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String address;

        /**
         * IP assignment type the use. By default dynamic s used.
         */
        @PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public IpAssignment assignment;

        /**
         * Holds the device index of this network interface.
         */
        public int deviceIndex;

        /**
         * Firewalls with which this compute instance is associated.
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
         * Link to the network this nic is connected to.
         */
        public String networkLink;

        /**
         * Subnet in which the network interface will be created.
         */
        public String subnetLink;

        /**
         * Link to the endpoint the network interface belongs to.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_8)
        public String endpointLink;

        /**
         * Indicates whether this NIC should be attached to a network with public IpAddress.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_9)
        @PropertyOptions(usage = { PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Boolean assignPublicIpAddress;

    }

    public NetworkInterfaceDescriptionService() {
        super(NetworkInterfaceDescription.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleStart(Operation start) {
        processInput(start);
        start.complete();
    }

    @Override
    public void handlePut(Operation put) {
        NetworkInterfaceDescription returnState = processInput(put);
        returnState.copyTenantLinks(getState(put));
        setState(put, returnState);
        put.complete();
    }

    private NetworkInterfaceDescription processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        NetworkInterfaceDescription state = op.getBody(NetworkInterfaceDescription.class);
        validateState(state);
        return state;
    }

    @Override
    public void handlePatch(Operation patch) {
        NetworkInterfaceDescription currentState = getState(patch);
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                NetworkInterfaceDescription.class, t -> {
                    NetworkInterfaceDescription patchBody =
                            patch.getBody(NetworkInterfaceDescription.class);
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

    private void validateState(NetworkInterfaceDescription state) {
        if (state.assignment == null) {
            state.assignment = IpAssignment.DYNAMIC;
        }

        Utils.validateState(getStateDescription(), state);

        if (state.address != null) {
            if (state.assignment != IpAssignment.STATIC) {
                throw new IllegalArgumentException(
                        "IP can be reserved only when assignment is STATIC");
            }
            if (!InetAddressValidator.getInstance().isValidInet4Address(
                    state.address)) {
                throw new IllegalArgumentException("IP address is invalid");
            }

        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        NetworkInterfaceDescription template = (NetworkInterfaceDescription) td;

        template.id = UUID.randomUUID().toString();
        template.name = "my-nic";
        template.subnetLink = UriUtils.buildUriPath(SubnetService.FACTORY_LINK,
                "sub-network");
        template.assignment = IpAssignment.STATIC;
        template.address = "10.1.0.12";
        template.securityGroupLinks = Collections
                .singletonList(UriUtils.buildUriPath(SecurityGroupService.FACTORY_LINK,
                        "security-group-one"));
        template.deviceIndex = 0;
        template.assignPublicIpAddress = Boolean.TRUE;

        return template;
    }
}
