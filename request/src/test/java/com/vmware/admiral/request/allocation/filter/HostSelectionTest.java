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

package com.vmware.admiral.request.allocation.filter;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.DescName;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;

public class HostSelectionTest {
    private HostSelection hostSelection;

    @Before
    public void setUp() {
        hostSelection = new HostSelection();
    }

    @Test
    public void testNames() throws Exception {
        String name1 = "test1";
        String name1_cont = name1 + "-dcp234";
        hostSelection.addDesc(createName(name1, name1_cont));

        String name2 = "test2";
        String name2_cont = name2 + "-dcp564";
        String alias2 = ":alias2";
        hostSelection.addDesc(createName(name2, name2_cont));

        String name3 = "test3";
        String name3_cont = name3 + "-dcp387";
        hostSelection.addDesc(createName(name3, name3_cont));

        String[] links = new String[] { name1, name2 + alias2, name3_cont };

        String[] containerNames = hostSelection.mapNames(links);

        assertEquals(name1_cont, containerNames[0]);
        assertEquals(name2_cont + alias2, containerNames[1]);
        assertEquals(name3_cont, containerNames[2]);
    }

    private DescName createName(String name, String containerName) {
        DescName descName = new DescName();
        descName.descriptionName = name;
        descName.addResourceNames(Arrays.asList(containerName));

        return descName;
    }
}
