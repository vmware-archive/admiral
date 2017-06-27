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

package com.vmware.admiral.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Base64;

import org.junit.Test;

import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class AuthUtilsTest {

    @Test
    public void testCreateAuthorizationHeader() {
        assertNull(AuthUtils.createAuthorizationHeader(null));

        String email = "test@test.test";
        String password = "test";
        String expectedHeader = String.format("Basic %s",
                new String(Base64.getEncoder()
                        .encode(String.format("%s:%s", email, password).getBytes())));
        AuthCredentialsServiceState credentials = new AuthCredentialsServiceState();
        credentials.type = AuthCredentialsType.Password.toString();
        credentials.userEmail = email;
        credentials.privateKey = password;
        assertEquals(expectedHeader, AuthUtils.createAuthorizationHeader(credentials));
    }

}
