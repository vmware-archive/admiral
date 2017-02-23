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
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.ssl.SSLContextBuilder;

import com.vmware.identity.openidconnect.client.ClientConfig;
import com.vmware.identity.openidconnect.client.ConnectionConfig;
import com.vmware.identity.openidconnect.client.MetadataHelper;
import com.vmware.identity.openidconnect.client.OIDCClient;
import com.vmware.identity.openidconnect.common.ClientID;
import com.vmware.identity.openidconnect.common.ProviderMetadata;
import com.vmware.identity.rest.afd.client.AfdClient;
import com.vmware.identity.rest.core.data.CertificateDTO;
import com.vmware.identity.rest.idm.client.IdmClient;

/**
 * General OIDC client metadata.
 */
public class AuthOIDCClient {
    private String domainControllerFQDN;
    private int domainControllerPort;
    private String tenant;
    private AuthCertificateStore certificateStore;
    private ProviderMetadata providerMetadata;
    private RSAPublicKey providerPublicKey;

    /**
     * Constructor.
     */
    public AuthOIDCClient(String domainControllerFQDN, int domainControllerPort, String tenant)
            throws AuthException {
        try {
            this.domainControllerFQDN = domainControllerFQDN;
            this.domainControllerPort = domainControllerPort;
            this.tenant = tenant;
            this.certificateStore = new AuthCertificateStore();

            AfdClient afdClient = setSSLTrustPolicy(domainControllerFQDN, domainControllerPort);
            populateSSLCertificates(afdClient);

            MetadataHelper metadataHelper = new MetadataHelper.Builder(domainControllerFQDN)
                    .domainControllerPort(this.domainControllerPort)
                    .tenant(this.tenant)
                    .keyStore(certificateStore.getKeyStore())
                    .build();
            this.providerMetadata = metadataHelper.getProviderMetadata();
            this.providerPublicKey = metadataHelper.getProviderRSAPublicKey(providerMetadata);

        } catch (Exception e) {
            throw new AuthException("Failed to build client metadata.", e);
        }
    }

    /**
     * Get token handler class instance.
     */
    public AuthTokenHandler getTokenHandler() {
        return new AuthTokenHandler(getOidcClient(), getProviderPublicKey(),
                getProviderMetadata().getIssuer());
    }

    /**
     * Get client handler class instance.
     */
    public AuthClientHandler getClientHandler(
            String user,
            String password)
            throws AuthException {

        return new AuthClientHandler(
                this,
                createIdmClient(domainControllerFQDN, domainControllerPort, user, password),
                getTokenHandler(),
                user,
                password,
                tenant);
    }

    private AfdClient setSSLTrustPolicy(String domainControllerFQDN, int domainControllerPort)
            throws AuthException {
        try {
            return new AfdClient(domainControllerFQDN,
                    domainControllerPort,
                    (hostname, sslSession) -> true,
                    new SSLContextBuilder()
                            .loadTrustMaterial(null, (chain, authType) -> true).build());

        } catch (Exception e) {
            throw new AuthException("Failed to set SSL policy", e);
        }
    }

    private void populateSSLCertificates(AfdClient afdClient) throws AuthException {
        try {
            List<CertificateDTO> certs = afdClient.vecs().getSSLCertificates();
            int index = 1;
            for (CertificateDTO cert : certs) {
                String alias = String.format("VecsSSLCert-%s-%d", cert.getFingerprint(), index);
                certificateStore.getKeyStore().setCertificateEntry(alias,
                        cert.getX509Certificate());
                index++;
            }
        } catch (Exception e) {
            throw new AuthException("Failed to populate SSL Certificates", e);
        }
    }

    /**
     * Get provider publick key. Package visibility.
     */
    RSAPublicKey getProviderPublicKey() {
        return providerPublicKey;
    }

    /**
     * Get provider metadata. Package visibility.
     */
    ProviderMetadata getProviderMetadata() {
        return providerMetadata;
    }

    /**
     * Get OIDC client. Package visibility.
     */
    OIDCClient getOidcClient() {
        return getOidcClient(null);
    }

    /**
     * Create OIDCClient object using the retrieved metadata. Package visibility.
     */
    public OIDCClient getOidcClient(ClientID clientID) {
        ConnectionConfig connectionConfig = new ConnectionConfig(
                providerMetadata,
                providerPublicKey,
                certificateStore.getKeyStore());
        return new OIDCClient(new ClientConfig(connectionConfig, clientID, null));
    }

    private IdmClient createIdmClient(
            String domainControllerFQDN,
            int domainControllerPort,
            String user,
            String password)
            throws AuthException {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(certificateStore.getKeyStore());
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            IdmClient idmClient = new IdmClient(domainControllerFQDN, domainControllerPort,
                    new DefaultHostnameVerifier(),
                    sslContext);

            com.vmware.identity.openidconnect.client.AccessToken accessToken = getTokenHandler()
                    .getAdminServerAccessToken(user, password)
                    .getAccessToken();

            com.vmware.identity.rest.core.client.AccessToken restAccessToken = new com.vmware.identity.rest.core.client.AccessToken(
                    accessToken.getValue(),
                    com.vmware.identity.rest.core.client.AccessToken.Type.JWT);
            idmClient.setToken(restAccessToken);
            return idmClient;
        } catch (Exception e) {
            throw new AuthException("Failed to createIdmClient", e);
        }
    }
}
