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

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNullOrEmpty;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.logging.Logger;

import com.vmware.admiral.auth.lightwave.pc.AuthClientHandler;
import com.vmware.admiral.auth.lightwave.pc.AuthException;
import com.vmware.admiral.auth.lightwave.pc.AuthOIDCClient;
import com.vmware.identity.openidconnect.common.ClientID;
import com.vmware.identity.rest.idm.data.OIDCClientDTO;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.ServiceHost.Arguments;

public class RegisterWithLightwave {

    public static final int DEFAULT_STS_PORT = 443;

    private static final Logger LOG = Logger.getLogger(RegisterWithLightwave.class.getName());

    public static class LightwaveArguments extends Arguments {
        public String tenant; // e.g. vsphere.local
        public String domainController; // e.g. Lightwave IP
        public Integer domainControllerPort;
        public String username;
        public String password;
        public String loginRedirectUrl; // e.g. http://localhost:8282/sso/token
        public String postLogoutRedirectUrl; // e.g. http://localhost:8282/sso/ui/logout.html
        public String resourceServer; // e.g. rs_admiral
        public String configFile;

        public void validate() {
            assertNotNullOrEmpty(tenant, "tenant");
            assertNotNullOrEmpty(domainController, "domainController");
            if (domainControllerPort == null) {
                domainControllerPort = DEFAULT_STS_PORT;
            }
            assertNotNullOrEmpty(username, "username");
            assertNotNullOrEmpty(password, "password");
            if (loginRedirectUrl == null || loginRedirectUrl.trim().isEmpty()) {
                loginRedirectUrl = "http://localhost:8282/sso/token";
            }
            if (postLogoutRedirectUrl == null || postLogoutRedirectUrl.trim().isEmpty()) {
                postLogoutRedirectUrl = "http://localhost:8282/sso/ui/logout.html";
            }
            if (resourceServer == null || resourceServer.trim().isEmpty()) {
                resourceServer = "rs_admiral";
            }
            assertNotNullOrEmpty(configFile, "configFile");
        }
    }

    public static void register(String[] args) throws Exception {
        assertNotEmpty(args, "args");

        LOG.info("Initializing...");

        LightwaveArguments params = new LightwaveArguments();
        CommandLineArgumentParser.parseFromArguments(params, args);
        params.validate();

        ClientID clientID = register(params);

        store(params, clientID);

        LOG.info("Done! Configuration stored in '" + params.configFile + "'.");
    }

    static ClientID register(LightwaveArguments params) throws AuthException {
        URI loginRedirectUrl = URI.create(params.loginRedirectUrl);
        URI postLogoutRedirectUri = URI.create(params.postLogoutRedirectUrl);
        List<String> resourceServers = Arrays.asList(params.resourceServer);

        LOG.info("Registering...");

        AuthOIDCClient client = new AuthOIDCClient(params.domainController,
                params.domainControllerPort, params.tenant);

        AuthClientHandler handler = client.getClientHandler(params.username, params.password);

        OIDCClientDTO clientDTO = handler.registerImplicitClient(loginRedirectUrl,
                postLogoutRedirectUri, resourceServers);

        return new ClientID(clientDTO.getClientId());
    }

    static void store(LightwaveArguments params, ClientID clientID) throws IOException {
        Properties props = new Properties() {
            private static final long serialVersionUID = 1L;

            @Override
            public synchronized Enumeration<Object> keys() {
                return Collections.enumeration(new TreeSet<>(super.keySet()));
            }
        };

        props.put("tenant", params.tenant);
        props.put("domain-controller", params.domainController);
        props.put("domain-controller.port", String.valueOf(params.domainControllerPort));
        props.put("resource-server", params.resourceServer);
        props.put("client-id", clientID.getValue());

        try (FileOutputStream fos = new FileOutputStream(params.configFile)) {
            props.store(fos, null);
        }
    }

    public static void main(String[] args) throws Exception {
        RegisterWithLightwave.register(args);
    }

}
