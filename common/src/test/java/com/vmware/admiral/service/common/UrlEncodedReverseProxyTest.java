/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common;

import static com.vmware.admiral.service.common.UrlEncodedReverseProxyService.createReverseProxyLocation;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.function.Function;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.xenon.common.Utils;

public class UrlEncodedReverseProxyTest {
    private Logger logger = Logger.getLogger(getClass().getName());

    @Test
    public void verifyNoHost() throws URISyntaxException, UnsupportedEncodingException {
        String path = "step1/step2/step3";
        String queryAndFragment = "?a=b&c=d#e";
        verify(path, queryAndFragment);
    }

    @Test
    public void verifyWithHost() throws URISyntaxException, UnsupportedEncodingException {
        String path = "https://myHost/step1/step2/step3";
        String queryAndFragment = "?a=b&c=d#e";
        verify(path, queryAndFragment);
    }

    @Test
    public void testPrepareProxyPart_full() throws UnsupportedEncodingException {
        prepareProxyPart("https://host.com:8080/step1/step2?p1=v1&p2=v2#fragment", null);
    }

    @Test
    public void testPrepareProxyPart_placeholder() throws UnsupportedEncodingException {
        String scheme = "http";
        String host = "myHost";
        String key = URLEncoder.encode("{" + host + "}", Utils.CHARSET);
        String location = UrlEncodedReverseProxyService.SELF_LINK
                + "/s1/" + key +
                "/step1/step2?p1=v1&p2=v2#fragment";
        URI uri = UrlEncodedReverseProxyService.extractBackendURI(URI.create(location),
                k -> URI.create(scheme + "://" + k));
        Assert.assertTrue(scheme.equals(uri.getScheme()));
        Assert.assertTrue(host.equals(uri.getHost()));
    }

    @Test
    public void testPrepareProxyPart_weird() throws UnsupportedEncodingException {
        prepareProxyPart("urn:example:mammal:monotreme:echidna", null);
    }

    @Test
    public void testPrepareProxyPart_path() throws UnsupportedEncodingException {
        prepareProxyPart("step1/step2?p1=v1&p2=v2#fragment", null);
    }

    @Test
    public void testPrepareProxyPart_query() throws UnsupportedEncodingException {
        prepareProxyPart("&p2=v2#fragment", null);
    }

    @Test
    public void testPrepareProxyPart_fragment() throws UnsupportedEncodingException {
        prepareProxyPart("#fragment", null);
    }

    private URI prepareProxyPart(String backendLocation,
            Function<String, URI> resolver) throws UnsupportedEncodingException {
        this.logger.info("backendLocation: " + backendLocation);
        String location = createReverseProxyLocation(backendLocation);
        this.logger.info("location: " + location);

        URI uri = UrlEncodedReverseProxyService.extractBackendURI(URI.create(location), resolver);

        this.logger.info("==> path: " + uri.toASCIIString());
        Assert.assertEquals(backendLocation, uri.toASCIIString());
        return uri;
    }

    private void verify(String path, String queryAndFragment)
            throws URISyntaxException, UnsupportedEncodingException {
        String expectedPath = path + queryAndFragment;
        URI uri = new URI("http://localhost:8910" +
                UrlEncodedReverseProxyService.createReverseProxyLocation(expectedPath)
        );
        this.logger.info("URI to uri: " + uri);
        URI backendURI = UrlEncodedReverseProxyService.extractBackendURI(uri, null);
        Assert.assertNotNull(backendURI);
        this.logger.info("proxyPath: " + backendURI.toASCIIString());
        Assert.assertEquals(expectedPath, backendURI.toASCIIString());
    }
}