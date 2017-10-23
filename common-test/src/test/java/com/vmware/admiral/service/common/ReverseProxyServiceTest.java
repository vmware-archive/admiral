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

package com.vmware.admiral.service.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.function.Function;

import org.junit.Test;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;

public class ReverseProxyServiceTest extends BaseTestCase {

    @Override
    public void before() throws Throwable {
        super.before();

        host.startServiceAndWait(ConfigurationFactoryService.class,
                ConfigurationFactoryService.SELF_LINK);

        host.startService(ReverseProxyService.class.newInstance());
        host.startService(MockPingService.class.newInstance());

        host.waitForServiceAvailable(ReverseProxyService.SELF_LINK);
        host.waitForServiceAvailable(MockPingService.SELF_LINK);

        ConfigurationState config = new ConfigurationState();
        config.key = ConfigurationUtil.ALLOW_SSH_CONSOLE_PROPERTY;
        config.value = "true";
        config.documentSelfLink = config.key;
        doPost(config, ConfigurationFactoryService.SELF_LINK);
    }

    @Test
    public void testGet() throws Throwable {
        testOperation(Operation::createGet, null);
    }

    @Test
    public void testPost() throws Throwable {
        testOperation(Operation::createPost, null);
    }

    @Test
    public void testPatch() throws Throwable {
        testOperation(Operation::createPatch, MockPingService.BODY_PING);
    }

    @Test
    public void testPut() throws Throwable {
        testOperation(Operation::createPut, MockPingService.BODY_PING);
    }

    @Test
    public void testDelete() throws Throwable {
        testOperation(Operation::createDelete, null);
    }

    @Test
    public void testOptions() throws Throwable {
        testOperation(Operation::createOptions, null);
    }

    @Test
    public void testOperationForbiddenWhenEmbedded() throws Throwable {

        ConfigurationState config = new ConfigurationState();
        config.key = ConfigurationUtil.EMBEDDED_MODE_PROPERTY;
        config.value = "true";
        config.documentSelfLink = config.key;
        doPost(config, ConfigurationFactoryService.SELF_LINK);

        try {
            testOperation(Operation::createGet, null);
            fail("It should have been forbidden!");
        } catch (IllegalAccessError e) {
            assertEquals("forbidden", e.getMessage());
        }
    }

    @Test
    public void testOperationForbiddenWhenVic() throws Throwable {

        ConfigurationState config = new ConfigurationState();
        config.key = ConfigurationUtil.VIC_MODE_PROPERTY;
        config.value = "true";
        config.documentSelfLink = config.key;
        doPost(config, ConfigurationFactoryService.SELF_LINK);

        try {
            testOperation(Operation::createGet, null);
            fail("It should have been forbidden!");
        } catch (IllegalAccessError e) {
            assertEquals("forbidden", e.getMessage());
        }
    }

    @Test
    public void testOperationForbiddenWhenShellDisabled() throws Throwable {

        ConfigurationState config = new ConfigurationState();
        config.key = ConfigurationUtil.ALLOW_SSH_CONSOLE_PROPERTY;
        config.value = "false";
        config.documentSelfLink = config.key;
        doPost(config, ConfigurationFactoryService.SELF_LINK);

        try {
            testOperation(Operation::createGet, null);
            fail("It should have been forbidden!");
        } catch (IllegalAccessError e) {
            assertEquals("forbidden", e.getMessage());
        }
    }

    private void testOperation(final Function<URI, Operation> createOp, String inBody)
            throws Throwable {
        URI pingUri = UriUtils.buildUri(host, MockPingService.SELF_LINK);
        URI rpPingPath = UriUtilsExtended.getReverseProxyUri(pingUri);
        URI rpPingUri = UriUtils.buildUri(host, rpPingPath.toString());
        Operation op = createOp.apply(rpPingUri);
        if (inBody != null) {
            op.setBody(inBody);
        }
        op.setCompletion((o, e) -> {
            if (e != null) {
                Operation.failActionNotSupported(o);
                return;
            }
            String outBody = o.getBody(String.class);
            assertEquals(MockPingService.BODY_PONG, outBody);
        });
        verifyOperation(op);
    }

    @Test
    public void testInvalidUri() throws Throwable {
        TestContext ctx = host.testCreate(1);
        URI rpPingUri = UriUtils.buildUri(host, "/rp/invalid");
        Operation op = Operation.createGet(rpPingUri)
                .setCompletion(ctx.getExpectedFailureCompletion());
        host.send(op);
        host.testWait(ctx);
    }

}
