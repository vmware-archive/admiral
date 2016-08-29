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

package com.vmware.admiral.compute.container;

import java.net.URISyntaxException;
import java.util.regex.Matcher;

import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.service.common.ResourceNamePrefixService;

/**
 * Configuration for exposing a service at a given container port to an address.
 */
public class ServiceAddressConfig {

    /**
     * The address at which a service can be accessed, it will have a generated part for uniqueness.
     * It supports format character \"%s\" to specify where the unique generated name part is placed.
     * If omitted it will be placed either as a prefix or suffix of the hostname, depending on system config.
     */
    public String address;

    /**
     * The container/service port to which this config is mapped to. It has to be a port that the container
     * is exposing.
     */
    public String port;

    public static String formatAddress(String address, String resourceName)
            throws URISyntaxException {
        Matcher matcher = getPatternMatcher(address);

        String scheme = matcher.group("scheme");
        String hostname = matcher.group("host");
        String port = matcher.group("port");
        String path = matcher.group("path");
        String query = matcher.group("query");

        if (port != null) {
            throw new IllegalArgumentException(
                    "Port cannot be specified, it will be determined based on scheme");
        }

        if (scheme == null) {
            scheme = UriUtilsExtended.HTTP_SCHEME;
        }

        if (!hostname.contains("%s")) {
            if (address.contains("%s")) {
                throw new IllegalArgumentException("%s only supported on hostname");
            }
            hostname = ResourceNamePrefixService.getDefaultResourceNameFormat(hostname);
        }

        hostname = String.format(hostname, resourceName);

        String result = String.format("%s://%s", scheme, hostname);
        if (path != null) {
            result += path;
        }
        if (query != null) {
            result += "?" + query;
        }

        return result;
    }

    public static void validateAddress(String address) {
        try {
            formatAddress(address, "placeholder");
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private static Matcher getPatternMatcher(String address) {
        Matcher matcher = UriUtilsExtended.PATTERN_HOST_URL.matcher(address.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid address: " + address);
        }
        return matcher;
    }
}
