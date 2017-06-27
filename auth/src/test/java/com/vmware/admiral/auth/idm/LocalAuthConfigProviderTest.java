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

import org.junit.Test;

import com.vmware.admiral.auth.AuthBaseTest;
import com.vmware.admiral.auth.idm.local.LocalAuthConfigProvider;
import com.vmware.xenon.services.common.authn.BasicAuthenticationService;

public class LocalAuthConfigProviderTest extends AuthBaseTest {

    @Test
    public void testInitConfig() {

        AuthConfigProvider provider = new LocalAuthConfigProvider();
        assertNull(provider.getAuthenticationService());
        assertEquals(BasicAuthenticationService.SELF_LINK,
                provider.getAuthenticationServiceSelfLink());
        assertNotNull(provider.getAuthenticationServiceUserLinkBuilder());
        assertNull(provider.createUserServiceFactory());
    }
}
