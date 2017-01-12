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

package com.vmware.admiral.common.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.vmware.admiral.service.common.ReverseProxyService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.UriUtils;

public class UriUtilsExtended {
    private static final String MINIMUM_DOCKER_API_VERSION = "1.21";
    private static final String DEFAULT_DOCKER_SCHEME = UriUtils.HTTPS_SCHEME;
    private static final int DEFAULT_DOCKER_HTTP_PORT = 80;
    private static final int DEFAULT_DOCKER_HTTPS_PORT = 443;
    private static final String DEFAULT_DOCKER_REGISTRY_SCHEME = UriUtils.HTTPS_SCHEME;
    private static final int DEFAULT_DOCKER_REGISTRY_HTTP_PORT = 80;
    private static final int DEFAULT_DOCKER_REGISTRY_HTTPS_PORT = 443;
    /* Host URL pattern */
    public static final Pattern PATTERN_HOST_URL = Pattern
            .compile("((?<scheme>\\w[\\w\\d+-\\.]*)(://))?"
                    + "((?<host>(\\[(.*?)\\]|([^/ :]+)))(:(?<port>[0-9]*))?)"
                    + "((?<path>/([^?# ]+)?)(\\?(?<query>(.*)?))?)?");

    public static final String MEDIA_TYPE_APPLICATION_YAML = "application/yaml";

    /**
     * URL parameter to be used in calls to the Reverse Proxy service to specify if the generated or
     * processed reverse proxy address has to be relative to the path specified by this parameter.
     */
    public static final String RP_RELATIVE_TO_PARAM = "rp-relative-to";

    public static URI buildDockerRegistryUri(String address) {
        Matcher matcher = addressPatternMatcher(address);

        String scheme = matcher.group("scheme");
        if (scheme == null) {
            scheme = DEFAULT_DOCKER_REGISTRY_SCHEME;
        }

        if (!scheme.equalsIgnoreCase(UriUtils.HTTP_SCHEME)
                && !scheme.equalsIgnoreCase(UriUtils.HTTPS_SCHEME)) {
            throw new LocalizableValidationException(
                    "Unsupported scheme, must be http or https: " + scheme,
                    "common.unsupported.scheme", scheme);
        }

        String servicePort = matcher.group("port");
        int port = DEFAULT_DOCKER_REGISTRY_HTTPS_PORT;
        if (servicePort != null && !servicePort.isEmpty()) {
            port = Integer.parseInt(servicePort);
        } else {
            if (UriUtils.HTTP_SCHEME.equals(scheme)) {
                port = DEFAULT_DOCKER_REGISTRY_HTTP_PORT;
            } else {
                port = DEFAULT_DOCKER_REGISTRY_HTTPS_PORT;
            }
        }

        String serviceHost = matcher.group("host");

        try {
            return new URI(scheme, null, serviceHost, port, null, null, null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static URI buildDockerUri(String scheme, String address, int port, String path) {
        Matcher matcher = addressPatternMatcher(address);

        if (scheme == null) {
            if ((scheme = matcher.group("scheme")) == null) {
                scheme = DEFAULT_DOCKER_SCHEME;
            }
        }
        scheme = scheme.toLowerCase();

        if (!scheme.equalsIgnoreCase(UriUtils.HTTP_SCHEME)
                && !scheme.equalsIgnoreCase(UriUtils.HTTPS_SCHEME)) {
            throw new LocalizableValidationException(
                    "Unsupported scheme, must be http or https: " + scheme,
                    "common.unsupported.scheme", scheme);
        }

        String servicePort = matcher.group("port");
        if (servicePort != null && !servicePort.isEmpty()) {
            port = Integer.parseInt(servicePort);
        }
        if (port == -1) {
            if (UriUtils.HTTP_SCHEME.equals(scheme)) {
                port = DEFAULT_DOCKER_HTTP_PORT;
            } else {
                port = DEFAULT_DOCKER_HTTPS_PORT;
            }
        }

        if (path == null) {
            path = matcher.group("path");
        }

        if (path == null) {
            path = "";
        }
        if (!path.contains("/v")) {
            // add docker api version
            String versionedPath = "/v" + MINIMUM_DOCKER_API_VERSION;
            path += versionedPath;
        }

        String serviceHost = matcher.group("host");

        try {
            return new URI(scheme, null, serviceHost, port, path, null, null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static String extractHost(String address) {
        return addressPatternMatcher(address).group("host");
    }

    public static String extractHostAndPort(String address) {
        Matcher matcher = addressPatternMatcher(address);
        String hostname = matcher.group("host");
        String port = matcher.group("port");
        if (port != null) {
            hostname = String.format("%s:%s", hostname, port);
        }
        return hostname;
    }

    public static String extractScheme(String address) {
        return addressPatternMatcher(address).group("scheme");
    }

    private static Matcher addressPatternMatcher(String address) {
        Matcher matcher = PATTERN_HOST_URL.matcher(address.trim());
        if (!matcher.matches()) {
            throw new LocalizableValidationException("Invalid host address: " + address, "common.host.address.invalid", address);
        }
        return matcher;
    }

    /**
     * Parse a boolean query parameter value
     *
     * Unlike Boolean.parseBoolean this will treat any value except null, false and 0 as true.
     *
     * @param value
     *            text to parse
     * @return boolean value
     */
    public static boolean parseBooleanParam(String value) {
        if (value == null) {
            return false;
        }

        if (value.equalsIgnoreCase("false") || value.equals("0")) {
            return false;
        }

        // anything else is considered true
        return true;
    }

    /**
     * Flattens a query parameters map so it can be used with extendQueryWithParams
     *
     * @param queryParams
     *            key-value map of parameters
     * @return array of Strings
     */
    public static String[] flattenQueryParams(Map<String, String> queryParams) {
        return queryParams.entrySet().stream()
                .flatMap((e) -> Stream.of(e.getKey(), e.getValue()))
                .toArray(String[]::new);
    }

    /**
     * Returns the provided {@link URI} transformed to be sent to the {@link ReverseProxyService}
     * instead of accessing directly to it.
     *
     * Some transformation examples ({xyz} means Reverse Proxy encoded path):
     * <pre>
     * http://abc:80/p1/p2 -> ../rp/{http://abc:80/p1/p2}
     * http://abc:80/p1/p2?q1=v1 -> ../rp/{http://abc:80/p1/p2?q1=v1}
     * </pre>
     *
     * @param in
     *            {@link URI} going to the {@link ReverseProxyService}
     * @return {@link ReverseProxyService} {@link URI}
     */
    public static URI getReverseProxyUri(URI in) {
        if (in == null) {
            return null;
        }
        String encodedUri = getReverseProxyEncoded(in.toString());
        return UriUtils.buildUri(UriUtils.buildUriPath(ReverseProxyService.SELF_LINK, encodedUri));
    }

    /**
     * Returns the provided new location (from the header "location") transformed to be sent to the
     * {@link ReverseProxyService} instead of accessing directly to it. If the new location is
     * relative then it's transformed to absolute based on the provided current {@link URI}.
     *
     * Optionally, if the original {@link URI} containing the Reverse Proxy target specifies the
     * parameter {@link RP_RELATIVE_TO_PARAM}, then the value of that parameter is used as a prefix
     * of the new location. That's required in order to avoid relying on Xenon's method getReferer()
     * since it's value can be altered by the framework.
     *
     * Some transformation examples ({xyz} means Reverse Proxy encoded path):
     * <pre>
     * /p3/p4 & http://abc:80/p1/p2 -> /rp/{http://abc:80/p3/p4}
     * http://abc:80/p1/p2 & xxx -> /rp/{http://abc:80/p1/p2}
     * </pre>
     *
     * @param location
     *            Location going to the {@link ReverseProxyService}
     * @param currentUri
     *            Current {@link URI} where the header "location" was retrieved from
     * @param originalUri
     *            Original {@link URI} containing the Reverse Proxy target
     * @return {@link ReverseProxyService} location
     */
    public static String getReverseProxyLocation(String location, URI currentUri, URI originalUri) {
        if (location.startsWith(UriUtils.URI_PATH_CHAR)) { // relative path
            location = UriUtils.buildUri(currentUri, location).toString()
                    // keep trailing '/' if applies (important! e.g. for the ShellInABox case)
                    + (location.endsWith(UriUtils.URI_PATH_CHAR) ? UriUtils.URI_PATH_CHAR : "");
        }
        String newLocation = UriUtils.buildUriPath(ReverseProxyService.SELF_LINK,
                getReverseProxyEncoded(location));
        if (originalUri == null) {
            return newLocation;
        }
        // check for the parameter RP_RELATIVE_TO_PARAM and use it if it applies
        Map<String, String> params = UriUtils.parseUriQueryParams(originalUri);
        String relativeTo = params.get(RP_RELATIVE_TO_PARAM);
        if (relativeTo == null) {
            return newLocation;
        }
        return UriUtils.buildUriPath(relativeTo, newLocation) + UriUtils.URI_QUERY_CHAR
                + UriUtils.buildUriQuery(RP_RELATIVE_TO_PARAM, relativeTo);
    }

    /**
     * Returns the actual {@link URI} that the {@link URI} coming from the
     * {@link ReverseProxyService} is targeting.
     *
     * Some transformation examples ({xyz} means Reverse Proxy encoded path):
     * <pre>
     * ../rp/{http://abc:80/p1/p2} -> http://abc:80/p1/p2
     * ../rp/{http://abc:80/p1/p2?q1=v1} -> http://abc:80/p1/p2?q1=v1
     * ../rp/{http://abc:80/p1/p2}/ep1/ep2 -> http://abc:80/p1/p2/ep1/ep2
     * ../rp/{http://abc:80/p1/p2?q1=v1}/ep1/ep2 -> http://abc:80/p1/p2/ep1/ep2?q1=v1
     * ../rp/{http://abc:80/p1/p2?q1=v1}/ep1/ep2?q2=v2 -> http://abc:80/p1/p2/ep1/ep2?q1=v1&q2=v2
     * </pre>
     *
     * @param opUri
     *            {@link URI} coming from the {@link ReverseProxyService}
     * @return Targeted {@link URI}, or {@code null} when there is no target {@link URI} or it's a
     *         relative path
     */
    public static URI getReverseProxyTargetUri(URI opUri) {
        if (opUri == null) {
            return null;
        }

        String opUriPath = opUri.getPath();

        int rpIndex = opUriPath.indexOf(ReverseProxyService.SELF_LINK);
        if (rpIndex == -1) {
            // no target URI provided!
            return null;
        }
        String opPath = opUriPath.substring(rpIndex + ReverseProxyService.SELF_LINK.length());

        if (opPath.startsWith(UriUtils.URI_PATH_CHAR)) {
            opPath = opPath.substring(1);
        } else {
            // no target URI provided!
            return null;
        }

        String encodedUri = opPath.split(UriUtils.URI_PATH_CHAR)[0];
        String decodedUri;
        try {
            decodedUri = getReverseProxyDecoded(encodedUri);
        } catch (Exception e) {
            // target URI is not RP encoded, most probably just a relative path
            return null;
        }

        URI targetUri = UriUtils.buildUri(decodedUri);
        if (targetUri == null) {
            throw new LocalizableValidationException("Invalid target URI: " + decodedUri,
                    "common.invalid.uri", decodedUri);
        }

        Map<String, String> queryParams = UriUtils.parseUriQueryParams(targetUri);
        queryParams.putAll(UriUtils.parseUriQueryParams(opUri));
        queryParams.remove(RP_RELATIVE_TO_PARAM);

        String opExtraPath = opPath.replace(encodedUri, "");
        if (!opExtraPath.isEmpty() && !opExtraPath.equals(UriUtils.URI_PATH_CHAR)) {
            targetUri = UriUtils.extendUri(targetUri, opExtraPath);
        }

        if (opExtraPath.endsWith(UriUtils.URI_PATH_CHAR) &&
                !targetUri.toString().endsWith(UriUtils.URI_PATH_CHAR)) {
            // keep trailing '/' if applies (important! e.g. for the ShellInABox case)
            targetUri = UriUtils.buildUri(targetUri.toString() + UriUtils.URI_PATH_CHAR);
        }

        if (!queryParams.isEmpty()) {
            targetUri = UriUtils.extendUriWithQuery(
                    // keep trailing '/' if applies (important! e.g. for the ShellInABox case)
                    UriUtils.buildUri(
                            targetUri.toString().replace("?" + targetUri.getRawQuery(), "")),
                    flattenQueryParams(queryParams));
        }

        return targetUri;
    }

    /**
     * Returns the provided value encoded to a {@link ReverseProxyService} friendly format.
     *
     * @param input
     *            Value to be encoded
     * @return Encoded value
     */
    public static String getReverseProxyEncoded(String input) {
        return Base64.getUrlEncoder().encodeToString(input.getBytes(UTF_8));
    }

    /**
     * Returns the provided value decoded from a {@link ReverseProxyService} friendly format.
     *
     * @param input
     *            Value to be decoded
     * @return Decoded value
     */
    public static String getReverseProxyDecoded(String input) {
        return new String(Base64.getUrlDecoder().decode(input), UTF_8);
    }

}
