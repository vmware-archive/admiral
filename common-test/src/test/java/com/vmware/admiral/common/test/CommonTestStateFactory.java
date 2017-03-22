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

package com.vmware.admiral.common.test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.UUID;

import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class CommonTestStateFactory {
    public static final String AUTH_CREDENTIALS_ID = "test-credentials-id";
    public static final String REGISTRATION_DOCKER_ID = "test-docker-registration-id";
    public static final String SSL_TRUST_CERT_ID = "test-ssl-trust-cert-id";
    public static final String DOCKER_HOST_REGISTRATION_NAME = "docker-host";
    public static final String DOCKER_COMPUTE_ID = "test-docker-host-compute-id";
    public static final String ENDPOINT_ID = "test-endpoint-id";
    public static final String ENDPOINT_REGION_ID = "us-east-1"; // used for zoneId too

    public static AuthCredentialsServiceState createAuthCredentials(boolean uniqueSelfLink) {
        AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
        authCredentials.documentSelfLink = AUTH_CREDENTIALS_ID;
        if (uniqueSelfLink) {
            authCredentials.documentSelfLink += "-" + UUID.randomUUID();
        }
        authCredentials.type = AuthCredentialsType.PublicKey.name();
        authCredentials.userEmail = "core";
        authCredentials.privateKey = getFileContent("docker-host-private-key.PEM");
        return authCredentials;
    }

    public static SslTrustCertificateState createSslTrustCertificateState(String pemFileName,
            String id) {

        SslTrustCertificateState sslTrustState = new SslTrustCertificateState();
        sslTrustState.documentSelfLink = id;
        sslTrustState.certificate = getFileContent(pemFileName);
        return sslTrustState;
    }

    // TODO: This method seems pretty similar to FileUtil.getResourceAsString...
    public static String getFileContent(String fileName) {
        try (InputStream is = CommonTestStateFactory.class.getClassLoader()
                .getResourceAsStream(fileName)) {
            if (is != null) {
                return readFile(new InputStreamReader(is));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try (FileReader fileReader = new FileReader(fileName)) {
            return readFile(fileReader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private static String readFile(Reader reader) throws IOException {
        BufferedReader br = new BufferedReader(reader);
        StringBuilder sb = new StringBuilder();
        String read = br.readLine();
        String newLine = System.getProperty("line.separator");
        while (read != null) {
            sb.append(read);
            sb.append(newLine);
            read = br.readLine();
        }
        return sb.toString();
    }
}
