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

package com.vmware.admiral.adapter.pks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.pks.entities.UAATokenResponse;
import com.vmware.admiral.compute.pks.PKSEndpointService;

public class PKSContextTest {

    private PKSEndpointService.Endpoint endpoint;
    private UAATokenResponse uaaTokenResponse;

    @Before
    public void setUp() throws Exception {
        endpoint = new PKSEndpointService.Endpoint();
        endpoint.uaaEndpoint = "http://some.host/uaa";
        endpoint.apiEndpoint = "http://some.host/api";

        uaaTokenResponse = new UAATokenResponse();
        uaaTokenResponse.accessToken = "access-token";
        uaaTokenResponse.refreshToken = "refresh-token";
        uaaTokenResponse.expiresIn = "100";
    }

    @Test
    public void test() {
        PKSContext pksContext = PKSContext.create(endpoint, uaaTokenResponse);

        assertNotNull(pksContext);

        assertNotNull(pksContext.pksUAAUri);
        assertEquals(endpoint.uaaEndpoint, pksContext.pksUAAUri.toString());
        assertNotNull(pksContext.pksAPIUri);
        assertEquals(endpoint.apiEndpoint, pksContext.pksAPIUri.toString());

        assertEquals(uaaTokenResponse.accessToken, pksContext.accessToken);
        assertEquals(uaaTokenResponse.refreshToken, pksContext.refreshToken);
        assertTrue(pksContext.expireMillisTime > System.currentTimeMillis());

        pksContext = PKSContext.create(endpoint, null);
        assertNotNull(pksContext.pksUAAUri);
        assertEquals(endpoint.uaaEndpoint, pksContext.pksUAAUri.toString());
        assertNotNull(pksContext.pksAPIUri);
        assertEquals(endpoint.apiEndpoint, pksContext.pksAPIUri.toString());
        assertNull(pksContext.accessToken);
        assertNull(pksContext.refreshToken);
        assertEquals(0, pksContext.expireMillisTime);
    }

    @Test
    public void testInvalidExpiration() {
        PKSContext pksContext = PKSContext.create(endpoint, uaaTokenResponse);

        // test when expiration time is not a number
        uaaTokenResponse.expiresIn = "aa";
        pksContext = PKSContext.create(endpoint, uaaTokenResponse);

        assertTrue(pksContext.expireMillisTime <= System.currentTimeMillis());

        // test when expiration time is negative
        uaaTokenResponse.expiresIn = "-100";
        pksContext = PKSContext.create(endpoint, uaaTokenResponse);

        assertTrue(pksContext.expireMillisTime < System.currentTimeMillis());

        // test when expiration time is null
        uaaTokenResponse.expiresIn = null;
        pksContext = PKSContext.create(endpoint, uaaTokenResponse);

        assertTrue(pksContext.expireMillisTime <= System.currentTimeMillis());
    }

    @Test(expected = NullPointerException.class)
    public void testNullURI() {
        endpoint.uaaEndpoint = null;

        PKSContext.create(endpoint, uaaTokenResponse);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidURI() {
        endpoint.uaaEndpoint = ":/+/adaf:";

        PKSContext.create(endpoint, uaaTokenResponse);
    }

}