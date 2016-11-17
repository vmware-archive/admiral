/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 */

package com.vmware.admiral;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;

import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class CommonTestStateFactory {
    public static final String AUTH_CREDENTIALS_ID = "test-credentials-id";
    public static final String COMPUTE_DESC_ID = "test-continaer-compute-desc-id";
    public static final String REGISTRATION_DOCKER_ID = "test-docker-registration-id";
    public static final String SSL_TRUST_CERT_ID = "test-ssl-trust-cert-id";
    public static final String DOCKER_HOST_REGISTRATION_NAME = "docker-host";
    public static final String DOCKER_COMPUTE_ID = "test-docker-host-compute-id";

    public static AuthCredentialsServiceState createAuthCredentials() throws Exception {
        AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
        authCredentials.documentSelfLink = AUTH_CREDENTIALS_ID;
        authCredentials.type = AuthCredentialsType.PublicKey.name();
        authCredentials.userEmail = "core";
        authCredentials.privateKey = getFileContent("docker-host-private-key.PEM");
        return authCredentials;
    }

    public static SslTrustCertificateState createSslTrustCertificateState(String pemFileName,
            String id) throws Exception {

        SslTrustCertificateState sslTrustState = new SslTrustCertificateState();
        sslTrustState.documentSelfLink = id;
        sslTrustState.certificate = getFileContent(pemFileName);
        return sslTrustState;
    }

    public static String getFileContent(String fileName) throws Exception {
        // It should work because a trust store which contains the cert is passed as argument.
        URI targetFile;
        targetFile = CommonTestStateFactory.class.getResource(fileName).toURI();
        try (InputStream is = new FileInputStream(targetFile.getPath())) {
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
