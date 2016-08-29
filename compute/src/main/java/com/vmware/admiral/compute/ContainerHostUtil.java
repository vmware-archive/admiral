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

package com.vmware.admiral.compute;

import java.util.List;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Service;

public class ContainerHostUtil {

    public static final String HOST_ID_TENANT_SEPARATOR = "::";

    private static final String PROPERTY_NAME_DRIVER = "__Driver";
    private static final String VMWARE_VIC_DRIVER1 = "vmware";
    private static final String VMWARE_VIC_DRIVER2 = "vsphere";


    /**
     * Build host id
     * <ul>
     * <li>in a group returns group::hostId
     * <li>not in a group returns hostId
     * </ul>
     *
     * @param tenantLinks the tenant links, or null if no group
     * @param hostId the host id
     * @return the host id
     */
    public static String buildHostId(List<String> tenantLinks, String hostId) {
        AssertUtil.assertNotNull(hostId, "hostId");
        String group = extractGroup(tenantLinks);
        String id = extractHostId(hostId);
        if (group == null || group.isEmpty()) {
            return id;
        } else {
            return group + HOST_ID_TENANT_SEPARATOR + id;
        }
    }

    /**
     * Extracts the id from a host id
     *
     * @param hostId the host id created with {@link #buildHostId(List, String)}
     * @return the host id without the group and separator
     */
    public static String extractHostId(String hostId) {
        AssertUtil.assertNotNull(hostId, "hostId");
        int idx = hostId.indexOf(HOST_ID_TENANT_SEPARATOR);
        String id = hostId;
        if (idx != -1) {
            id = hostId.substring(idx + HOST_ID_TENANT_SEPARATOR.length());
        }
        return id;
    }

    /**
     *
     * @param tenantLinks
     * @return
     */
    public static String extractGroup(List<String> tenantLinks) {
        if (tenantLinks != null && !tenantLinks.isEmpty()) {
            return Service.getId(tenantLinks.get(0));
        } else {
            return "";
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
}
