/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.photon.controller.model.resources.ComputeService;

public class ContainerHostUtilTest {

    @Test
    public void testGetContainerHostTypesForResourceType() {
        List<ContainerHostType> allTypes = new ArrayList<>(
                Arrays.asList(ContainerHostType.values()));

        List<ContainerHostType> k8sTypes = ContainerHostUtil
                .getContainerHostTypesForResourceType(ResourceType.KUBERNETES_DEPLOYMENT_TYPE);
        assertEquals(Collections.singletonList(ContainerHostType.KUBERNETES), k8sTypes);
        k8sTypes = ContainerHostUtil
                .getContainerHostTypesForResourceType(ResourceType.KUBERNETES_POD_TYPE);
        assertEquals(Collections.singletonList(ContainerHostType.KUBERNETES), k8sTypes);
        k8sTypes = ContainerHostUtil.getContainerHostTypesForResourceType(
                ResourceType.KUBERNETES_REPLICATION_CONTROLLER_TYPE);
        assertEquals(Collections.singletonList(ContainerHostType.KUBERNETES), k8sTypes);
        k8sTypes = ContainerHostUtil
                .getContainerHostTypesForResourceType(ResourceType.KUBERNETES_SERVICE_TYPE);
        assertEquals(Collections.singletonList(ContainerHostType.KUBERNETES), k8sTypes);

        allTypes.remove(ContainerHostType.KUBERNETES);
        List<ContainerHostType> otherTypes = ContainerHostUtil
                .getContainerHostTypesForResourceType(ResourceType.CONTAINER_TYPE);
        assertEquals(allTypes, otherTypes);
    }

    @Test
    public void testIsVic() {
        final String driver = ContainerHostUtil.VMWARE_VIC_DRIVER2;
        ComputeService.ComputeState state = new ComputeService.ComputeState();
        state.customProperties = new HashMap<>();
        state.customProperties.put(ContainerHostUtil.PROPERTY_NAME_DRIVER, driver);

        boolean result = ContainerHostUtil.isVicHost(state);
        assertTrue(result);

        // nagative test
        state.customProperties = null;
        result = ContainerHostUtil.isVicHost(state);
        assertFalse(result);
    }

    @Test
    public void testIsKubernetesHost() {
        ComputeService.ComputeState state = new ComputeService.ComputeState();
        state.customProperties = new HashMap<>();
        state.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.KUBERNETES.name());

        boolean result = ContainerHostUtil.isKubernetesHost(state);
        assertTrue(result);

        // negative test
        state.customProperties = null;
        result = ContainerHostUtil.isKubernetesHost(state);
        assertFalse(result);
    }

    @Test
    public void testGetDriver() {
        final String driver = "overlay";
        ComputeService.ComputeState state = new ComputeService.ComputeState();
        state.customProperties = new HashMap<>();
        state.customProperties.put(ContainerHostUtil.PROPERTY_NAME_DRIVER, driver);

        String result = ContainerHostUtil.getDriver(state);
        assertEquals(driver, result);

        // negative test
        state.customProperties = null;
        result = ContainerHostUtil.getDriver(state);
        assertNull(result);
    }

    @Test
    public void testGetTrustAlias() {
        final String alias = "alias";
        ComputeService.ComputeState state = new ComputeService.ComputeState();
        state.customProperties = new HashMap<>();
        state.customProperties.put(ContainerHostService.SSL_TRUST_ALIAS_PROP_NAME, alias);

        String result = ContainerHostUtil.getTrustAlias(state);
        assertEquals(alias, result);

        // negative test
        state.customProperties = null;
        result = ContainerHostUtil.getTrustAlias(state);
        assertNull(result);
    }

    @Test
    public void testIsSchedulerHost() {
        final String alias = "alias";
        ComputeService.ComputeState state = new ComputeService.ComputeState();
        state.customProperties = new HashMap<>();
        state.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.KUBERNETES.name());
        state.customProperties.put(ContainerHostUtil.PROPERTY_NAME_DRIVER,
                ContainerHostUtil.VMWARE_VIC_DRIVER2);

        boolean result = ContainerHostUtil.isSchedulerHost(state);
        assertTrue(result);

        // negative test
        state.customProperties = new HashMap<>();
        result = ContainerHostUtil.isSchedulerHost(state);
        assertFalse(result);
    }

    @Test
    public void testIsSupportedVchVersion() {
        assertTrue(ContainerHostUtil.isSupportedVchVersion("1.0.0", null, null));
        assertTrue(ContainerHostUtil.isSupportedVchVersion("1.0.0", "1.0.0", null));
        assertTrue(ContainerHostUtil.isSupportedVchVersion("1.0.0", null, "1.1.0"));
        assertTrue(ContainerHostUtil.isSupportedVchVersion("1.0.0", "1.0.0", "1.1.0"));

        assertFalse(ContainerHostUtil.isSupportedVchVersion("1.0.0", null, "1.0.0"));
        assertFalse(ContainerHostUtil.isSupportedVchVersion("1.1.0", null, "1.0.0"));
        assertFalse(ContainerHostUtil.isSupportedVchVersion("1.1", "1.1.1", null));
        assertFalse(ContainerHostUtil.isSupportedVchVersion("1.0", "2.0", null));
    }
}