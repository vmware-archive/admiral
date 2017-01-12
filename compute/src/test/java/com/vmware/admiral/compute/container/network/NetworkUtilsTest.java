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

import org.junit.Assert;
import org.junit.Test;

import com.vmware.xenon.common.LocalizableValidationException;

public class NetworkUtilsTest {

    private class TestEntry {

        public String testValue;
        public boolean shouldFail;

        public TestEntry(String testValue, boolean shouldFail) {
            this.testValue = testValue;
            this.shouldFail = shouldFail;
        }

    }

    private TestEntry[] testIpAddressesData = new TestEntry[] {
            // null is allowed
            new TestEntry(null, false),

            // empty String is not allowed
            new TestEntry("", false),

            // host names are not allowed
            new TestEntry("foo.bar", true),

            // valid IPv4 addresses
            new TestEntry("192.168.0.1", false),
            new TestEntry("127.0.0.1", false),
            new TestEntry("10.10.3.2", false),

            // invalid IPv4 addresses
            new TestEntry("256.0.0.1", true),
            new TestEntry("100.256.0.1", true),
            new TestEntry("100.100.256.1", true),
            new TestEntry("100.100.100.256", true),
            new TestEntry("100.100.25", true),
            new TestEntry("100.100.100.100.100", true),

            // valid IPv6 addresses
            new TestEntry("::", false),
            new TestEntry("::1", false),
            new TestEntry("a::a", false),
            new TestEntry("A::A", false),
            new TestEntry("aaaa::a", false),
            new TestEntry("aaaa:aaaa::a", false),
            new TestEntry("aaaa:aaaa:aaaa::a", false),
            new TestEntry("aaaa:aaaa:aaaa:aaaa::a", false),
            new TestEntry("aaaa:aaaa:aaaa:aaaa:aaaa::a", false),
            new TestEntry("aaaa:aaaa:aaaa:aaaa:aaaa:aaaa::a", false),
            new TestEntry("1111:2222:3333:4444:aaaa:bbbb:cccc:dddd", false),
            new TestEntry("1:22:333:4444:aaaa:bbb:cc:d", false),
            new TestEntry("1111:2222:3333::aaaa:bbbb:cccc:dddd", false),
            new TestEntry("1111:2222::aaaa:bbbb:cccc:dddd", false),
            new TestEntry("1111:2222::bbbb:cccc:dddd", false),
            new TestEntry("1111:2222::192.168.0.100", false),
            new TestEntry("1111:2222:3333::192.168.0.100", false),
            new TestEntry("1:22:333:4444::192.168.0.100", false),
            new TestEntry("1:22:333:4444:5555:6666:192.168.0.100", false),
            new TestEntry("2001:db8:1::", false),

            // invalid IPv6 addresses
            new TestEntry("1111::bbbb:cccc::dddd", true),
            new TestEntry("11115::bbbb:cccc:dddd", true),
            new TestEntry("gaaaa::bbbb:cccc:dddd", true),
            new TestEntry("1111:2222:3333:4444:5555:6666:7777", true),
            new TestEntry("1111:2222:3333:4444:5555:6666:7777:8888:9999", true),
            new TestEntry("1111", true),
            new TestEntry("1:22:333:4444:5555:6666::192.168.0.100", true),
            new TestEntry("1:22:333:4444:5555:6666:7777:192.168.0.100", true),
            new TestEntry("1:22:333:4444:5555:6666:300.168.0.100", true)
    };

    private TestEntry[] testIpCidrNotationData = new TestEntry[] {
            // null is allowed
            new TestEntry(null, false),

            // empty String is not allowed
            new TestEntry("", false),

            // host names are not allowed
            new TestEntry("foo.bar/16", true),

            // valid IPv4 ranges in CIDR notation
            new TestEntry("127.0.0.1/0", false),
            new TestEntry("127.0.0.1/8", false),
            new TestEntry("127.0.0.1/16", false),
            new TestEntry("127.0.0.1/32", false),

            // invalid IPv4 ranges in CIDR notation
            new TestEntry("127.0.0.1/-1", true),
            new TestEntry("127.0.0.1/33", true),
            new TestEntry("127.0.0.1/100", true),
            new TestEntry("127.0.0.1/foo", true),
            new TestEntry("256.0.0.1/16", true),
            new TestEntry("127.256.0.1/16", true),
            new TestEntry("127.0.256.1/16", true),
            new TestEntry("127.0.0.256/16", true),
            new TestEntry("127.0.0.1.1/16", true),
            new TestEntry("127.0.1/16", true),

            // valid IPv6 ranges in CIDR notation
            new TestEntry("::1/128", false),
            new TestEntry("::1/96", false),
            new TestEntry("::1/64", false),
            new TestEntry("::1/32", false),
            new TestEntry("::1/16", false),
            new TestEntry("::1/8", false),
            new TestEntry("2001:db8:1::/64", false),

            // invalid IPv6 ranges in CIDR notation
            new TestEntry("::1/129", true),
            new TestEntry("::1/300", true),
            new TestEntry("::1/-1", true),
            new TestEntry("::1/foo", true),
            new TestEntry("::1::1/-1", true),
            new TestEntry("1:2:3:4:5:6:7::1/32", true),
            new TestEntry("1:2:3:4:5:6:7:8:9/32", true),
            new TestEntry("g::1/32", true)
    };

    private TestEntry[] testNetworkNames = new TestEntry[] {
            // Null or empty names are not allowed
            new TestEntry(null, true),
            new TestEntry("", true),
            new TestEntry(" ", true),

            new TestEntry("valid-name", false),
            new TestEntry("valid name", false),
            new TestEntry("1valid-name", false)
    };

    @Test
    public void testValidateIpAddress() {
        for (TestEntry entry : testIpAddressesData) {
            try {
                NetworkUtils.validateIpAddress(entry.testValue);
                if (entry.shouldFail) {
                    String message = String.format(
                            "Test should have failed, '%s' is not a valid IP address",
                            entry.testValue);
                    Assert.fail(message);
                }
            } catch (LocalizableValidationException e) {
                String errorMessage = e.getMessage();
                String expectedError = String.format(NetworkUtils.FORMAT_IP_VALIDATION_ERROR,
                        entry.testValue);
                if (!errorMessage.equals(expectedError)) {
                    throw e;
                } else {
                    if (!entry.shouldFail) {
                        String message = String.format(
                                "Test should have passed, '%s' is a valid IP address",
                                entry.testValue);
                        Assert.fail(message);
                    }
                }
            }
        }
    }

    @Test
    public void testValidateIpCidrNotation() {
        for (TestEntry entry : testIpCidrNotationData) {
            try {
                NetworkUtils.validateIpCidrNotation(entry.testValue);
                if (entry.shouldFail) {
                    String message = String.format(
                            "Test should have failed, '%s' is not a valid CIDR notation",
                            entry.testValue);
                    Assert.fail(message);
                }
            } catch (LocalizableValidationException e) {
                String errorMessage = e.getMessage();
                String expectedError = String.format(
                        NetworkUtils.FORMAT_CIDR_NOTATION_VALIDATION_ERROR,
                        entry.testValue);
                if (!errorMessage.equals(expectedError)) {
                    throw e;
                } else {
                    if (!entry.shouldFail) {
                        String message = String.format(
                                "Test should have passed, '%s' is a valid CIDR notation",
                                entry.testValue);
                        Assert.fail(message);
                    }
                }
            }
        }
    }

    @Test
    public void testValidateNetworkName() {
        for (TestEntry entry : testNetworkNames) {
            try {
                NetworkUtils.validateNetworkName(entry.testValue);
                if (entry.shouldFail) {
                    String message = String.format(
                            "Test should have failed, '%s' is not a valid network name",
                            entry.testValue);
                    Assert.fail(message);
                }
            } catch (LocalizableValidationException e) {
                String errorMessage = e.getMessage();
                String expectedError = NetworkUtils.ERROR_NETWORK_NAME_IS_REQUIRED;
                if (!errorMessage.equals(expectedError)) {
                    throw e;
                } else {
                    if (!entry.shouldFail) {
                        String message = String.format(
                                "Test should have passed, '%s' is a valid network name",
                                entry.testValue);
                        Assert.fail(message);
                    }
                }
            }
        }
    }
}
