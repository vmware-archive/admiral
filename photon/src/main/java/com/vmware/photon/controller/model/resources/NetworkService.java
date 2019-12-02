/*
 * Copyright (c) 2018-2019 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.resources;

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.NETWORK_SUBTYPE_NETWORK_STATE;

import java.net.URI;
import java.util.UUID;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import org.apache.commons.net.util.SubnetUtils;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Represents a network resource.
 */
public class NetworkService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_NETWORKS;

    /**
     * Network State document.
     */
    public static class NetworkState extends ResourceState {
        public static final String FIELD_NAME_ADAPTER_MANAGEMENT_REFERENCE = "adapterManagementReference";
        public static final String FIELD_NAME_AUTH_CREDENTIALS_LINK = "authCredentialsLink";

        /**
         * Subnet CIDR
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String subnetCIDR;

        /**
         * Link to secrets. Required
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String authCredentialsLink;

        /**
         * The pool which this resource is a part of. Required
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String resourcePoolLink;

        /**
         * The network adapter to use to create the network. Required
         */
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI instanceAdapterReference;

        /**
         * Reference to the management endpoint of the compute provider.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public URI adapterManagementReference;

        /**
         * Link to the cloud account endpoint the network belongs to.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_5_7)
        public String endpointLink;

        /**
         * Network resource sub-type
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_18)
        @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT)
        public String type = NETWORK_SUBTYPE_NETWORK_STATE;
    }

    public NetworkService() {
        super(NetworkState.class);
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
    public void handlePut(Operation put) {
        try {
            NetworkState returnState = processInput(put);
            returnState.copyTenantLinks(getState(put));
            setState(put, returnState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    private NetworkState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        NetworkState state = op.getBody(NetworkState.class);
        validateState(state);
        return state;
    }

    public void validateState(NetworkState state) {
        Utils.validateState(getStateDescription(), state);
        PhotonModelUtils.validateRegionId(state);

        // do we have a subnet in CIDR notation
        // creating new SubnetUtils to validate
        if (state.subnetCIDR != null) {
            new SubnetUtils(state.subnetCIDR);
        }
    }

    @Override
    public void handlePost(Operation post) {
        try {
            NetworkState returnState = processInput(post);
            setState(post, returnState);
            post.complete();
        } catch (Throwable t) {
            post.fail(t);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        NetworkState currentState = getState(patch);
        ResourceUtils.handlePatch(patch, currentState, getStateDescription(),
                NetworkState.class, t -> {
                    NetworkState patchBody = patch.getBody(NetworkState.class);
                    boolean hasStateChanged = false;
                    if (patchBody.endpointLink != null && currentState.endpointLink == null) {
                        currentState.endpointLink = patchBody.endpointLink;
                        hasStateChanged = true;
                    }
                    return hasStateChanged;
                });
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(td);
        NetworkState template = (NetworkState) td;

        template.id = UUID.randomUUID().toString();
        template.subnetCIDR = "10.1.0.0/16";
        template.name = "cell-network";

        return template;
    }
}
