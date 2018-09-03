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

package com.vmware.admiral.auth.idm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.function.Function;

import org.junit.Test;

import com.vmware.admiral.auth.idm.local.LocalAuthConfigProvider;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.services.common.UserService;
import com.vmware.xenon.services.common.authn.BasicAuthenticationService;

public class LocalAuthConfigProviderTest {

    @Test
    public void testInitConfig() {

        AuthConfigProvider provider = new LocalAuthConfigProvider();
        assertNull(provider.getAuthenticationService());
        assertEquals(BasicAuthenticationService.SELF_LINK,
                provider.getAuthenticationServiceSelfLink());

        Function<Claims, String> userLinkBuilder = provider
                .getAuthenticationServiceUserLinkBuilder();
        assertNotNull(userLinkBuilder);

        Claims claims = new Claims.Builder().setSubject("test@admiral.com").getResult();

        String userLink = userLinkBuilder.apply(claims);
        assertEquals(UserService.FACTORY_LINK + "/test@admiral.com", userLink);

        Function<Claims, String> userFactoryLinkBuilder = provider
                .getAuthenticationServiceUserFactoryLinkBuilder();
        assertNotNull(userFactoryLinkBuilder);

        String userFactoryLink = userFactoryLinkBuilder.apply(claims);
        assertEquals(UserService.FACTORY_LINK, userFactoryLink);

        assertTrue(provider.createServiceFactories().isEmpty());
    }
}
