/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost.ServiceAlreadyStartedException;

public class OperationUtilTest extends BaseTestCase {

    @Test
    public void testExtractTenantFromBGHeader() throws Throwable {
        String businessGroup = "/tenants/qe/groups/22c64610-84d0-49e9-8dd7-dee32539dcfe";
        Operation op = new Operation();
        op.addRequestHeader(OperationUtil.PROJECT_ADMIRAL_HEADER, businessGroup);
        String tenantLink = OperationUtil.extractTenantFromProjectHeader(op);
        Assert.assertTrue(tenantLink.equals("/tenants/qe"));
    }

    @Test
    public void testExtractTenantFromBGHeaderInvalid() throws Throwable {
        String businessGroup = "test";
        Operation op = new Operation();
        op.addRequestHeader(OperationUtil.PROJECT_ADMIRAL_HEADER, businessGroup);
        String tenantLink = OperationUtil.extractTenantFromProjectHeader(op);
        Assert.assertTrue(tenantLink == null);
    }

    @Test
    public void testIsServiceAlreadyStarted() {
        assertFalse(OperationUtil.isServiceAlreadyStarted(null, null));
        assertFalse(OperationUtil.isServiceAlreadyStarted(new Exception(), null));
        assertFalse(OperationUtil.isServiceAlreadyStarted(new Exception(), new Operation()));

        assertTrue(OperationUtil
                .isServiceAlreadyStarted(new ServiceAlreadyStartedException("simulated"), null));
        assertTrue(OperationUtil.isServiceAlreadyStarted(
                new Exception(new ServiceAlreadyStartedException("simulated")), null));
        assertTrue(OperationUtil.isServiceAlreadyStarted(null,
                new Operation().setStatusCode(Operation.STATUS_CODE_CONFLICT)));
    }
}
