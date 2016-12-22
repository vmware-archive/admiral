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

package com.vmware.admiral.test.upgrade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldHost;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService1.UpgradeOldService1State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService2.UpgradeOldService2State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewHost;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService1.UpgradeNewService1State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService2.UpgradeNewService2State;

public class ManagementHostUpgradeTest extends ManagementHostUpgradeBaseTest {

    private static final TemporaryFolder SANDBOX = new TemporaryFolder();

    private int hostPort;
    private String hostSandbox;

    @BeforeClass
    public static void beforeClass() {
        serviceClient = ServiceClientFactory.createServiceClient(null);
    }

    @AfterClass
    public static void afterClass() {
        serviceClient.stop();
    }

    @Before
    public void beforeTest() throws Exception {
        hostPort = 9292;

        SANDBOX.create();
        hostSandbox = SANDBOX.getRoot().toPath().toString();
    }

    @After
    public void afterTest() {
        SANDBOX.delete();
    }

    @Test
    public void testService1AddNewFieldOptional() throws Throwable {

        upgradeHost = startHost(UpgradeOldHost.class, hostPort, hostSandbox);

        UpgradeOldService1State oldState = new UpgradeOldService1State();
        oldState.field1 = "foo";
        oldState.field2 = "bar";

        UpgradeOldService1State instance1 = createUpgradeServiceInstance(oldState);

        assertNotNull(instance1);
        assertEquals(oldState.field1, instance1.field1);
        assertEquals(oldState.field2, instance1.field2);

        stopHost(upgradeHost);

        /*
         * ---- Upgrade occurs here ----
         */

        upgradeHost = startHost(UpgradeNewHost.class, hostPort, hostSandbox);

        // get old instance

        UpgradeNewService1State instance2 = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService1State.class);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(null, instance2.field3);

        // update old instance

        instance2 = updateUpgradeServiceInstance(instance2);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(null, instance2.field3);

        // CRU new instance

        UpgradeNewService1State newState = new UpgradeNewService1State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.field3 = "new";

        instance2 = createUpgradeServiceInstance(newState);

        assertNotNull(instance2);
        assertEquals(newState.field1, instance2.field1);
        assertEquals(newState.field2, instance2.field2);
        assertEquals(newState.field3, instance2.field3);

        stopHost(upgradeHost);
    }

    @Test
    public void testService2AddNewFieldRequired() throws Throwable {

        upgradeHost = startHost(UpgradeOldHost.class, hostPort, hostSandbox);

        UpgradeOldService2State oldState = new UpgradeOldService2State();
        oldState.field1 = "foo";
        oldState.field2 = "bar";

        UpgradeOldService2State instance1 = createUpgradeServiceInstance(oldState);

        assertNotNull(instance1);
        assertEquals(oldState.field1, instance1.field1);
        assertEquals(oldState.field2, instance1.field2);

        stopHost(upgradeHost);

        /*
         * ---- Upgrade occurs here ----
         */

        upgradeHost = startHost(UpgradeNewHost.class, hostPort, hostSandbox);

        // get old instance

        UpgradeNewService2State instance2 = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService2State.class);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(null, instance2.field3);

        // update old instance

        // TODO - here should fail since field3 is null!
        instance2 = updateUpgradeServiceInstance(instance2);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(null, instance2.field3);

        // CRU new instance

        UpgradeNewService2State newState = new UpgradeNewService2State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.field3 = "new";

        instance2 = createUpgradeServiceInstance(newState);

        assertNotNull(instance2);
        assertEquals(newState.field1, instance2.field1);
        assertEquals(newState.field2, instance2.field2);
        assertEquals(newState.field3, instance2.field3);

        stopHost(upgradeHost);
    }

}
