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

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.UriUtils;

public class ContainerHostUtil {

    private static final String PROPERTY_NAME_DRIVER = "__Driver";
    private static final String VMWARE_VIC_DRIVER1 = "vmware";
    private static final String VMWARE_VIC_DRIVER2 = "vsphere";

    /**
     * Check if docker is running on VMware Integrated Container host.
     *
     * @param computeState
     *            host to check
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

}
