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

package com.vmware.admiral.auth.lightwave.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.vmware.admiral.auth.lightwave.pc.AuthClientHandler;
import com.vmware.admiral.auth.lightwave.pc.AuthException;
import com.vmware.admiral.auth.lightwave.pc.AuthOIDCClient;
import com.vmware.identity.rest.idm.data.OIDCClientDTO;
import com.vmware.xenon.common.LocalizableValidationException;

@RunWith(PowerMockRunner.class)
@PrepareForTest(RegisterWithLightwave.class)
public class RegisterWithLightwaveTest {

    @Test
    public void testRequiredArguments() {

        try {
            RegisterWithLightwave.register((String[]) null);
            fail("exception expected!");
        } catch (Exception e) {
            assertTrue(e instanceof LocalizableValidationException);
            assertEquals("'args' cannot be empty", e.getMessage());
        }

        List<String> args = new ArrayList<>();
        try {
            RegisterWithLightwave.register(args.toArray(new String[] {}));
            fail("exception expected!");
        } catch (Exception e) {
            assertTrue(e instanceof LocalizableValidationException);
            assertEquals("'args' cannot be empty", e.getMessage());
        }

        args.add("--foo=bar");
        try {
            RegisterWithLightwave.register(args.toArray(new String[] {}));
            fail("exception expected!");
        } catch (Exception e) {
            assertTrue(e instanceof LocalizableValidationException);
            assertEquals("'tenant' is required", e.getMessage());
        }

        args.add("--tenant=tenant");
        try {
            RegisterWithLightwave.register(args.toArray(new String[] {}));
            fail("exception expected!");
        } catch (Exception e) {
            assertTrue(e instanceof LocalizableValidationException);
            assertEquals("'domainController' is required", e.getMessage());
        }

        args.add("--domainController=127.0.0.1");
        try {
            RegisterWithLightwave.register(args.toArray(new String[] {}));
            fail("exception expected!");
        } catch (Exception e) {
            assertTrue(e instanceof LocalizableValidationException);
            assertEquals("'username' is required", e.getMessage());
        }

        args.add("--username=user");
        try {
            RegisterWithLightwave.register(args.toArray(new String[] {}));
            fail("exception expected!");
        } catch (Exception e) {
            assertTrue(e instanceof LocalizableValidationException);
            assertEquals("'password' is required", e.getMessage());
        }

        args.add("--password=pass");
        try {
            RegisterWithLightwave.register(args.toArray(new String[] {}));
            fail("exception expected!");
        } catch (Exception e) {
            assertTrue(e instanceof LocalizableValidationException);
            assertEquals("'configFile' is required", e.getMessage());
        }

        args.add("--configFile=/foo/bar");
        args.add("--domainControllerPort= ");
        args.add("--loginRedirectUrl= ");
        args.add("--postLogoutRedirectUrl= ");
        args.add("--resourceServer= ");
        try {
            RegisterWithLightwave.register(args.toArray(new String[] {}));
            fail("exception expected!");
        } catch (Exception e) {
            assertTrue(e instanceof AuthException);
            assertEquals("Failed to build client metadata.", e.getMessage());
        }
    }

    @Test
    public void testRegistrationSuccessful() throws Exception {

        File configFile = File.createTempFile("lightwave-config.properties", null);
        configFile.deleteOnExit();

        List<String> args = new ArrayList<>();

        args.add("--tenant=qe");
        args.add("--domainController=127.0.0.1");
        args.add("--domainControllerPort=443");
        args.add("--username=user");
        args.add("--password=pass");
        args.add("--loginRedirectUrl=http://localhost:8282/sso/token");
        args.add("--postLogoutRedirectUrl=http://localhost:8282/sso/ui/logout.html");
        args.add("--resourceServer=rs_admiral");
        args.add("--configFile=" + configFile.getPath());

        AuthOIDCClient client = mock(AuthOIDCClient.class);
        AuthClientHandler handler = mock(AuthClientHandler.class);
        OIDCClientDTO clientDTO = mock(OIDCClientDTO.class);

        Mockito.when(clientDTO.getClientId()).thenReturn("1234");
        Mockito.when(handler.registerImplicitClient(
                URI.create("http://localhost:8282/sso/token"),
                URI.create("http://localhost:8282/sso/ui/logout.html"),
                Arrays.asList("rs_admiral")))
                .thenReturn(clientDTO);
        Mockito.when(client.getClientHandler("user", "pass")).thenReturn(handler);
        PowerMockito.whenNew(AuthOIDCClient.class).withAnyArguments().thenReturn(client);

        RegisterWithLightwave.main(args.toArray(new String[] {}));

        List<String> allLines = Files.readAllLines(configFile.toPath());
        assertNotNull(allLines);
        assertEquals(6, allLines.size());
        assertTrue(allLines.get(0).startsWith("#"));
        assertEquals("client-id=1234", allLines.get(1));
        assertEquals("domain-controller=127.0.0.1", allLines.get(2));
        assertEquals("domain-controller.port=443", allLines.get(3));
        assertEquals("resource-server=rs_admiral", allLines.get(4));
        assertEquals("tenant=qe", allLines.get(5));
    }

}
