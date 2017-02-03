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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

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

public class ManagementHostUpgradeTest extends ManagementHostBaseTest {

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

        // get old instance

        UpgradeNewService1State instance2 = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService1State.class);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(null, instance2.field3);
        assertEquals(null, instance2.field4);
        assertEquals(null, instance2.field5);

        // update old instance

        instance2 = updateUpgradeServiceInstance(instance2);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(null, instance2.field3);
        assertEquals(null, instance2.field4);
        assertEquals(null, instance2.field5);

        // CRU new instance

        UpgradeNewService1State newState = new UpgradeNewService1State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.field3 = "new";
        newState.field4 = 2015L;
        newState.field5 = Arrays.asList("a", "b", "c");

        instance2 = createUpgradeServiceInstance(newState);

        assertNotNull(instance2);
        assertEquals(newState.field1, instance2.field1);
        assertEquals(newState.field2, instance2.field2);
        assertEquals(newState.field3, instance2.field3);
        assertEquals(newState.field4, instance2.field4);
        assertEquals(newState.field5, instance2.field5);

        Collection<UpgradeNewService1State> instances;
        instances = queryUpgradeServiceInstances(UpgradeNewService1State.class, "field3", "new");
        assertEquals(1, instances.size());

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

        // get old instance

        UpgradeNewService2State instance2 = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService2State.class);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals("default value", instance2.field3);
        assertEquals(Long.valueOf(42), instance2.field4);
        assertEquals(Arrays.asList("a", "b", "c"), instance2.field5);

        Collection<UpgradeNewService2State> instances;
        instances = queryUpgradeServiceInstances(UpgradeNewService2State.class, "field3",
                "default value");
        assertEquals(1, instances.size());

        // update old instance

        instance2.field3 += " updated";

        instance2 = updateUpgradeServiceInstance(instance2);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals("default value updated", instance2.field3);
        assertEquals(Long.valueOf(42), instance2.field4);
        assertEquals(Arrays.asList("a", "b", "c"), instance2.field5);

        instances = queryUpgradeServiceInstances(UpgradeNewService2State.class, "field3",
                "default value");
        assertEquals(0, instances.size());
        instances = queryUpgradeServiceInstances(UpgradeNewService2State.class, "field3",
                "default value updated");
        assertEquals(1, instances.size());

        // CRU new instance

        UpgradeNewService2State newState = new UpgradeNewService2State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.field3 = "new";
        newState.field4 = 2015L;
        newState.field5 = Arrays.asList("a", "b", "c");
        instance2 = createUpgradeServiceInstance(newState);

        assertNotNull(instance2);
        assertEquals(newState.field1, instance2.field1);
        assertEquals(newState.field2, instance2.field2);
        assertEquals(newState.field3, instance2.field3);
        assertEquals(newState.field4, instance2.field4);
        assertEquals(newState.field5, instance2.field5);

        instances = queryUpgradeServiceInstances(UpgradeNewService2State.class, "field3", "new");
        assertEquals(1, instances.size());

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

        // get old instance

        UpgradeNewService3State instance2 = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService3State.class);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(Long.valueOf(42), instance2.field3);
        HashSet<String> expectedField4 = new HashSet<>(Arrays.asList("a", "b", "c"));
        assertEquals(expectedField4, instance2.field4);
        Map<String, String> expectedField5 = new HashMap<>();
        expectedField5.put("a", "a");
        expectedField5.put("b", "b");
        expectedField5.put("c", "c");
        // assertEquals(expectedField5, instance2.field5);
        Map<String, String> expectedField6 = new HashMap<>();
        expectedField6.put("one", "1");
        expectedField6.put("two", "2");
        assertEquals(expectedField6, instance2.field6);

        // Collection<UpgradeNewService3State> instances;
        // The query fails with 'field="field3" was indexed with numDims=0 but this query has
        // numDims=1' despite of the right value when doing a GET!
        // After an update (PUT) the query still fails.
        // instances = queryUpgradeServiceInstances(UpgradeNewService3State.class, "field3", 42L);
        // assertEquals(1, instances.size());

        // update old instance

        instance2.field3 *= 2;

        instance2 = updateUpgradeServiceInstance(instance2);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(Long.valueOf(42 * 2), instance2.field3);
        assertEquals(expectedField4, instance2.field4);
        // assertEquals(expectedField5, instance2.field5);
        assertEquals(expectedField6, instance2.field6);

        // instances = queryUpgradeServiceInstances(UpgradeNewService3State.class, "field3", 42L);
        // assertEquals(0, instances.size());
        // instances = queryUpgradeServiceInstances(UpgradeNewService3State.class, "field3", 84L);
        // assertEquals(1, instances.size());

        // CRU new instance

        UpgradeNewService3State newState = new UpgradeNewService3State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.field3 = 2015L;
        newState.field4 = new HashSet<>(Arrays.asList("a", "b", "c"));
        // newState.field5 = new HashMap<>();
        // newState.field5.put("a", "a");
        // newState.field5.put("b", "b");
        // newState.field5.put("c", "c");
        newState.field6 = new HashMap<>();
        newState.field6.put("one", "1");
        newState.field6.put("two", "2");

        instance2 = createUpgradeServiceInstance(newState);

        assertNotNull(instance2);
        assertEquals(newState.field1, instance2.field1);
        assertEquals(newState.field2, instance2.field2);
        assertEquals(newState.field3, instance2.field3);
        assertEquals(newState.field4, instance2.field4);
        // assertEquals(newState.field5, instance2.field5);
        assertEquals(newState.field6, instance2.field6);

        // instances = queryUpgradeServiceInstances(UpgradeNewService3State.class, "field3", 2015L);
        // assertEquals(1, instances.size());

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

        // get old instance

        UpgradeNewService4State instance2 = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService4State.class);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(UpgradeNewService4State.FIELD3_PREFIX + "field3", instance2.field3);
        assertEquals(UpgradeNewService4State.FIELD4_PREFIX + "field4", instance2.field4);

        Collection<UpgradeNewService4State> instances;
        instances = queryUpgradeServiceInstances(UpgradeNewService4State.class, "field3",
                UpgradeOldService4State.FIELD3_PREFIX + "field3");
        assertEquals(0, instances.size());
        instances = queryUpgradeServiceInstances(UpgradeNewService4State.class, "field3",
                UpgradeNewService4State.FIELD3_PREFIX + "field3");
        assertEquals(1, instances.size());

        // update old instance

        instance2.field3 = instance2.field3 + "-new";

        instance2 = updateUpgradeServiceInstance(instance2);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(UpgradeNewService4State.FIELD3_PREFIX + "field3-new", instance2.field3);
        assertEquals(UpgradeNewService4State.FIELD4_PREFIX + "field4", instance2.field4);

        instances = queryUpgradeServiceInstances(UpgradeNewService4State.class, "field3",
                UpgradeNewService4State.FIELD3_PREFIX + "field3");
        assertEquals(0, instances.size());
        instances = queryUpgradeServiceInstances(UpgradeNewService4State.class, "field3",
                UpgradeNewService4State.FIELD3_PREFIX + "field3-new");
        assertEquals(1, instances.size());

        // CRU new instance

        UpgradeNewService4State newState = new UpgradeNewService4State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.field3 = UpgradeNewService4State.FIELD3_PREFIX + "field3";
        newState.field4 = UpgradeNewService4State.FIELD4_PREFIX + "field4";

        instance2 = createUpgradeServiceInstance(newState);

        assertNotNull(instance2);
        assertEquals(newState.field1, instance2.field1);
        assertEquals(newState.field2, instance2.field2);
        assertEquals(newState.field3, instance2.field3);
        assertEquals(newState.field4, instance2.field4);

        instances = queryUpgradeServiceInstances(UpgradeNewService4State.class, "field3",
                UpgradeNewService4State.FIELD3_PREFIX + "field3");
        assertEquals(1, instances.size());

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

        // get old instance

        UpgradeNewService5State instance2 = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService5State.class);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(null, instance2.field3);
        assertEquals(null, instance2.field4);
        assertEquals(null, instance2.field5);
        assertEquals("field3#field4#field5", instance2.field345);
        assertEquals("field6", instance2.field6);
        assertEquals("field7", instance2.field7);
        assertEquals("field8", instance2.field8);
        assertEquals(null, instance2.field678);

        Collection<UpgradeNewService5State> instances;
        instances = queryUpgradeServiceInstances(UpgradeNewService5State.class, "field3", "field3",
                "field4", "field4", "field5", "field5");
        assertEquals(0, instances.size());
        instances = queryUpgradeServiceInstances(UpgradeNewService5State.class, "field345",
                "field3#field4#field5");
        assertEquals(1, instances.size());

        // update old instance

        instance2.field345 = instance2.field345 + "-new";

        instance2 = updateUpgradeServiceInstance(instance2);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(null, instance2.field3);
        assertEquals(null, instance2.field4);
        assertEquals(null, instance2.field5);
        assertEquals("field3#field4#field5-new", instance2.field345);
        assertEquals("field6", instance2.field6);
        assertEquals("field7", instance2.field7);
        assertEquals("field8", instance2.field8);
        assertEquals(null, instance2.field678);

        instances = queryUpgradeServiceInstances(UpgradeNewService5State.class, "field345",
                "field3#field4#field5");
        assertEquals(0, instances.size());
        instances = queryUpgradeServiceInstances(UpgradeNewService5State.class, "field345",
                "field3#field4#field5-new");
        assertEquals(1, instances.size());

        // CRU new instance

        UpgradeNewService5State newState = new UpgradeNewService5State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.field345 = "new-field1#new-field2#new-field3";
        newState.field6 = "new-field6";
        newState.field7 = "new-field7";
        newState.field8 = "new-field8";

        instance2 = createUpgradeServiceInstance(newState);

        assertNotNull(instance2);
        assertEquals(newState.field1, instance2.field1);
        assertEquals(newState.field2, instance2.field2);
        assertEquals(null, instance2.field3);
        assertEquals(null, instance2.field4);
        assertEquals(null, instance2.field5);
        assertEquals(newState.field345, instance2.field345);
        assertEquals(newState.field6, instance2.field6);
        assertEquals(newState.field7, instance2.field7);
        assertEquals(newState.field8, instance2.field8);
        assertEquals(null, instance2.field678);

        instances = queryUpgradeServiceInstances(UpgradeNewService5State.class, "field345",
                "new-field1#new-field2#new-field3");
        assertEquals(1, instances.size());

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

        // get old instance

        UpgradeNewService6State instance2 = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService6State.class);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(null, instance2.brandNewServiceLink);

        // update old instance

        BrandNewServiceState brandNewServiceInstance = new BrandNewServiceState();
        brandNewServiceInstance.field1 = "brand new foo";
        brandNewServiceInstance.field2 = "brand new bar";

        brandNewServiceInstance = createUpgradeServiceInstance(brandNewServiceInstance);

        instance2.brandNewServiceLink = brandNewServiceInstance.documentSelfLink;

        instance2 = updateUpgradeServiceInstance(instance2);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(brandNewServiceInstance.documentSelfLink, instance2.brandNewServiceLink);

        // CRU new instance

        UpgradeNewService6State newState = new UpgradeNewService6State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.brandNewServiceLink = brandNewServiceInstance.documentSelfLink;

        instance2 = createUpgradeServiceInstance(newState);

        assertNotNull(instance2);
        assertEquals(newState.field1, instance2.field1);
        assertEquals(newState.field2, instance2.field2);
        assertEquals(newState.brandNewServiceLink, instance2.brandNewServiceLink);

        Collection<UpgradeNewService6State> instances;
        instances = queryUpgradeServiceInstances(UpgradeNewService6State.class,
                "brandNewServiceLink", brandNewServiceInstance.documentSelfLink);
        assertEquals(2, instances.size());

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

        // get old instance

        UpgradeNewService7State instance2 = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService7State.class);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals(oldState.field3, instance2.upgradedField3);

        Collection<UpgradeNewService7State> instances;
        instances = queryUpgradeServiceInstances(UpgradeNewService7State.class, "field3",
                "field3");
        assertEquals(0, instances.size());
        instances = queryUpgradeServiceInstances(UpgradeNewService7State.class, "upgradedField3",
                "field3");
        assertEquals(1, instances.size());

        // update old instance

        instance2.upgradedField3 = instance2.upgradedField3 + "-new";

        instance2 = updateUpgradeServiceInstance(instance2);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);
        assertEquals("field3-new", instance2.upgradedField3);

        instances = queryUpgradeServiceInstances(UpgradeNewService7State.class, "upgradedField3",
                "field3");
        assertEquals(0, instances.size());
        instances = queryUpgradeServiceInstances(UpgradeNewService7State.class, "upgradedField3",
                "field3-new");
        assertEquals(1, instances.size());

        // CRU new instance

        UpgradeNewService7State newState = new UpgradeNewService7State();
        newState.field1 = "foo";
        newState.field2 = "bar";
        newState.upgradedField3 = "field3";

        instance2 = createUpgradeServiceInstance(newState);

        assertNotNull(instance2);
        assertEquals(newState.field1, instance2.field1);
        assertEquals(newState.field2, instance2.field2);
        assertEquals(newState.upgradedField3, instance2.upgradedField3);

        instances = queryUpgradeServiceInstances(UpgradeNewService7State.class, "upgradedField3",
                "field3");
        assertEquals(1, instances.size());

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

        // get old instance

        UpgradeNewService8State instance2 = getUpgradeServiceInstance(instance1.documentSelfLink,
                UpgradeNewService8State.class);
        assertNotNull(instance2);
        assertEquals(oldState.field1, instance2.field1);
        assertEquals(oldState.field2, instance2.field2);

        Collection<UpgradeNewService7State> instances;
        instances = queryUpgradeServiceInstances(UpgradeNewService7State.class, "field3",
                "field3");
        assertEquals(0, instances.size());

        // update old instance

        instance2.field1 = instance2.field1 + "-new";

        instance2 = updateUpgradeServiceInstance(instance2);
        assertNotNull(instance2);
        assertEquals("foo-new", instance2.field1);
        assertEquals(oldState.field2, instance2.field2);

        // CRU new instance

        UpgradeNewService8State newState = new UpgradeNewService8State();
        newState.field1 = "foo";
        newState.field2 = "bar";

        instance2 = createUpgradeServiceInstance(newState);

        assertNotNull(instance2);
        assertEquals(newState.field1, instance2.field1);
        assertEquals(newState.field2, instance2.field2);

        stopHost(upgradeHost);
    }

}
