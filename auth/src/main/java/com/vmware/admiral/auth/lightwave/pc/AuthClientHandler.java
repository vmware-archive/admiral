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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.List;

import com.vmware.identity.rest.idm.client.IdmClient;
import com.vmware.identity.rest.idm.data.OIDCClientDTO;
import com.vmware.identity.rest.idm.data.OIDCClientMetadataDTO;

/**
 * Class that handles oauth clients.
 */
public class AuthClientHandler {
    private static final String LOGIN_STATE = "L";
    private static final String LOGOUT_STATE = "E";
    private static final String LOGIN_REQUEST_NONCE = "1";
    private static final String LOGOUT_URL_ID_TOKEN_START = "id_token_hint";
    private static final String LOGOUT_URL_ID_TOKEN_PLACEHOLDER = "[ID_TOKEN_PLACEHOLDER]";

    private final AuthOIDCClient oidcClient;
    private final String user;
    private final String password;
    private final String tenant;
    private IdmClient idmClient;
    private AuthTokenHandler tokenHandler;

    /**
     * Constructor.
     *
     * @param oidcClient
     *            - Provides the oidc client.
     * @param user
     *            - Provides the user of the LightWave Admin.
     * @param password
     *            - Provides the password of the LightWave Admin.
     * @throws AuthException
     * @throws URISyntaxException
     */
    AuthClientHandler(
            AuthOIDCClient oidcClient,
            IdmClient idmClient,
            AuthTokenHandler tokenHandler,
            String user,
            String password,
            String tenant)
            throws AuthException {
        this.oidcClient = oidcClient;
        this.idmClient = idmClient;
        this.tokenHandler = tokenHandler;
        this.user = user;
        this.password = password;
        this.tenant = tenant;
    }

    /**
     * Register implicit client and retrieve login and logout URIs.
     *
     * @param loginRedirectURI
     * @param logoutRedirectURI
     * @param resourceServers
     * @return
     * @throws AuthException
     */
    public OIDCClientDTO registerImplicitClient(URI loginRedirectURI, URI logoutRedirectURI,
            List<String> resourceServers)
            throws AuthException {
        try {
            OIDCClientDTO oidcClientDTO = registerClient(loginRedirectURI, loginRedirectURI,
                    logoutRedirectURI);
            return oidcClientDTO;
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(String
                    .format("Failed to register implicit client with loginRedirectURI %s and " +
                            "logoutRedirectURI %s ", loginRedirectURI, logoutRedirectURI),
                    e);
        }
    }

    /**
     * Register OAuth client.
     *
     * @param redirectURI
     * @return
     * @throws AuthException
     */
    public OIDCClientDTO registerClient(URI redirectURI) throws AuthException {
        return registerClient(redirectURI, redirectURI, redirectURI);
    }

    /**
     * Register OAuth client.
     */
    private OIDCClientDTO registerClient(URI redirectURI, URI logoutURI, URI postLogoutURI)
            throws AuthException {
        Exception registerException = null;

        try {
            // register an OIDC client
            OIDCClientMetadataDTO oidcClientMetadataDTO = new OIDCClientMetadataDTO.Builder()
                    .withRedirectUris(Arrays.asList(redirectURI.toString()))
                    .withPostLogoutRedirectUris(Arrays.asList(postLogoutURI.toString()))
                    .withLogoutUri(logoutURI.toString())
                    .withTokenEndpointAuthMethod("none")
                    .build();
            idmClient.oidcClient().register(tenant, oidcClientMetadataDTO);

        } catch (Exception e) {
            registerException = new AuthException("failed to registerClient", e);
        }

        try {
            List<OIDCClientDTO> oidcClientDTOList = idmClient.oidcClient().getAll(tenant);
            for (OIDCClientDTO oidcClientDTO : oidcClientDTOList) {
                if (oidcClientDTO.getOIDCClientMetadataDTO().getRedirectUris().size() == 1 &&
                        oidcClientDTO.getOIDCClientMetadataDTO().getRedirectUris().iterator().next()
                                .compareTo(redirectURI.toString()) == 0) {
                    return oidcClientDTO;
                }
            }
        } catch (Exception e) {
            throw new AuthException("failed to registerClient", e);
        }

        throw new AuthException("Client expected to be registered,  but not found",
                registerException);
    }

    /**
     * Replace the id_token part in logout URL with placeholder.
     *
     * @param logoutUri
     * @throws AuthException
     */
    public static URI replaceIdTokenWithPlaceholder(URI logoutUri)
            throws IllegalArgumentException {
        String placeholderValue = "";
        String keyToMatch;
        try {
            keyToMatch = URLDecoder.decode(LOGOUT_URL_ID_TOKEN_START, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        final String urlQuery = logoutUri.getQuery();

        if (urlQuery == null) {
            throw new IllegalArgumentException(
                    String.format("Logout URL %s has invalid format", logoutUri.toString()));
        }

        final String[] queryParams = urlQuery.split("&");

        for (String param : queryParams) {
            final String[] pairs = param.split("=");
            if (pairs[0].equals(keyToMatch)) {
                placeholderValue = pairs[1];
                break;
            }
        }

        if (placeholderValue.equals("")) {
            throw new IllegalArgumentException(
                    String.format("Logout URL %s has invalid format", logoutUri.toString()));
        }

        String logoutStr = logoutUri.toString();
        return URI.create(logoutStr.replace(placeholderValue, LOGOUT_URL_ID_TOKEN_PLACEHOLDER));
    }

}
