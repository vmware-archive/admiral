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

package com.vmware.admiral.auth.lightwave.pc;

import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

import com.vmware.identity.openidconnect.client.GroupMembershipType;
import com.vmware.identity.openidconnect.client.OIDCClient;
import com.vmware.identity.openidconnect.client.OIDCTokens;
import com.vmware.identity.openidconnect.client.ResourceServerAccessToken;
import com.vmware.identity.openidconnect.client.TokenSpec;
import com.vmware.identity.openidconnect.client.TokenValidationException;
import com.vmware.identity.openidconnect.common.Issuer;
import com.vmware.identity.openidconnect.common.TokenType;

/**
 * Authorization token retrieval logic.
 */
public class AuthTokenHandler {

    public static final String ADMIN_SERVER_RESOURCE = "rs_admin_server";
    private final OIDCClient oidcClient;
    private final RSAPublicKey providerPublicKey;
    private final Issuer issuer;

    /**
     * Constructor.
     */
    AuthTokenHandler(OIDCClient oidcClient, RSAPublicKey providerPublickKey, Issuer issuer) {
        this.oidcClient = oidcClient;
        this.providerPublicKey = providerPublickKey;
        this.issuer = issuer;
    }

    public OIDCTokens getAdminServerAccessToken(String username, String password)
            throws AuthException {
        return getAuthTokensByPassword(username, password, ADMIN_SERVER_RESOURCE);
    }

    /**
     * Get id, access and refresh tokens by username and password.
     *
     * @return AccessToken.
     */
    private OIDCTokens getAuthTokensByPassword(
            String username,
            String password,
            String resourceServer)
            throws AuthException {
        try {
            TokenSpec tokenSpec = new TokenSpec.Builder(TokenType.BEARER).refreshToken(true)
                    .idTokenGroups(GroupMembershipType.NONE)
                    .accessTokenGroups(GroupMembershipType.FULL)
                    .resourceServers(Arrays.asList(resourceServer)).build();

            return oidcClient.acquireTokensByPassword(username, password, tokenSpec);
        } catch (Exception e) {
            throw new AuthException("Failed to acquire tokens.", e);
        }
    }

    /**
     * Checks if the passed accessToken is valid. If not, throws the AuthException.
     *
     * @param accessToken
     * @param resourceServer
     * @param clockTolerance
     * @return ResourceServerAccessToken
     * @throws TokenValidationException
     */
    public ResourceServerAccessToken parseAccessToken(String accessToken, String resourceServer,
            long clockTolerance)
            throws TokenValidationException {
        return ResourceServerAccessToken.build(
                accessToken,
                providerPublicKey,
                issuer,
                resourceServer,
                clockTolerance);
    }
}
