/*
 * Copyright (c) 2016-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;

import org.junit.Test;

import com.vmware.admiral.service.common.ReverseProxyService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.UriUtils;

public class UriUtilsExtendedTest {

    private static final String SAMPLE_URL = "http://github.com/ob?branch=master&product=admiral";

    private static final URI SAMPLE_URI = UriUtils.buildUri(SAMPLE_URL);

    @Test
    public void testReverseProxyEncoding() {

        String encoded = UriUtilsExtended.getValueEncoded(SAMPLE_URL);
        assertNotEquals(SAMPLE_URL, encoded);

        String decoded = UriUtilsExtended.getReverseProxyDecoded(encoded);
        assertNotEquals(encoded, decoded);

        assertEquals(SAMPLE_URL, decoded);
    }

    @Test
    public void testReverseProxyRequest() {
        assertNotNull(SAMPLE_URI);

        URI rpUri = UriUtilsExtended.getReverseProxyUri(SAMPLE_URI);
        assertNotEquals(SAMPLE_URI, rpUri);

        URI targetUri = UriUtilsExtended.getReverseProxyTargetUri(rpUri);
        assertNotEquals(rpUri, targetUri);

        assertEquals(SAMPLE_URI, targetUri);
    }

    @Test
    public void testReverseProxyTransformations() {

        // ../rp/{http://abc:80/p1/p2} ->
        // http://abc:80/p1/p2

        String url = "http://www.test-url.com:8080/p1/p2";
        URI uri = UriUtils.buildUri(url);
        URI rpUri = UriUtilsExtended.getReverseProxyUri(uri);
        URI targetUri = UriUtilsExtended.getReverseProxyTargetUri(rpUri);
        assertEquals("http://www.test-url.com:8080/p1/p2", targetUri.toString());

        // ../rp/{http://abc:80/p1/p2?q1=v1} ->
        // http://abc:80/p1/p2?q1=v1

        url = "http://www.test-url.com:8080/p1/p2?q1=v1";
        uri = UriUtils.buildUri(url);
        rpUri = UriUtilsExtended.getReverseProxyUri(uri);
        targetUri = UriUtilsExtended.getReverseProxyTargetUri(rpUri);
        assertEquals("http://www.test-url.com:8080/p1/p2?q1=v1", targetUri.toString());

        // ../rp/{http://abc:80/p1/p2}/ep1/ep2 ->
        // http://abc:80/p1/p2/ep1/ep2

        url = "http://www.test-url.com:8080/p1/p2";
        uri = UriUtils.buildUri(url);
        rpUri = UriUtilsExtended.getReverseProxyUri(uri);

        rpUri = UriUtils.buildUri(rpUri.toString() + "/ep1/ep2");

        targetUri = UriUtilsExtended.getReverseProxyTargetUri(rpUri);
        assertEquals("http://www.test-url.com:8080/p1/p2/ep1/ep2", targetUri.toString());

        // ../rp/{http://abc:80/p1/p2?q1=v1}/ep1/ep2 ->
        // http://abc:80/p1/p2/ep1/ep2?q1=v1

        url = "http://www.test-url.com:8080/p1/p2?q1=v1";
        uri = UriUtils.buildUri(url);
        rpUri = UriUtilsExtended.getReverseProxyUri(uri);

        rpUri = UriUtils.buildUri(rpUri.toString() + "/ep1/ep2");

        targetUri = UriUtilsExtended.getReverseProxyTargetUri(rpUri);
        assertEquals("http://www.test-url.com:8080/p1/p2/ep1/ep2?q1=v1", targetUri.toString());

        // ../rp/{http://abc:80/p1/p2?q1=v1}/ep1/ep2?q2=v2 ->
        // http://abc:80/p1/p2/ep1/ep2?q1=v1&q2=v2

        url = "http://www.test-url.com:8080/p1/p2?q1=v1";
        uri = UriUtils.buildUri(url);
        rpUri = UriUtilsExtended.getReverseProxyUri(uri);

        rpUri = UriUtils.buildUri(rpUri.toString() + "/ep1/ep2?q2=v2");

        targetUri = UriUtilsExtended.getReverseProxyTargetUri(rpUri);
        assertEquals("http://www.test-url.com:8080/p1/p2/ep1/ep2?q1=v1&q2=v2",
                targetUri.toString());
    }

    @Test
    public void testReverseProxyTransformationsWithTrailingSlash() {

        // ../rp/{http://abc:80/p1/p2/} ->
        // http://abc:80/p1/p2/

        String url = "http://www.test-url.com:8080/p1/p2/";
        URI uri = UriUtils.buildUri(url);
        URI rpUri = UriUtilsExtended.getReverseProxyUri(uri);
        URI targetUri = UriUtilsExtended.getReverseProxyTargetUri(rpUri);
        assertEquals("http://www.test-url.com:8080/p1/p2/", targetUri.toString());

        // ../rp/{http://abc:80/p1/p2/?q1=v1} ->
        // http://abc:80/p1/p2/?q1=v1

        url = "http://www.test-url.com:8080/p1/p2/?q1=v1";
        uri = UriUtils.buildUri(url);
        rpUri = UriUtilsExtended.getReverseProxyUri(uri);
        targetUri = UriUtilsExtended.getReverseProxyTargetUri(rpUri);
        assertEquals("http://www.test-url.com:8080/p1/p2/?q1=v1", targetUri.toString());

        // ../rp/{http://abc:80/p1/p2}/ep1/ep2/ ->
        // http://abc:80/p1/p2/ep1/ep2/

        url = "http://www.test-url.com:8080/p1/p2";
        uri = UriUtils.buildUri(url);
        rpUri = UriUtilsExtended.getReverseProxyUri(uri);

        rpUri = UriUtils.buildUri(rpUri.toString() + "/ep1/ep2/");

        targetUri = UriUtilsExtended.getReverseProxyTargetUri(rpUri);
        assertEquals("http://www.test-url.com:8080/p1/p2/ep1/ep2/", targetUri.toString());

        // ../rp/{http://abc:80/p1/p2?q1=v1}/ep1/ep2/ ->
        // http://abc:80/p1/p2/ep1/ep2/?q1=v1

        url = "http://www.test-url.com:8080/p1/p2?q1=v1";
        uri = UriUtils.buildUri(url);
        rpUri = UriUtilsExtended.getReverseProxyUri(uri);

        rpUri = UriUtils.buildUri(rpUri.toString() + "/ep1/ep2/");

        targetUri = UriUtilsExtended.getReverseProxyTargetUri(rpUri);
        assertEquals("http://www.test-url.com:8080/p1/p2/ep1/ep2/?q1=v1", targetUri.toString());

        // ../rp/{http://abc:80/p1/p2?q1=v1}/ep1/ep2/?q2=v2 ->
        // http://abc:80/p1/p2/ep1/ep2/?q1=v1&q2=v2

        url = "http://www.test-url.com:8080/p1/p2?q1=v1";
        uri = UriUtils.buildUri(url);
        rpUri = UriUtilsExtended.getReverseProxyUri(uri);

        rpUri = UriUtils.buildUri(rpUri.toString() + "/ep1/ep2/?q2=v2");

        targetUri = UriUtilsExtended.getReverseProxyTargetUri(rpUri);
        assertEquals("http://www.test-url.com:8080/p1/p2/ep1/ep2/?q1=v1&q2=v2",
                targetUri.toString());
    }

    @Test
    public void testReverseProxyLocationTransformations() {

        String url = "http://www.test-url.com:8080/p1/p2";
        URI uri = UriUtils.buildUri(url);

        // location: http://localhost/foo/bar ->
        // http://abc:80/rp/{http://localhost/foo/bar}

        String location = "http://localhost/foo/bar";
        String targetLocation = UriUtilsExtended.getReverseProxyLocation(location, null, null);
        assertNotNull(targetLocation);
        assertTrue(targetLocation.startsWith(ReverseProxyService.SELF_LINK));

        URI targetUri = UriUtilsExtended.getReverseProxyTargetUri(
                UriUtils.buildUri(uri, targetLocation));
        assertEquals("http://localhost/foo/bar", targetUri.toString());

        // location: /foo/bar & referer: http://localhost/p ->
        // http://abc:80/rp/{http://localhost/foo/bar}

        location = "/foo/bar";
        URI referer = UriUtils.buildUri("http://localhost/p");
        targetLocation = UriUtilsExtended.getReverseProxyLocation(location, referer, null);
        assertNotNull(targetLocation);
        assertTrue(targetLocation.startsWith(ReverseProxyService.SELF_LINK));

        targetUri = UriUtilsExtended.getReverseProxyTargetUri(
                UriUtils.buildUri(uri, targetLocation));
        assertEquals("http://localhost/foo/bar", targetUri.toString());
    }

    @Test
    public void testReverseProxyLocationTransformationsWithTrailingSlash() {

        String url = "http://www.test-url.com:8080/p1/p2";
        URI uri = UriUtils.buildUri(url);

        // location: http://localhost/foo/bar ->
        // http://abc:80/rp/{http://localhost/foo/bar}

        String location = "http://localhost/foo/bar/";
        String targetLocation = UriUtilsExtended.getReverseProxyLocation(location, null, null);
        assertNotNull(targetLocation);
        assertTrue(targetLocation.startsWith(ReverseProxyService.SELF_LINK));

        URI targetUri = UriUtilsExtended.getReverseProxyTargetUri(
                UriUtils.buildUri(uri, targetLocation));
        assertEquals("http://localhost/foo/bar/", targetUri.toString());

        // location: /foo/bar & referer: http://localhost/p ->
        // http://abc:80/rp/{http://localhost/foo/bar}

        location = "/foo/bar/";
        URI referer = UriUtils.buildUri("http://localhost/p");
        targetLocation = UriUtilsExtended.getReverseProxyLocation(location, referer, null);
        assertNotNull(targetLocation);
        assertTrue(targetLocation.startsWith(ReverseProxyService.SELF_LINK));

        targetUri = UriUtilsExtended.getReverseProxyTargetUri(
                UriUtils.buildUri(uri, targetLocation));
        assertEquals("http://localhost/foo/bar/", targetUri.toString());
    }

    @Test
    public void testReverseProxyLocationTransformationsWithRelativeToParam() {

        String RP_RELATIVE_TO = "/relative-path";

        String originalUrl = "http://www.test-url.com/path?rp-relative-to=" + RP_RELATIVE_TO;
        URI originalUri = UriUtils.buildUri(originalUrl);

        String url = "http://www.test-url.com:8080/p1/p2";
        URI uri = UriUtils.buildUri(url);

        // location: http://localhost/foo/bar ->
        // http://abc:80/rp/{http://localhost/foo/bar}

        String location = "http://localhost/foo/bar/";
        String targetLocation = UriUtilsExtended.getReverseProxyLocation(location, null,
                originalUri);
        assertNotNull(targetLocation);
        assertTrue(targetLocation.startsWith(RP_RELATIVE_TO + ReverseProxyService.SELF_LINK));

        URI targetUri = UriUtilsExtended.getReverseProxyTargetUri(
                UriUtils.buildUri(uri, targetLocation));
        assertEquals("http://localhost/foo/bar/", targetUri.toString());

        // location: /foo/bar & referer: http://localhost/p ->
        // http://abc:80/rp/{http://localhost/foo/bar}

        location = "/foo/bar/";
        URI referer = UriUtils.buildUri("http://localhost/p");
        targetLocation = UriUtilsExtended.getReverseProxyLocation(location, referer, originalUri);
        assertNotNull(targetLocation);
        assertTrue(targetLocation.startsWith(RP_RELATIVE_TO + ReverseProxyService.SELF_LINK));

        targetUri = UriUtilsExtended.getReverseProxyTargetUri(
                UriUtils.buildUri(uri, targetLocation));
        assertEquals("http://localhost/foo/bar/", targetUri.toString());
    }

    @Test
    public void testReverseProxyInvalidTransformations() {

        URI uri = UriUtilsExtended.getReverseProxyUri(null);
        assertNull(uri);

        uri = UriUtilsExtended.getReverseProxyTargetUri(null);
        assertNull(uri);

        URI opUri = UriUtils.buildUri("http://localhost/rp");
        uri = UriUtilsExtended.getReverseProxyTargetUri(opUri);
        assertNull(uri);

        opUri = UriUtils.buildUri("http://localhost/rp/f-o-o/bar");
        uri = UriUtilsExtended.getReverseProxyTargetUri(opUri);
        assertNull(uri);

        opUri = UriUtils.buildUri("http://localhost/rp/"
                + UriUtilsExtended.getValueEncoded("%0"));
        try {
            uri = UriUtilsExtended.getReverseProxyTargetUri(opUri);
            fail("URI should be invalid!");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().startsWith("Invalid target URI:"));
        }
    }

    @Test
    public void testBuildDockerUri() throws Exception {
        URI uri = UriUtilsExtended.buildDockerUri("http", "a.b.c", 1234, null);
        assertNotNull(uri);
        assertEquals("http", uri.getScheme());
        assertEquals("a.b.c", uri.getHost());
        assertEquals(1234, uri.getPort());
        assertEquals("/v1.24", uri.getPath());

        uri = UriUtilsExtended.buildDockerUri(null, "a.b.c", -1, "/path");
        assertNotNull(uri);
        assertEquals("https", uri.getScheme());
        assertEquals("a.b.c", uri.getHost());
        assertEquals(443, uri.getPort());
        assertEquals("/path/v1.24", uri.getPath());

        try {
            UriUtilsExtended.buildDockerUri("ftp", "a.b.c", 21, "/path");
            fail("ftp protocol should not be supported!");
        } catch (LocalizableValidationException e) {
            assertEquals("Unsupported scheme, must be http or https: ftp", e.getMessage());
        }

        try {
            UriUtilsExtended.buildDockerUri(null, "ftp://a.b.c", -1, "/path");
            fail("ftp protocol should not be supported!");
        } catch (LocalizableValidationException e) {
            assertEquals("Unsupported scheme, must be http or https: ftp", e.getMessage());
        }
    }

    @Test
    public void testBuildDockerRegistryUri() throws Exception {
        URI uri = UriUtilsExtended.buildDockerRegistryUri("hostname.local");
        assertEquals("https://hostname.local:443", uri.toString());

        uri = UriUtilsExtended.buildDockerRegistryUri("hostname.local:80");
        assertEquals("https://hostname.local:80", uri.toString());

        uri = UriUtilsExtended.buildDockerRegistryUri("http://hostname.local");
        assertEquals("http://hostname.local:80", uri.toString());

        uri = UriUtilsExtended.buildDockerRegistryUri("https://hostname.local:443");
        assertEquals("https://hostname.local:443", uri.toString());

        try {
            UriUtilsExtended.buildDockerRegistryUri("ftp://a.b.c");
            fail("ftp protocol should not be supported!");
        } catch (LocalizableValidationException e) {
            assertEquals("Unsupported scheme, must be http or https: ftp", e.getMessage());
        }
    }

    @Test
    public void testExtractHostPort() {
        assertEquals("5000" ,UriUtilsExtended.extractPort("localhost:5000"));
        assertEquals("5000" ,UriUtilsExtended.extractPort("https://test-host:5000"));
        assertEquals(null ,UriUtilsExtended.extractPort("localhost"));
    }
}
