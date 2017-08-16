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

package com.vmware.admiral.service.common;

import java.util.Map.Entry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.services.common.NodeGroupService.NodeGroupState;
import com.vmware.xenon.services.common.NodeState;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class ClusterMonitoringServiceTest extends BaseTestCase {

    private TestRequestSender sender;

    @Before
    public void setUp() throws Throwable {
        sender = host.getTestRequestSender();
        System.setProperty(ClusterMonitoringService.AUTOMATIC_QUORM_UPDATE_PROPERTY, Boolean.TRUE
                .toString());
        host.startServiceAndWait(ClusterMonitoringService.class, ClusterMonitoringService
                .SELF_LINK);
        waitForServiceAvailability(ClusterMonitoringService.SELF_LINK);
    }

    @Test
    public void testUpdateQuorumOnStart() {

        NodeGroupState nodeGroupState = sender
                .sendGetAndWait(UriUtils.buildUri(host, ServiceUriPaths.DEFAULT_NODE_GROUP),
                        NodeGroupState.class);
        Assert.assertNotNull(nodeGroupState);
        Assert.assertEquals(1, nodeGroupState.nodes.size());
        Entry<String, NodeState> node = nodeGroupState.nodes.entrySet().stream()
                .findFirst().get();

        Assert.assertEquals(1, node.getValue().membershipQuorum);

    }

}
