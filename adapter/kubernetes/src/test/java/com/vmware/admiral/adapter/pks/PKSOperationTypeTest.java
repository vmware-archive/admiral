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

package com.vmware.admiral.adapter.pks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class PKSOperationTypeTest {

    @Test
    public void testInstanceById() {
        PKSOperationType operationType = PKSOperationType.instanceById(null);
        assertNull(operationType);

        operationType = PKSOperationType.instanceById("-missing-");
        assertNull(operationType);

        operationType = PKSOperationType.instanceById("PKS.ListClusters");
        assertSame(PKSOperationType.LIST_CLUSTERS, operationType);
    }

    @Test
    public void testGetDisplayName() {
        String displayName = PKSOperationType.LIST_CLUSTERS.getDisplayName();
        assertEquals("ListClusters", displayName);
    }

    @Test
    public void testToString() {
        String displayName = PKSOperationType.LIST_CLUSTERS.toString();
        assertEquals("PKS.ListClusters", displayName);
    }

}