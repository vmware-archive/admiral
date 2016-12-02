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
import java.util.UUID;

import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class CommonTestStateFactory {
    public static final String AUTH_CREDENTIALS_ID = "test-credentials-id";
    public static final String REGISTRATION_DOCKER_ID = "test-docker-registration-id";

    public static AuthCredentialsServiceState createAuthCredentials(boolean uniqueSelfLink) {
        AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
        authCredentials.documentSelfLink = AUTH_CREDENTIALS_ID;
        if (uniqueSelfLink) {
            authCredentials.documentSelfLink += "-" + UUID.randomUUID();
        }
        authCredentials.type = AuthCredentialsType.PublicKey.name();
        authCredentials.userEmail = "core";
        authCredentials.privateKey = getFileContent("certs/client-key.pem");
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
