/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerHostUtil {

    public static final String CONTAINER_HOST_TYPE_NOT_SUPPORTED_MESSAGE_FORMAT = "Container "
            + "host type '%s' is not supported";
    public static final String CONTAINER_HOST_TYPE_NOT_SUPPORTED_MESSAGE_CODE = "compute.host.type.not.supported";

    private static final String PROPERTY_NAME_DRIVER = "__Driver";
    private static final String VMWARE_VIC_DRIVER1 = "vmware";
    private static final String VMWARE_VIC_DRIVER2 = "vsphere";

    /**
     * Check if this host is a scheduler host (e.g. VIC, Kubernetes)
     *
     * @param computeState
     * @return boolean value
     */
    public static boolean isSchedulerHost(ComputeState computeState) {
        return ContainerHostUtil.isVicHost(computeState)
                || ContainerHostUtil.isKubernetesHost(computeState);
    }

    /**
     * Check if this host should be treated as scheduler host. Note that a host may be a scheduler
     * (e.g. VIC, Kubernetes) but it may be declared as plain docker host and in this case the host
     * should be treated as a plain docker host
     *
     * @param computeState
     * @return boolean value
     */
    public static boolean isTreatedLikeSchedulerHost(ComputeState computeState) {
        return getDeclaredContainerHostType(computeState) != ContainerHostType.DOCKER;
    }

    public static ContainerHostType getDeclaredContainerHostType(ComputeState computeState) {
        AssertUtil.assertNotNull(computeState, "computeState");

        if (computeState.customProperties == null) {
            return ContainerHostType.getDefaultHostType();
        }

        String hostTypeRaw = computeState.customProperties
                .get(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME);
        if (hostTypeRaw == null) {
            return ContainerHostType.getDefaultHostType();
        }

        try {
            return ContainerHostType.valueOf(hostTypeRaw);
        } catch (IllegalArgumentException ex) {
            String error = String.format(CONTAINER_HOST_TYPE_NOT_SUPPORTED_MESSAGE_FORMAT,
                    hostTypeRaw);
            throw new LocalizableValidationException(ex, error,
                    CONTAINER_HOST_TYPE_NOT_SUPPORTED_MESSAGE_CODE, hostTypeRaw);
        }
    }

    public static List<ContainerHostType> getContainerHostTypesForResourceType(
            ResourceType resourceType) {

        switch (resourceType) {
        case KUBERNETES_DEPLOYMENT_TYPE:
        case KUBERNETES_POD_TYPE:
        case KUBERNETES_REPLICATION_CONTROLLER_TYPE:
        case KUBERNETES_SERVICE_TYPE:
            return Collections.singletonList(ContainerHostType.KUBERNETES);
        default:
            return Arrays.asList(ContainerHostType.DOCKER, ContainerHostType.VCH);

        }
    }

    /**
     * Check if docker is running on VMware Integrated Container host.
     *
     * @param computeState host to check
     * @return boolean value
     */
    public static boolean isVicHost(ComputeState computeState) {
        boolean vic = false;

        if (computeState != null && computeState.customProperties != null) {
            String driver = computeState.customProperties.get(PROPERTY_NAME_DRIVER);
            driver = driver != null ? driver.toLowerCase().trim() : "";
            vic = driver.startsWith(VMWARE_VIC_DRIVER1) || driver.startsWith(VMWARE_VIC_DRIVER2);
        }

        return vic;
    }

    /**
     * Check if host is running Kubernetes.
     *
     * @param computeState host to check
     * @return boolean value
     */
    public static boolean isKubernetesHost(ComputeState computeState) {
        if (computeState == null || computeState.customProperties == null) {
            return false;
        }
        String hostType = computeState.customProperties.get(
                ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME);
        return (hostType != null && hostType.equals(ContainerHostType.KUBERNETES.name()));
    }

    /**
     * Gets trust alias property value from host custom properties.
     */
    public static String getTrustAlias(ComputeState computeState) {
        if (computeState != null && computeState.customProperties != null) {
            return computeState.customProperties
                    .get(ContainerHostService.SSL_TRUST_ALIAS_PROP_NAME);
        }
        return null;
    }

    /**
     * Returns whether the trust alias should be set and it is not (e.g. because the upgrade of an
     * instance with hosts already configured)
     */
    public static boolean isTrustAliasMissing(ComputeState computeState) {
        URI hostUri = ContainerDescription.getDockerHostUri(computeState);
        return UriUtils.HTTPS_SCHEME.equalsIgnoreCase(hostUri.getScheme())
                && (getTrustAlias(computeState) == null);
    }

    public static void filterKubernetesHostLinks(Service sender, Set<String> hostLinks,
            BiConsumer<Set<String>, Map<Long, Throwable>> callback) {
        List<Operation> getHosts = new ArrayList<>();
        for (String hostLink : hostLinks) {
            getHosts.add(Operation.createGet(sender, hostLink));
        }

        OperationJoin.create(getHosts)
                .setCompletion((ops, errs) -> {
                    Set<String> kubernetesHostLinks = new HashSet<>();
                    if (errs != null && !errs.isEmpty()) {
                        callback.accept(null, errs);
                    } else {
                        for (Operation op : ops.values()) {
                            if (op == null || op.getStatusCode() != Operation.STATUS_CODE_OK) {
                                continue;
                            }
                            ComputeState state = op.getBody(ComputeState.class);
                            if (isKubernetesHost(state)) {
                                kubernetesHostLinks.add(state.documentSelfLink);
                            }
                        }
                        callback.accept(kubernetesHostLinks, null);
                    }
                }).sendWith(sender);
    }

    /**
     * Deletes a auto-generated resources (placement zone, placement) that were created as part of a
     * failed request to add a host.
     */
    public static void cleanupAutogeneratedResources(Service requestorService,
            Collection<String> resourceLinks) {
        AssertUtil.assertNotNull(requestorService, "requestorService");
        AssertUtil.assertNotNull(resourceLinks, "resourceLinks");

        ServiceHost serviceHost = requestorService.getHost();
        for (String resourceLink : resourceLinks) {
            serviceHost.log(Level.FINE, "Cleanup for autogenerated resource %s", resourceLink);
            Operation.createDelete(serviceHost, resourceLink)
                    .setReferer(requestorService.getUri())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            serviceHost.log(Level.WARNING,
                                    "Failed cleanup for auto-generated resource %s: %s",
                                    resourceLink, Utils.toString(e));
                        } else {
                            serviceHost.log(Level.FINE,
                                    "Successful cleanup for auto-generated resource %s",
                                    resourceLink);
                        }
                    }).sendWith(serviceHost);
        }
    }

}
