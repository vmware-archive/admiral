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

package com.vmware.photon.controller.model.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 * Utility methods for dealing with IP addresses.
 */
public class IpHelper {

    /**
     * Convert an Inet4Address to host byte order long.
     *
     * @param ip
     *            IPv4 address
     * @return host byte order equivalent
     */
    public static long ipToLong(Inet4Address ip) {
        byte[] octets = ip.getAddress();
        long result = 0;

        for (byte octet : octets) {
            result <<= 8;
            result |= octet & 0xff;
        }
        return result;
    }

    /**
     * Convert an host byte order long to InetAddress.
     *
     * @param ip
     *            long value storing ipv4 bytes in host byte order
     * @return InetAddress
     */
    public static InetAddress longToIp(long ip) {
        try {
            return InetAddress.getByAddress(longToNetworkByteOrderArray(ip));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IP " + ip);
        }
    }

    /**
     * Convert an ip in long format to an ip string.
     *
     * @param ip
     * @return
     */
    public static String longToIpString(long ip) {
        InetAddress ipAddress = longToIp(ip);
        return ipAddress.getHostAddress();
    }

    /**
     * Convert an ip string to long.
     *
     * @param ip
     * @return
     */
    public static long ipStringToLong(String ip) {
        try {
            int networkAddress = ByteBuffer
                    .wrap(InetAddress.getByName(ip).getAddress())
                    .getInt();
            // Convert signed int to unsigned long.
            return networkAddress & 0x00000000ffffffffL;
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException(ip + " is not an IPv4 address", ex);
        }
    }

    /**
     * Convert an host byte order long to byte array in network byte order.
     *
     * @param addr
     *            long value storing ipv4 bytes in host byte order
     * @return byte array in network byte order
     */
    public static byte[] longToNetworkByteOrderArray(long addr) {
        return new byte[] { (byte) (addr >>> 24), (byte) (addr >>> 16), (byte) (addr >>> 8),
                (byte) addr };
    }

    /**
     * Calculate the CIDR from a given ip range. Valid ranges are the ones that can be covered with
     * a single CIDR and that is validated by making sure that the inverseSubnet+1 is a power of 2.
     *
     * @param ipLow
     * @param ipHigh
     * @return
     */
    public static String calculateCidrFromIpV4Range(long ipLow, long ipHigh) {

        AssertUtil.assertTrue(ipLow <= ipHigh,
                String.format("ipLow should be less than or equal to ipHigh, ipLow=%s, ipHigh=%s",
                        ipLow, ipHigh));

        int inverseSubnetMask = (int) (ipHigh - ipLow);
        int subnetMask = ~inverseSubnetMask;

        AssertUtil.assertTrue(
                (inverseSubnetMask == 0) || ((inverseSubnetMask & (inverseSubnetMask + 1)) == 0),
                String.format(
                        "inverseSubnetMask should be 0 or " +
                                "inverseSubnetMask + 1 should be a power of 2, " +
                                "ipLow=%s, ipHigh=%s, inverseSubnetMask=%s[%s], subnetMask=%s",
                        Long.toBinaryString(ipLow),
                        Long.toBinaryString(ipHigh),
                        inverseSubnetMask,
                        Long.toBinaryString(inverseSubnetMask),
                        Long.toBinaryString(subnetMask)));

        AssertUtil.assertTrue((ipLow & subnetMask) == ipLow,
                String.format(
                        "ipLow & subnetMask should equal ipLow, ipLow = %s, ipHigh= %s, subnetMask= %s",
                        Long.toBinaryString(ipLow),
                        Long.toBinaryString(ipHigh),
                        Long.toBinaryString(subnetMask)));

        int cidr = (32 - Integer.numberOfTrailingZeros(subnetMask));
        InetAddress subnetAddress = longToIp(ipLow);
        return subnetAddress.getHostAddress() + "/" + cidr;
    }

    /**
     * Calculates the netmask from a given CIDR.
     *
     * @param cidr
     * @return
     */
    public static InetAddress calculateNetmaskFromCidr(String cidr) {
        String[] cidrParts = cidr.split("/");
        AssertUtil.assertTrue(cidrParts.length == 2, "Invalid cidr: " + cidr);

        int prefix = Integer.parseInt(cidrParts[1]);
        AssertUtil.assertTrue(prefix > 0 && prefix <= 32, "Invalid prefix: " + prefix);
        int mask = 0xffffffff << (32 - prefix);

        int value = mask;
        byte[] bytes = new byte[] {
                (byte) (value >> 24),
                (byte) (value >> 16 & 0xff),
                (byte) (value >> 8 & 0xff),
                (byte) (value & 0xff)
        };

        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid cidr " + cidr);
        }
    }

    /**
     * Calculates the netmask from a given CIDR.
     *
     * @param cidr
     * @return
     */
    public static String calculateNetmaskStringFromCidr(String cidr) {
        InetAddress netmask = calculateNetmaskFromCidr(cidr);
        return netmask.getHostAddress();
    }

    /**
     * Converts a long to int only if the long value is small enough to fit into an int.
     *
     * @param l
     * @return
     */
    public static int safeLongToInt(long l) {
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(l
                    + " cannot be cast to int without changing its value.");
        }
        return (int) l;
    }
}