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

package com.vmware.admiral.compute.container.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerNetworkServiceTest extends ComputeBaseTest {
    private List<String> networksForDeletion;

    private static final String SUBNET_TEMPLATE = "10.%d.0.0/16";
    private static final String IP_RANGE_TEMPLATE = "10.%d.%d.0/24";
    private static final String GATEWAY_TEMPLATE = "10.%d.0.1";

    private static final String IPAM_DRIVER = "default";
    private static final String IPAM_ADDITIONAL_HOST_KEY = "additional-host";
    private static final String IPAM_ADDITIONAL_HOST_IP_ADDRESS_TEMPLATE = "10.%d.5.5";

    private static final String DRIVER_OPTIONS_KEY_1 = "driver.option.1";
    private static final String DRIVER_OPTIONS_KEY_2 = "driver.option.2";
    private static final String DRIVER_OPTIONS_VALUE_1 = "value.1";
    private static final String DRIVER_OPTIONS_VALUE_2 = "value.2";

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ContainerNetworkService.FACTORY_LINK);
        networksForDeletion = new ArrayList<>();
    }

    @After
    public void tearDown() throws Throwable {
        for (String selfLink : networksForDeletion) {
            delete(selfLink);
        }
    }

    @Test
    public void testContainerServices() throws Throwable {
        verifyService(
                FactoryService.create(ContainerNetworkService.class),
                ContainerNetworkState.class,
                (prefix, index) -> {
                    ContainerNetworkState networkState = new ContainerNetworkState();
                    networkState.id = prefix + "id" + index;
                    networkState.name = prefix + "name" + index;

                    Ipam ipam = new Ipam();
                    ipam.driver = IPAM_DRIVER;

                    IpamConfig ipamConfig = new IpamConfig();
                    ipamConfig.subnet = String.format(SUBNET_TEMPLATE, index % 256);
                    ipamConfig.ipRange = String.format(IP_RANGE_TEMPLATE, index % 256, index % 256);
                    ipamConfig.gateway = String.format(GATEWAY_TEMPLATE, index % 256);
                    ipamConfig.auxAddresses = new HashMap<>();
                    ipamConfig.auxAddresses.put(prefix + IPAM_ADDITIONAL_HOST_KEY,
                            String.format(IPAM_ADDITIONAL_HOST_IP_ADDRESS_TEMPLATE, index % 256));

                    ipam.config = new IpamConfig[] { ipamConfig };
                    networkState.ipam = ipam;

                    String key = prefix + "option" + index;
                    String value = prefix + "value" + index;
                    networkState.options = new HashMap<>();
                    networkState.options.put(key, value);

                    return networkState;
                },
                (prefix, serviceDocument) -> {
                    ContainerNetworkState networkState = (ContainerNetworkState) serviceDocument;
                    networksForDeletion.add(networkState.documentSelfLink);
                    assertTrue(networkState.id.startsWith(prefix + "id"));
                    assertTrue(networkState.name.startsWith(prefix + "name"));
                    assertTrue(networkState.ipam != null);
                    assertEquals(IPAM_DRIVER, networkState.ipam.driver);
                    assertTrue(networkState.ipam.config != null);
                    assertEquals(1, networkState.ipam.config.length);
                    NetworkUtils.validateIpCidrNotation(networkState.ipam.config[0].subnet);
                    NetworkUtils.validateIpCidrNotation(networkState.ipam.config[0].ipRange);
                    NetworkUtils.validateIpAddress(networkState.ipam.config[0].gateway);
                    networkState.ipam.config[0].auxAddresses.values().stream()
                            .forEach((address) -> {
                                NetworkUtils.validateIpAddress(address);
                            });
                });
    }

    @Test
    public void testPatchExpiration() throws Throwable {
        ContainerNetworkState network = createNetwork(null);
        URI networkUri = UriUtils.buildUri(host, network.documentSelfLink);

        ContainerNetworkState patch = new ContainerNetworkState();
        long nowMicrosUtc = Utils.getNowMicrosUtc() + TimeUnit.SECONDS.toMicros(30);
        patch.documentExpirationTimeMicros = nowMicrosUtc;

        doOperation(patch, networkUri, false, Action.PATCH);
        ContainerNetworkState updatedNetwork = getDocument(ContainerNetworkState.class,
                network.documentSelfLink);
        assertEquals(nowMicrosUtc, updatedNetwork.documentExpirationTimeMicros);

        patch = new ContainerNetworkState();
        patch.documentExpirationTimeMicros = -1;

        doOperation(patch, networkUri, false, Action.PATCH);
        updatedNetwork = getDocument(ContainerNetworkState.class, network.documentSelfLink);
        assertEquals(0, updatedNetwork.documentExpirationTimeMicros);
    }

    @Test
    public void testPatchDriverOptions() throws Throwable {
        ContainerNetworkState network = createNetwork("tenant1");
        URI networkUri = UriUtils.buildUri(host, network.documentSelfLink);

        // Test add option to empty map
        ContainerNetworkState patch = new ContainerNetworkState();
        patch.options = new HashMap<>();
        patch.options.put(DRIVER_OPTIONS_KEY_1, DRIVER_OPTIONS_VALUE_1);

        doOperation(patch, networkUri, false, Action.PATCH);
        ContainerNetworkState updatedNetwork = getDocument(ContainerNetworkState.class,
                network.documentSelfLink);
        assertEquals(1, updatedNetwork.options.size());
        assertEquals(DRIVER_OPTIONS_VALUE_1,
                updatedNetwork.options.get(DRIVER_OPTIONS_KEY_1));

        // Test append option to existing list
        patch = new ContainerNetworkState();
        patch.options = new HashMap<>();
        patch.options.put(DRIVER_OPTIONS_KEY_2, DRIVER_OPTIONS_VALUE_2);

        doOperation(patch, networkUri, false, Action.PATCH);
        updatedNetwork = getDocument(ContainerNetworkState.class, network.documentSelfLink);
        assertEquals(2, updatedNetwork.options.size());
        assertEquals(DRIVER_OPTIONS_VALUE_1,
                updatedNetwork.options.get(DRIVER_OPTIONS_KEY_1));
        assertEquals(DRIVER_OPTIONS_VALUE_2,
                updatedNetwork.options.get(DRIVER_OPTIONS_KEY_2));

        // Test overwrite existing options
        patch = new ContainerNetworkState();
        patch.options = new HashMap<>();
        patch.options.put(DRIVER_OPTIONS_KEY_1, DRIVER_OPTIONS_VALUE_2);
        patch.options.put(DRIVER_OPTIONS_KEY_2, DRIVER_OPTIONS_VALUE_1);

        doOperation(patch, networkUri, false, Action.PATCH);
        updatedNetwork = getDocument(ContainerNetworkState.class, network.documentSelfLink);
        assertEquals(2, updatedNetwork.options.size());
        assertEquals(DRIVER_OPTIONS_VALUE_1,
                updatedNetwork.options.get(DRIVER_OPTIONS_KEY_2));
        assertEquals(DRIVER_OPTIONS_VALUE_2,
                updatedNetwork.options.get(DRIVER_OPTIONS_KEY_1));
    }

    @Test
    public void testPropertiesValidationOnCreate() {
        // Test with valid properties
        try {
            createNetwork(null);
        } catch (Throwable e) {
            fail("Network should have been created successfully");
            e.printStackTrace();
        }

        // Test with invalid subnet string
        try {
            createNetwork(null, "invalid", "127.17.0.1");
            fail("Creation of network should have failed due to invalid subnet");
        } catch (Throwable e) {
            // this is expected
            e.printStackTrace();
        }

    }

    @Test
    public void testPropertiesValidationOnUpdate() {
        ContainerNetworkState network = null;

        // Test with valid properties
        try {
            network = createNetwork(null);
        } catch (Throwable e) {
            fail("Network should have been created successfully");
            e.printStackTrace();
        }

        // Test with invalid subnet string
        try {
            updateNetwork(network, "invalid", null);
            fail("Update of network should have failed due to invalid subnet");
        } catch (Throwable e) {
            // this is expected
            e.printStackTrace();
        }

    }

    @Test
    public void testSubnetValidation() {
        try {
            NetworkUtils.validateIpCidrNotation(null);
        } catch (Throwable e) {
            e.printStackTrace();
            fail("Null should be valid value");
        }

        String testValue = "foo.bar";
        String correctValue = "172.17.0.0/16";
        try {
            NetworkUtils.validateIpCidrNotation(testValue);
            fail(getSubnetValidationTestFailedMessage(testValue, correctValue));
        } catch (IllegalArgumentException e) {
            String errorMessage = e.getMessage();
            String expectedError = String.format(NetworkUtils.IPV4_CIDR_VALIDATION_ERROR_FORMAT,
                    testValue);
            if (!errorMessage.equals(expectedError)) {
                throw e;
            }
        }

        testValue = "172.17.0.0";
        correctValue = "172.17.0.0/16";
        try {
            NetworkUtils.validateIpCidrNotation(testValue);
            fail(getSubnetValidationTestFailedMessage(testValue, correctValue));
        } catch (IllegalArgumentException e) {
            String errorMessage = e.getMessage();
            String expectedError = String.format(NetworkUtils.IPV4_CIDR_VALIDATION_ERROR_FORMAT,
                    testValue);
            if (!errorMessage.equals(expectedError)) {
                throw e;
            }
        }

        testValue = "300.17.0.0";
        correctValue = "172.17.0.0/16";
        try {
            NetworkUtils.validateIpCidrNotation(testValue);
            fail(getSubnetValidationTestFailedMessage(testValue, correctValue));
        } catch (IllegalArgumentException e) {
            String errorMessage = e.getMessage();
            String expectedError = String.format(NetworkUtils.IPV4_CIDR_VALIDATION_ERROR_FORMAT,
                    testValue);
            if (!errorMessage.equals(expectedError)) {
                throw e;
            }
        }
    }

    public void testGatewayValidation() {
        try {
            NetworkUtils.validateIpAddress(null);
        } catch (Throwable e) {
            e.printStackTrace();
            fail("Null should be valid value");
        }

        String testValue = "foo.bar";
        String correctValue = "172.17.0.1";
        try {
            NetworkUtils.validateIpAddress(testValue);
            fail(getGatewayValidationTestFailedMessage(testValue, correctValue));
        } catch (IllegalArgumentException e) {
            String errorMessage = e.getMessage();
            String expectedError = String.format(NetworkUtils.IPV4_VALIDATION_ERROR_FORMAT,
                    testValue);
            if (!errorMessage.equals(expectedError)) {
                throw e;
            }
        }

        testValue = "172.17.0";
        correctValue = "172.17.0.1";
        try {
            NetworkUtils.validateIpAddress(testValue);
            fail(getGatewayValidationTestFailedMessage(testValue, correctValue));
        } catch (IllegalArgumentException e) {
            String errorMessage = e.getMessage();
            String expectedError = String.format(NetworkUtils.IPV4_VALIDATION_ERROR_FORMAT,
                    testValue);
            if (!errorMessage.equals(expectedError)) {
                throw e;
            }
        }

        testValue = "300.17.0.1";
        correctValue = "172.17.0.1";
        try {
            NetworkUtils.validateIpAddress(testValue);
            fail(getGatewayValidationTestFailedMessage(testValue, correctValue));
        } catch (IllegalArgumentException e) {
            String errorMessage = e.getMessage();
            String expectedError = String.format(NetworkUtils.IPV4_VALIDATION_ERROR_FORMAT,
                    testValue);
            if (!errorMessage.equals(expectedError)) {
                throw e;
            }
        }
    }

    private String getSubnetValidationTestFailedMessage(String testValue, String correctValue) {
        return String.format("Validation should have failed: %s is invalid IPv4 subnet "
                + "in CIDR notation. The valid one is %s", testValue, correctValue);
    }

    private String getGatewayValidationTestFailedMessage(String testValue, String correctValue) {
        return String.format("Validation should have failed: %s is invalid IPv4 gateway. "
                + "The valid one is %s", testValue, correctValue);
    }

    private ContainerNetworkState createNetwork(String group) throws Throwable {
        return createNetwork(group, null, null);
    }

    private ContainerNetworkState createNetwork(String group, String subnet, String gateway)
            throws Throwable {
        ContainerNetworkState networkState = new ContainerNetworkState();

        networkState.id = UUID.randomUUID().toString();
        networkState.name = "name_" + networkState.id;

        if (subnet != null || gateway != null) {
            Ipam ipam = new Ipam();
            IpamConfig ipamConfig = new IpamConfig();
            ipamConfig.subnet = subnet;
            ipamConfig.gateway = gateway;
            ipam.config = new IpamConfig[] { ipamConfig };
            networkState.ipam = ipam;
        }

        networkState.tenantLinks = Collections.singletonList(group);

        networkState = doPost(networkState, ContainerNetworkService.FACTORY_LINK);
        networksForDeletion.add(networkState.documentSelfLink);

        return networkState;
    }

    /** pass <code>null</code> for no change */
    private ContainerNetworkState updateNetwork(ContainerNetworkState network, String newSubnet,
            String newGateway) throws Throwable {
        ContainerNetworkState patch = new ContainerNetworkState();

        if (newSubnet != null || newGateway != null) {
            Ipam ipam = new Ipam();
            IpamConfig ipamConfig = new IpamConfig();
            ipamConfig.subnet = newSubnet;
            ipamConfig.gateway = newGateway;
            ipam.config = new IpamConfig[] { ipamConfig };
            patch.ipam = ipam;
        }

        return doPatch(patch, network.documentSelfLink);
    }

}
