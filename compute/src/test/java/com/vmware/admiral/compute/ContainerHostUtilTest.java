/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;

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

}
