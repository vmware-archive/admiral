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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldHost;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService1.UpgradeOldService1State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService2.UpgradeOldService2State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService3.UpgradeOldService3State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService4.UpgradeOldService4State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService5.UpgradeOldService5State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService6.UpgradeOldService6State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService7.UpgradeOldService7State;
import com.vmware.admiral.test.upgrade.version1.UpgradeOldService8.UpgradeOldService8State;
import com.vmware.admiral.test.upgrade.version2.BrandNewService.BrandNewServiceState;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewHost;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService1;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService1.UpgradeNewService1State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService2;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService2.UpgradeNewService2State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService3;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService3.UpgradeNewService3State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService4;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService4.UpgradeNewService4State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService5;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService5.UpgradeNewService5State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService6;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService6.UpgradeNewService6State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService7;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService7.UpgradeNewService7State;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService8;
import com.vmware.admiral.test.upgrade.version2.UpgradeNewService8.UpgradeNewService8State;

public class ManagementHostBackwardCompatibilityTest extends ManagementHostBaseTest {

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
        hostPort = 0; // ask runtime to pick a random port

        SANDBOX.create();
        hostSandbox = SANDBOX.getRoot().toPath().toString();
    }

    @After
    public void afterTest() {
        if (upgradeHost != null) {
            stopHost(upgradeHost);
        }

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

        // do the service migration

        upgradeService(upgradeHost, UpgradeNewService1.FACTORY_LINK, UpgradeNewService1State.class);

        // get old instance with old version

        UpgradeOldService1State instance2old = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeOldService1State.class, ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);

        // update old instance with old version

        instance2old = updateUpgradeServiceInstance(instance2old,
                ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);

        // get old instance with latest version

        UpgradeNewService1State instance2new = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService1State.class);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2new.field1);
        assertEquals(oldState.field2, instance2new.field2);
        assertEquals(null, instance2new.field3);
        assertEquals(null, instance2new.field4);
        assertEquals(null, instance2new.field5);

        // update old instance with latest version

        instance2new = updateUpgradeServiceInstance(instance2new);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);
        assertEquals(null, instance2new.field3);
        assertEquals(null, instance2new.field4);
        assertEquals(null, instance2new.field5);

        // create new instance with old version

        UpgradeOldService1State newState = new UpgradeOldService1State();
        newState.field1 = "foo";
        newState.field2 = "bar";

        instance2old = createUpgradeServiceInstance(newState, ReleaseConstants.API_VERSION_0_9_1);

        assertNotNull(instance2old);
        assertEquals(newState.field1, instance2old.field1);
        assertEquals(newState.field2, instance2old.field2);

        // create new instance with new version

        UpgradeNewService1State newState2 = new UpgradeNewService1State();
        newState2.field1 = "foo";
        newState2.field2 = "bar";

        instance2new = createUpgradeServiceInstance(newState2);

        assertNotNull(instance2new);
        assertEquals(newState2.field1, instance2new.field1);
        assertEquals(newState2.field2, instance2new.field2);
        assertEquals(null, instance2new.field3);
        assertEquals(null, instance2new.field4);
        assertEquals(null, instance2new.field5);

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

        // do the service migration

        upgradeService(upgradeHost, UpgradeNewService2.FACTORY_LINK, UpgradeNewService2State.class);

        // get old instance with old version

        UpgradeOldService2State instance2old = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeOldService2State.class, ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);

        // update old instance with old version

        instance2old = updateUpgradeServiceInstance(instance2old,
                ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);

        // get old instance with new version

        UpgradeNewService2State instance2new = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService2State.class);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2new.field1);
        assertEquals(oldState.field2, instance2new.field2);
        assertEquals("default value", instance2new.field3);
        assertEquals(Long.valueOf(42L), instance2new.field4);
        assertEquals(Arrays.asList("a", "b", "c"), instance2new.field5);

        // update old instance with new version

        instance2new = updateUpgradeServiceInstance(instance2new);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2new.field1);
        assertEquals(oldState.field2, instance2new.field2);
        assertEquals("default value", instance2new.field3);
        assertEquals(Long.valueOf(42L), instance2new.field4);
        assertEquals(Arrays.asList("a", "b", "c"), instance2new.field5);

        // create new instance with old version

        UpgradeOldService2State newState = new UpgradeOldService2State();
        newState.field1 = "foo";
        newState.field2 = "bar";

        instance2old = createUpgradeServiceInstance(newState, ReleaseConstants.API_VERSION_0_9_1);

        assertNotNull(instance2old);
        assertEquals(newState.field1, instance2old.field1);
        assertEquals(newState.field2, instance2old.field2);

        // create new instance with new version

        UpgradeNewService2State newState2 = new UpgradeNewService2State();
        newState2.field1 = "foo";
        newState2.field2 = "bar";

        instance2new = createUpgradeServiceInstance(newState2);

        assertNotNull(instance2new);
        assertEquals(newState2.field1, instance2new.field1);
        assertEquals(newState2.field2, instance2new.field2);
        assertEquals("default value", instance2new.field3);
        assertEquals(Long.valueOf(42L), instance2new.field4);
        assertEquals(Arrays.asList("a", "b", "c"), instance2new.field5);

        stopHost(upgradeHost);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testService3ChangeFieldType() throws Throwable {

        upgradeHost = startHost(UpgradeOldHost.class, hostPort, hostSandbox);

        UpgradeOldService3State oldState = new UpgradeOldService3State();
        oldState.field1 = "foo";
        oldState.field2 = "bar";
        oldState.field3 = "fortytwo";
        oldState.field4 = Arrays.asList("a", "b", "c");
        // oldState.field5 = Arrays.asList("a", "b", "c");
        oldState.field6 = new HashMap<>();
        oldState.field6.put("one", "1");
        oldState.field6.put("two", "2");

        UpgradeOldService3State instance1 = createUpgradeServiceInstance(oldState);

        assertNotNull(instance1);
        assertEquals(oldState.field1, instance1.field1);
        assertEquals(oldState.field2, instance1.field2);
        assertEquals(oldState.field3, instance1.field3);
        assertEquals(oldState.field4, instance1.field4);
        // assertEquals(oldState.field5, instance1.field5);
        assertEquals(oldState.field6, instance1.field6);

        stopHost(upgradeHost);

        /*
         * ---- Upgrade occurs here ----
         */

        upgradeHost = startHost(UpgradeNewHost.class, hostPort, hostSandbox);

        // do the service migration

        upgradeService(upgradeHost, UpgradeNewService3.FACTORY_LINK, UpgradeNewService3State.class);

        // get old instance with old version

        UpgradeOldService3State instance2old = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeOldService3State.class, ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);
        assertEquals(oldState.field3, instance2old.field3);
        assertEquals(oldState.field4, instance2old.field4);
        // assertEquals(oldState.field5, instance2old.field5);
        assertEquals(oldState.field6, instance2old.field6);

        // update old instance with old version

        instance2old = updateUpgradeServiceInstance(instance2old,
                ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);
        assertEquals(oldState.field3, instance2old.field3);
        assertEquals(oldState.field4, instance2old.field4);
        // assertEquals(oldState.field5, instance2old.field5);
        assertEquals(oldState.field6, instance2old.field6);

        // get old instance with new version

        UpgradeNewService3State instance2new = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService3State.class);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2new.field1);
        assertEquals(oldState.field2, instance2new.field2);
        assertEquals(Long.valueOf(42), instance2new.field3);
        HashSet<String> expectedField4 = new HashSet<>(Arrays.asList("a", "b", "c"));
        assertEquals(expectedField4, instance2new.field4);
        // Map<String, String> expectedField5 = new HashMap<>();
        // expectedField5.put("a", "a");
        // expectedField5.put("b", "b");
        // expectedField5.put("c", "c");
        // assertEquals(expectedField5, instance2new.field5);
        Map<String, String> expectedField6 = new HashMap<>();
        expectedField6.put("one", "1");
        expectedField6.put("two", "2");
        assertEquals(expectedField6, instance2new.field6);

        // update old instance with new version

        instance2new = updateUpgradeServiceInstance(instance2new);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2new.field1);
        assertEquals(oldState.field2, instance2new.field2);
        assertEquals(Long.valueOf(42), instance2new.field3);
        assertEquals(expectedField4, instance2new.field4);
        // assertEquals(expectedField5, instance2new.field5);
        assertEquals(expectedField6, instance2new.field6);

        // create new instance with old version

        UpgradeOldService3State newState = new UpgradeOldService3State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.field3 = "fortytwo";
        newState.field4 = Arrays.asList("a", "b", "c");
        // newState.field5 = new HashMap<>();
        // newState.field5.put("a", "a");
        // newState.field5.put("b", "b");
        // newState.field5.put("c", "c");
        newState.field6 = new HashMap<>();
        newState.field6.put("one", "1");
        newState.field6.put("two", "2");

        instance2old = createUpgradeServiceInstance(newState, ReleaseConstants.API_VERSION_0_9_1);

        assertNotNull(instance2old);
        assertEquals(newState.field1, instance2old.field1);
        assertEquals(newState.field2, instance2old.field2);
        assertEquals(newState.field3, instance2old.field3);
        assertEquals(newState.field4, instance2old.field4);
        // assertEquals(newState.field5, instance2old.field5);
        assertEquals(newState.field6, instance2old.field6);

        // create new instance with new version

        UpgradeNewService3State newState2 = new UpgradeNewService3State();
        newState2.field1 = "foo";
        newState2.field2 = "bar";
        newState2.field3 = 2015L;
        newState2.field4 = new HashSet<>(Arrays.asList("a", "b", "c"));
        // newState2.field5 = new HashMap<>();
        // newState2.field5.put("a", "a");
        // newState2.field5.put("b", "b");
        // newState2.field5.put("c", "c");
        newState2.field6 = new HashMap<>();
        newState2.field6.put("one", "1");
        newState2.field6.put("two", "2");

        instance2new = createUpgradeServiceInstance(newState2);

        assertNotNull(instance2new);
        assertEquals(newState2.field1, instance2new.field1);
        assertEquals(newState2.field2, instance2new.field2);
        assertEquals(newState2.field3, instance2new.field3);
        assertEquals(newState2.field4, instance2new.field4);
        // assertEquals(newState2.field5, instance2new.field5);
        assertEquals(newState2.field6, instance2new.field6);

        stopHost(upgradeHost);
    }

    @Test
    public void testService4ChangeFieldValue() throws Throwable {

        upgradeHost = startHost(UpgradeOldHost.class, hostPort, hostSandbox);

        UpgradeOldService4State oldState = new UpgradeOldService4State();
        oldState.field1 = "foo";
        oldState.field2 = "bar";
        oldState.field3 = UpgradeOldService4State.FIELD3_PREFIX + "field3";
        oldState.field4 = "field4";

        UpgradeOldService4State instance1 = createUpgradeServiceInstance(oldState);

        assertNotNull(instance1);
        assertEquals(oldState.field1, instance1.field1);
        assertEquals(oldState.field2, instance1.field2);
        assertEquals(oldState.field3, instance1.field3);
        assertEquals(oldState.field4, instance1.field4);

        stopHost(upgradeHost);

        /*
         * ---- Upgrade occurs here ----
         */

        upgradeHost = startHost(UpgradeNewHost.class, hostPort, hostSandbox);

        // do the service migration

        upgradeService(upgradeHost, UpgradeNewService4.FACTORY_LINK, UpgradeNewService4State.class);

        // get old instance with old version

        UpgradeOldService4State instance2old = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeOldService4State.class, ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);
        assertEquals(UpgradeOldService4State.FIELD3_PREFIX + "field3", instance2old.field3);
        assertEquals("field4", instance2old.field4);

        // update old instance with old version

        instance2old = updateUpgradeServiceInstance(instance2old,
                ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);
        assertEquals(UpgradeOldService4State.FIELD3_PREFIX + "field3", instance2old.field3);
        assertEquals("field4", instance2old.field4);

        // get old instance with new version

        UpgradeNewService4State instance2new = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService4State.class, ReleaseConstants.CURRENT_API_VERSION);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2new.field1);
        assertEquals(oldState.field2, instance2new.field2);
        assertEquals(UpgradeNewService4State.FIELD3_PREFIX + "field3", instance2new.field3);
        assertEquals(UpgradeNewService4State.FIELD4_PREFIX + "field4", instance2new.field4);

        // update old instance with new version

        instance2new = updateUpgradeServiceInstance(instance2new);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2new.field1);
        assertEquals(oldState.field2, instance2new.field2);
        assertEquals(UpgradeNewService4State.FIELD3_PREFIX + "field3", instance2new.field3);
        assertEquals(UpgradeNewService4State.FIELD4_PREFIX + "field4", instance2new.field4);

        // create new instance with old version

        UpgradeOldService4State newState = new UpgradeOldService4State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.field3 = UpgradeOldService4State.FIELD3_PREFIX + "field3";
        newState.field4 = "field4";

        instance2old = createUpgradeServiceInstance(newState, ReleaseConstants.API_VERSION_0_9_1);

        assertNotNull(instance2old);
        assertEquals(newState.field1, instance2old.field1);
        assertEquals(newState.field2, instance2old.field2);
        assertEquals(newState.field3, instance2old.field3);
        assertEquals(newState.field4, instance2old.field4);

        // create new instance with new version

        UpgradeNewService4State newState2 = new UpgradeNewService4State();
        newState2.field1 = "foo";
        newState2.field2 = "bar";
        newState2.field3 = UpgradeNewService4State.FIELD3_PREFIX + "field3";
        newState2.field4 = UpgradeNewService4State.FIELD4_PREFIX + "field4";

        instance2new = createUpgradeServiceInstance(newState2);

        assertNotNull(instance2new);
        assertEquals(newState2.field1, instance2new.field1);
        assertEquals(newState2.field2, instance2new.field2);
        assertEquals(newState2.field3, instance2new.field3);
        assertEquals(newState2.field4, instance2new.field4);

        stopHost(upgradeHost);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testService5SplitFieldValue() throws Throwable {

        upgradeHost = startHost(UpgradeOldHost.class, hostPort, hostSandbox);

        UpgradeOldService5State oldState = new UpgradeOldService5State();
        oldState.field1 = "foo";
        oldState.field2 = "bar";
        oldState.field3 = "field3";
        oldState.field4 = "field4";
        oldState.field5 = "field5";
        oldState.field678 = "field6/field7/field8";

        UpgradeOldService5State instance1 = createUpgradeServiceInstance(oldState);

        assertNotNull(instance1);
        assertEquals(oldState.field1, instance1.field1);
        assertEquals(oldState.field2, instance1.field2);
        assertEquals(oldState.field3, instance1.field3);
        assertEquals(oldState.field4, instance1.field4);
        assertEquals(oldState.field5, instance1.field5);
        assertEquals(oldState.field678, instance1.field678);

        stopHost(upgradeHost);

        /*
         * ---- Upgrade occurs here ----
         */

        upgradeHost = startHost(UpgradeNewHost.class, hostPort, hostSandbox);

        // do the service migration

        upgradeService(upgradeHost, UpgradeNewService5.FACTORY_LINK, UpgradeNewService5State.class);

        // get old instance with old version

        UpgradeOldService5State instance2old = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeOldService5State.class, ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);
        assertEquals("field3", instance2old.field3);
        assertEquals("field4", instance2old.field4);
        assertEquals("field5", instance2old.field5);
        assertEquals("field6/field7/field8", instance2old.field678);

        // update old instance with old version

        instance2old = updateUpgradeServiceInstance(instance2old,
                ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);
        assertEquals("field3", instance2old.field3);
        assertEquals("field4", instance2old.field4);
        assertEquals("field5", instance2old.field5);
        assertEquals("field6/field7/field8", instance2old.field678);

        // get old instance with new version

        UpgradeNewService5State instance2new = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService5State.class);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2new.field1);
        assertEquals(oldState.field2, instance2new.field2);
        assertEquals(null, instance2new.field3);
        assertEquals(null, instance2new.field4);
        assertEquals(null, instance2new.field5);
        assertEquals("field3#field4#field5", instance2new.field345);
        assertEquals("field6", instance2new.field6);
        assertEquals("field7", instance2new.field7);
        assertEquals("field8", instance2new.field8);
        assertEquals(null, instance2new.field678);

        // update old instance with new version

        instance2new = updateUpgradeServiceInstance(instance2new);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2new.field1);
        assertEquals(oldState.field2, instance2new.field2);
        assertEquals(null, instance2new.field3);
        assertEquals(null, instance2new.field4);
        assertEquals(null, instance2new.field5);
        assertEquals("field3#field4#field5", instance2new.field345);
        assertEquals("field6", instance2new.field6);
        assertEquals("field7", instance2new.field7);
        assertEquals("field8", instance2new.field8);
        assertEquals(null, instance2new.field678);

        // create new instance with old version

        UpgradeOldService5State newState = new UpgradeOldService5State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.field3 = "field3";
        newState.field4 = "field4";
        newState.field5 = "field5";
        newState.field678 = "field6/field7/field8";

        instance2old = createUpgradeServiceInstance(newState, ReleaseConstants.API_VERSION_0_9_1);

        assertNotNull(instance2old);
        assertEquals(newState.field1, instance2old.field1);
        assertEquals(newState.field2, instance2old.field2);
        assertEquals(newState.field3, instance2old.field3);
        assertEquals(newState.field4, instance2old.field4);
        assertEquals(newState.field5, instance2old.field5);
        assertEquals(newState.field678, instance2old.field678);

        // create new instance with new version

        UpgradeNewService5State newState2 = new UpgradeNewService5State();
        newState2.field1 = "foo";
        newState2.field2 = "bar";
        newState2.field345 = "new-field1#new-field2#new-field3";
        newState2.field6 = "new-field6";
        newState2.field7 = "new-field7";
        newState2.field8 = "new-field8";

        instance2new = createUpgradeServiceInstance(newState2);

        assertNotNull(instance2new);
        assertEquals(newState2.field1, instance2new.field1);
        assertEquals(newState2.field2, instance2new.field2);
        assertEquals(null, instance2new.field3);
        assertEquals(null, instance2new.field4);
        assertEquals(null, instance2new.field5);
        assertEquals(newState2.field345, instance2new.field345);
        assertEquals(newState2.field6, instance2new.field6);
        assertEquals(newState2.field7, instance2new.field7);
        assertEquals(newState2.field8, instance2new.field8);
        assertEquals(null, instance2new.field678);

        stopHost(upgradeHost);
    }

    @Test
    public void testService6AddNewService() throws Throwable {

        upgradeHost = startHost(UpgradeOldHost.class, hostPort, hostSandbox);

        UpgradeOldService6State oldState = new UpgradeOldService6State();
        oldState.field1 = "foo";
        oldState.field2 = "bar";

        UpgradeOldService6State instance1 = createUpgradeServiceInstance(oldState);

        assertNotNull(instance1);
        assertEquals(oldState.field1, instance1.field1);
        assertEquals(oldState.field2, instance1.field2);

        stopHost(upgradeHost);

        /*
         * ---- Upgrade occurs here ----
         */

        upgradeHost = startHost(UpgradeNewHost.class, hostPort, hostSandbox);

        // do the service migration

        upgradeService(upgradeHost, UpgradeNewService6.FACTORY_LINK, UpgradeNewService6State.class);

        // get old instance with old version

        UpgradeOldService6State instance2old = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeOldService6State.class, ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);

        // update old instance with old version

        instance2old = updateUpgradeServiceInstance(instance2old,
                ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);

        // get old instance with new version

        UpgradeNewService6State instance2new = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService6State.class);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2new.field1);
        assertEquals(oldState.field2, instance2new.field2);
        assertEquals(null, instance2new.brandNewServiceLink);

        // update old instance with new version

        BrandNewServiceState brandNewServiceInstance = new BrandNewServiceState();
        brandNewServiceInstance.field1 = "brand new foo";
        brandNewServiceInstance.field2 = "brand new bar";

        brandNewServiceInstance = createUpgradeServiceInstance(brandNewServiceInstance);

        instance2new.brandNewServiceLink = brandNewServiceInstance.documentSelfLink;

        instance2new = updateUpgradeServiceInstance(instance2new);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2new.field1);
        assertEquals(oldState.field2, instance2new.field2);
        assertEquals(brandNewServiceInstance.documentSelfLink, instance2new.brandNewServiceLink);

        // create new instance with old version

        UpgradeOldService6State newState = new UpgradeOldService6State();
        newState.field1 = "foo";
        newState.field2 = "bar";

        instance2old = createUpgradeServiceInstance(newState);

        assertNotNull(instance2old);
        assertEquals(newState.field1, instance2old.field1);
        assertEquals(newState.field2, instance2old.field2);

        // create new instance with new version

        UpgradeNewService6State newState2 = new UpgradeNewService6State();
        newState2.field1 = "foo";
        newState2.field2 = "bar";
        newState2.brandNewServiceLink = brandNewServiceInstance.documentSelfLink;

        instance2new = createUpgradeServiceInstance(newState2);

        assertNotNull(instance2new);
        assertEquals(newState2.field1, instance2new.field1);
        assertEquals(newState2.field2, instance2new.field2);
        assertEquals(newState2.brandNewServiceLink, instance2new.brandNewServiceLink);

        stopHost(upgradeHost);
    }

    @Test
    public void testService7ChangeFieldName() throws Throwable {

        upgradeHost = startHost(UpgradeOldHost.class, hostPort, hostSandbox);

        UpgradeOldService7State oldState = new UpgradeOldService7State();
        oldState.field1 = "foo";
        oldState.field2 = "bar";
        oldState.field3 = "field3";

        UpgradeOldService7State instance1 = createUpgradeServiceInstance(oldState);

        assertNotNull(instance1);
        assertEquals(oldState.field1, instance1.field1);
        assertEquals(oldState.field2, instance1.field2);
        assertEquals(oldState.field3, instance1.field3);

        stopHost(upgradeHost);

        /*
         * ---- Upgrade occurs here ----
         */

        upgradeHost = startHost(UpgradeNewHost.class, hostPort, hostSandbox);

        // do the service migration

        upgradeService(upgradeHost, UpgradeNewService7.FACTORY_LINK, UpgradeNewService7State.class);

        // get old instance with old version

        UpgradeOldService7State instance2old = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeOldService7State.class, ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);
        assertEquals(oldState.field3, instance2old.field3);

        // update old instance with old version

        instance2old = updateUpgradeServiceInstance(instance2old,
                ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);
        assertEquals("field3", instance2old.field3);

        // get old instance with new version

        UpgradeNewService7State instance2new = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService7State.class);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2new.field1);
        assertEquals(oldState.field2, instance2new.field2);
        assertEquals(oldState.field3, instance2new.upgradedField3);

        // update old instance with new version

        instance2new = updateUpgradeServiceInstance(instance2new);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2new.field1);
        assertEquals(oldState.field2, instance2new.field2);
        assertEquals("field3", instance2new.upgradedField3);

        // create new instance with old version

        UpgradeOldService7State newState = new UpgradeOldService7State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.field3 = "field3";

        instance2old = createUpgradeServiceInstance(newState, ReleaseConstants.API_VERSION_0_9_1);

        assertNotNull(instance2old);
        assertEquals(newState.field1, instance2old.field1);
        assertEquals(newState.field2, instance2old.field2);
        assertEquals(newState.field3, instance2old.field3);

        // create new instance with new version

        UpgradeNewService7State newState2 = new UpgradeNewService7State();
        newState2.field1 = "foo";
        newState2.field2 = "bar";
        newState2.upgradedField3 = "field3";

        instance2new = createUpgradeServiceInstance(newState2);

        assertNotNull(instance2new);
        assertEquals(newState2.field1, instance2new.field1);
        assertEquals(newState2.field2, instance2new.field2);
        assertEquals(newState2.upgradedField3, instance2new.upgradedField3);

        stopHost(upgradeHost);
    }

    @Test
    public void testService8RemoveField() throws Throwable {

        upgradeHost = startHost(UpgradeOldHost.class, hostPort, hostSandbox);

        UpgradeOldService8State oldState = new UpgradeOldService8State();
        oldState.field1 = "foo";
        oldState.field2 = "bar";
        oldState.field3 = "field3";

        UpgradeOldService8State instance1 = createUpgradeServiceInstance(oldState);

        assertNotNull(instance1);
        assertEquals(oldState.field1, instance1.field1);
        assertEquals(oldState.field2, instance1.field2);
        assertEquals(oldState.field3, instance1.field3);

        stopHost(upgradeHost);

        /*
         * ---- Upgrade occurs here ----
         */

        upgradeHost = startHost(UpgradeNewHost.class, hostPort, hostSandbox);

        // do the service migration

        upgradeService(upgradeHost, UpgradeNewService8.FACTORY_LINK, UpgradeNewService8State.class);

        // get old instance with old version

        UpgradeOldService8State instance2old = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeOldService8State.class, ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);
        assertEquals("", instance2old.field3);

        // update old instance with old version

        instance2old = updateUpgradeServiceInstance(instance2old,
                ReleaseConstants.API_VERSION_0_9_1);
        assertNotNull(instance2old);
        assertEquals(oldState.field1, instance2old.field1);
        assertEquals(oldState.field2, instance2old.field2);
        assertEquals("", instance2old.field3);

        // get old instance with new version

        UpgradeNewService8State instance2new = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService8State.class);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2new.field1);
        assertEquals(oldState.field2, instance2new.field2);

        // update old instance with new version

        instance2new = updateUpgradeServiceInstance(instance2new);
        assertNotNull(instance2new);
        assertEquals(oldState.field1, instance2new.field1);
        assertEquals(oldState.field2, instance2new.field2);

        // create new instance with old version

        UpgradeOldService8State newState = new UpgradeOldService8State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.field3 = "field3";

        instance2old = createUpgradeServiceInstance(newState, ReleaseConstants.API_VERSION_0_9_1);

        assertNotNull(instance2new);
        assertEquals(newState.field1, instance2old.field1);
        assertEquals(newState.field2, instance2old.field2);
        assertEquals("", instance2old.field3);

        // create new instance with new version

        UpgradeNewService8State newState2 = new UpgradeNewService8State();
        newState2.field1 = "foo";
        newState2.field2 = "bar";

        instance2new = createUpgradeServiceInstance(newState2);

        assertNotNull(instance2new);
        assertEquals(newState2.field1, instance2new.field1);
        assertEquals(newState2.field2, instance2new.field2);

        stopHost(upgradeHost);
    }

}
