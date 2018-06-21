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

import io.netty.util.internal.StringUtil;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.validator.routines.InetAddressValidator;

import com.vmware.photon.controller.model.support.IPVersion;
import com.vmware.xenon.common.LocalizableValidationException;

/**
 * Helper class for subnet range and IP address validations.
 */

public class SubnetValidator {

    /**
     * Method to validate an IP address
     *
     * @param ipAddress IP address to be validated
     * @param ipVersion IPv4 or IPv6
     */
    public static boolean isValidIPAddress(String ipAddress, IPVersion ipVersion) {
        if (StringUtil.isNullOrEmpty(ipAddress)) {
            throw new LocalizableValidationException("IP address must be specified",
                    "subnet.range.ip.must.be.specified");
        }
        switch (ipVersion) {
        case IPv6:
            return InetAddressValidator.getInstance().isValidInet6Address(ipAddress);
        case IPv4:
        default:
            return InetAddressValidator.getInstance().isValidInet4Address(ipAddress);
        }
    }

    /**
     * Method to compare two IP addresses and check if one is greater than the other.
     * Assuming the IP addresses are valid
     *
     * @param startAddress he start IP address
     * @param endAddress   The end IP address
     * @param ipVersion    IPv4 or IPv6
     * @return true if start IP is greater than end IP, false otherwise
     */
    public static boolean isStartIPGreaterThanEndIP(String startAddress,
            String endAddress,
            IPVersion ipVersion) {
        boolean isStartIPGreaterThanEndIP = false;
        if (startAddress != null && endAddress != null) {
            if (IPVersion.IPv4.equals(ipVersion)) {
                try {
                    isStartIPGreaterThanEndIP = (IpHelper.ipStringToLong(startAddress) - IpHelper
                            .ipStringToLong(endAddress)) > 0;
                } catch (IllegalArgumentException e) {
                }
            } else {
                throw new UnsupportedOperationException(
                        "Support for IPv6 IP address is not yet implemented");
            }
        }
        return isStartIPGreaterThanEndIP;
    }

    /**
     * If the ip is in the same network as the cidr, it returns true
     *
     * @param ipAddress The ip address under test
     * @param cidr      The cidr that defines the expected network address
     * @param ipVersion
     * @return
     */
    public static boolean isIpInValidRange(String ipAddress,
            String cidr, IPVersion ipVersion) {

        if (!IPVersion.IPv4.equals(ipVersion)) {
            throw new UnsupportedOperationException(
                    "Support for IPv6 IP address is not yet implemented");
        }

        SubnetUtils utils = new SubnetUtils(cidr);

        if (!utils.getInfo().getAddress().equals(utils.getInfo().getNetworkAddress())) {
            String err = String.format("Invalid CIDR: %s. Host identifier of the"
                    + " CIDR contains non zero bits.", cidr);
            throw new RuntimeException(err);
        }

        return utils.getInfo().isInRange(ipAddress);

    }

    /**
     * Returns true if the ip lies between the start and end address.
     * Or if the ip is equal to either start or end address.
     *
     * @param startAddress The starting ip address of the range.
     * @param endAddress   The ending ip address of the range.
     * @param ipVersion    ip version  IPv4 or IPv6.
     * @param ipAddress    the ip address that we want to check if falls in the range.
     * @return
     */
    public static boolean isIpInBetween(String startAddress,
            String endAddress,
            IPVersion ipVersion,
            String ipAddress) {
        if (!IPVersion.IPv4.equals(ipVersion)) {
            throw new UnsupportedOperationException(
                    "Support for IPv6 IP address is not yet implemented");
        }

        boolean isIpGreaterThanStart = false;
        boolean isIpLessThanEnd = false;

        if ((IpHelper.ipStringToLong(endAddress) - IpHelper.ipStringToLong(ipAddress))
                >= 0) {
            isIpLessThanEnd = true;
        }

        if ((IpHelper.ipStringToLong(ipAddress) - IpHelper.ipStringToLong(startAddress))
                >= 0) {
            isIpGreaterThanStart = true;
        }

        return (isIpGreaterThanStart && isIpLessThanEnd);
    }
}