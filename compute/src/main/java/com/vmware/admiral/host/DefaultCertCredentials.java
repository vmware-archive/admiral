/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.admiral.common.util.CertificateUtil;
import com.vmware.admiral.common.util.CertificateUtil.CertChainKeyPair;
import com.vmware.admiral.common.util.KeyUtil;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class DefaultCertCredentials {

    public static final String AUTH_CREDENTIALS_CA_LINK = UriUtils.buildUriPath(
            AuthCredentialsService.FACTORY_LINK, "default-ca-cert");
    public static final String AUTH_CREDENTIALS_CLIENT_LINK = UriUtils.buildUriPath(
            AuthCredentialsService.FACTORY_LINK, "default-client-cert");
    public static final String AUTH_CREDENTIALS_SERVER_LINK = UriUtils.buildUriPath(
            AuthCredentialsService.FACTORY_LINK, "default-server-cert");

    private static final String CA_CERT_PEM_FILE = System.getProperty("default.ca.cert.pem.file",
            "certs/default-ca.pem");
    private static final String CA_KEY_PEM_FILE = System.getProperty("default.ca.key.pem.file",
            "certs/default-ca-key.pem");

    public static List<AuthCredentialsServiceState> buildDefaultStateInstances() {
        String caCert = loadFileContent(CA_CERT_PEM_FILE);
        String caKey = loadFileContent(CA_KEY_PEM_FILE);
        X509Certificate caCertificate = CertificateUtil.createCertificate(caCert);
        KeyPair caKeyPair = CertificateUtil.createKeyPair(caKey);

        ArrayList<AuthCredentialsServiceState> credentials = new ArrayList<>();
        credentials.add(createCACredentials(caCert, caKey));
        credentials.add(createClientCredentials(caCertificate, caKeyPair));
        credentials.add(createServerCredentials(caCertificate, caKeyPair));
        return credentials;
    }

    private static AuthCredentialsServiceState createServerCredentials(
            X509Certificate caCertificate, KeyPair caKeyPair) {
        AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
        authCredentials.documentSelfLink = AUTH_CREDENTIALS_SERVER_LINK;
        authCredentials.type = AuthCredentialsType.PublicKey.name();
        authCredentials.userEmail = "core";

        CertChainKeyPair signedForServer = CertificateUtil.generateSigned("computeServer",
                caCertificate, caKeyPair.getPrivate());
        authCredentials.publicKey = CertificateUtil.toPEMformat(signedForServer.getCertificate());
        authCredentials.privateKey = KeyUtil.toPEMFormat(signedForServer.getPrivateKey());
        return authCredentials;
    }

    private static AuthCredentialsServiceState createClientCredentials(
            X509Certificate caCertificate, KeyPair caKeyPair) {
        AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
        authCredentials.documentSelfLink = AUTH_CREDENTIALS_CLIENT_LINK;
        authCredentials.type = AuthCredentialsType.PublicKey.name();
        authCredentials.userEmail = "core";

        CertChainKeyPair signedForClient = CertificateUtil.generateSignedForClient("computeClient",
                caCertificate, caKeyPair.getPrivate());
        authCredentials.publicKey = CertificateUtil.toPEMformat(signedForClient.getCertificate());
        authCredentials.privateKey = KeyUtil.toPEMFormat(signedForClient.getPrivateKey());
        return authCredentials;
    }

    private static AuthCredentialsServiceState createCACredentials(String caCert, String caKey) {
        AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
        authCredentials.documentSelfLink = AUTH_CREDENTIALS_CA_LINK;
        authCredentials.type = AuthCredentialsType.PublicKey.name();
        authCredentials.userEmail = "core";
        authCredentials.publicKey = caCert;
        authCredentials.privateKey = caKey;
        return authCredentials;
    }

    private static String loadFileContent(String pemFile) {
        try (InputStream in = DefaultCertCredentials.class.getClassLoader()
                .getResourceAsStream(pemFile)) {

            if (in == null) {
                return null;
            }
            try (Scanner sc = new Scanner(in, "UTF-8")) {
                return sc.useDelimiter("\\A").next();
            }

        } catch (IOException e) {
            Utils.logWarning("Unable to load pem file %s, reason %s", pemFile, e.getMessage());
            return null;
        }
    }
}
