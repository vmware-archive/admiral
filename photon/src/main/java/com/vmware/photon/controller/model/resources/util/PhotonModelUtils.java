/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.resources.util;

import static java.util.Collections.singletonMap;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceStateWithDescription;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class PhotonModelUtils {

    /**
     * The set of ResourceStates which support {@code endpointLink} property through
     * <b>explicit</b>field.
     */
    public static final Set<Class<? extends ResourceState>> ENDPOINT_LINK_EXPLICIT_SUPPORT;

    static {
        Set<Class<? extends ResourceState>> set = new HashSet<>();
        set.add(ComputeDescription.class);
        set.add(ComputeState.class);
        set.add(ComputeStateWithDescription.class);
        set.add(DiskState.class);
        set.add(ImageState.class);
        set.add(NetworkInterfaceDescription.class);
        set.add(NetworkInterfaceState.class);
        set.add(NetworkInterfaceStateWithDescription.class);
        set.add(NetworkState.class);
        set.add(SecurityGroupState.class);
        set.add(StorageDescription.class);
        set.add(SubnetState.class);

        ENDPOINT_LINK_EXPLICIT_SUPPORT = Collections.unmodifiableSet(set);
    }

    /**
     * The set of ServiceDocuments which support {@code endpointLink} property through <b>custom
     * property</b>.
     */
    public static final Set<Class<? extends ServiceDocument>> ENDPOINT_LINK_CUSTOM_PROP_SUPPORT;

    static {
        Set<Class<? extends ServiceDocument>> set = new HashSet<>();
        set.add(AuthCredentialsServiceState.class);
        set.add(ResourceGroupState.class);

        ENDPOINT_LINK_CUSTOM_PROP_SUPPORT = Collections.unmodifiableSet(set);
    }

    public static <T extends ServiceDocument> T setEndpointLink(T state, String endpointLink) {

        if (state == null) {
            return state;
        }

        if (ENDPOINT_LINK_EXPLICIT_SUPPORT.contains(state.getClass())) {

            ServiceDocumentDescription sdDesc = ServiceDocumentDescription.Builder.create()
                    .buildDescription(state.getClass());

            ReflectionUtils.setPropertyValue(
                    sdDesc.propertyDescriptions.get(PhotonModelConstants.FIELD_NAME_ENDPOINT_LINK),
                    state,
                    endpointLink);

        } else if (ENDPOINT_LINK_CUSTOM_PROP_SUPPORT.contains(state.getClass())) {

            ServiceDocumentDescription sdDesc = ServiceDocumentDescription.Builder.create()
                    .buildDescription(state.getClass());

            if (endpointLink != null && !endpointLink.isEmpty()) {
                ReflectionUtils.setOrUpdatePropertyValue(
                        sdDesc.propertyDescriptions.get(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES),
                        state,
                        singletonMap(PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK, endpointLink));
            }
        }

        return state;
    }

    public static void handleIdempotentPut(StatefulService s, Operation put) {

        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            // converted PUT due to IDEMPOTENT_POST option
            s.logFine(() -> String.format("Task %s has already started. Ignoring converted PUT.",
                    put.getUri()));
            put.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            put.complete();
            return;
        }

        // normal PUT is not supported
        put.fail(Operation.STATUS_CODE_BAD_METHOD);
    }

    public static void validateRegionId(ResourceState resourceState) {
        if (resourceState.regionId == null) {
            throw (new IllegalArgumentException("regionId is required"));
        }
    }

    /**
     * Merges two lists of strings, filtering duplicate elements from the second one (the patch).
     * Also keeping track if change of source list has been modified.
     * @param source The source list (can be null).
     * @param patch The patch list. If null, the @source will be the result.
     * @return Returns a pair. The left part is the merged list and the right one is the boolean
     * value, indicating if the changes to @source is modified.
     */
    public static Pair<List<String>, Boolean> mergeLists(List<String> source, List<String> patch) {
        if (patch == null) {
            return new ImmutablePair<>(source, Boolean.FALSE);
        }
        boolean hasChanged = false;
        List<String> result = source;
        if (result == null) {
            result = patch;
            hasChanged = true;
        } else {
            for (String newValue : patch) {
                if (!result.contains(newValue)) {
                    result.add(newValue);
                    hasChanged = true;
                }
            }
        }
        return new ImmutablePair<>(result, Boolean.valueOf(hasChanged));
    }
}
