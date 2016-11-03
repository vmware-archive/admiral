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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Scanner;

import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.security.EncryptionUtils;
import com.vmware.admiral.common.util.CertificateUtil;
import com.vmware.admiral.common.util.CertificateUtil.CertChainKeyPair;
import com.vmware.admiral.common.util.KeyUtil;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class CaSigningCertService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.CONFIG_CA_CREDENTIALS;

    private static final String CA_CERT_PEM_FILE = "certs/default-ca.pem";
    private static final String CA_KEY_PEM_FILE = "certs/default-ca-key.pem";

    @Override
    public void handleStart(Operation startOp) {
        Operation caCertOp = Operation
                .createGet(getHost(), UriUtils.buildUriPath(ManagementUriParts.CONFIG_PROPS,
                        "default.ca.cert.pem.file"));
        Operation caKeyOp = Operation
                .createGet(getHost(), UriUtils.buildUriPath(ManagementUriParts.CONFIG_PROPS,
                        "default.ca.key.pem.file"));
        OperationSequence.create(caCertOp, caKeyOp)
                .setCompletion((ops, exs) -> {
                    if (exs == null) {
                        ConfigurationState certBody = ops.get(caCertOp.getId())
                                .getBody(ConfigurationState.class);
                        ConfigurationState keyBody = ops.get(caKeyOp.getId())
                                .getBody(ConfigurationState.class);

                        registerCaCertIfNeeded(loadContent(certBody), loadContent(keyBody),
                                startOp);
                        return;
                    }
                    registerCaCertIfNeeded(null, null, startOp);
                })
                .sendWith(this);
    }

    private void registerCaCertIfNeeded(String caCert, String caKey, Operation startOp) {
        Operation.createGet(this, ManagementUriParts.AUTH_CREDENTIALS_CA_LINK)
                .setCompletion((o, e) -> {
                    String cert = caCert;
                    String key = caKey;
                    if (caCert == null || caKey == null) {
                        cert = loadFileContent(CA_CERT_PEM_FILE, true);
                        key = loadFileContent(CA_KEY_PEM_FILE, true);
                    }
                    if (e != null) {
                        registerCaCert(cert, key, startOp);
                        return;
                    }
                    AuthCredentialsServiceState caCred = o
                            .getBody(AuthCredentialsServiceState.class);
                    if (caCred.publicKey.equals(cert)) {
                        registerClientCredIfNeeded(caCred, cert, key, startOp);
                    } else {
                        registerCaCert(cert, key, startOp);
                    }
                })
                .sendWith(this);
    }

    private void registerClientCredIfNeeded(AuthCredentialsServiceState caCred, String caCert,
            String caKey, Operation startOp) {
        Operation.createGet(this, ManagementUriParts.AUTH_CREDENTIALS_CLIENT_LINK)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        registerClientCred(caCert, caKey, startOp);
                        return;
                    }
                    startOp.complete();
                })
                .sendWith(this);
    }

    private void registerClientCred(String caCert, String caKey, Operation startOp) {
        createClientCredentials(caCert, caKey)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        startOp.fail(e);
                        return;
                    }
                    startOp.complete();
                })
                .sendWith(this);

    }

    private void registerCaCert(String caCert, String caKey, Operation startOp) {

        OperationSequence.create(createCaCredentials(caCert, caKey),
                createClientCredentials(caCert, caKey))
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        startOp.fail(exs.values().iterator().next());
                        return;
                    }
                    startOp.complete();
                })
                .sendWith(this);
    }

    private Operation createCaCredentials(String caCert, String caKey) {
        AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
        authCredentials.documentSelfLink = ManagementUriParts.AUTH_CREDENTIALS_CA_LINK;
        authCredentials.type = AuthCredentialsType.PublicKeyCA.name();
        authCredentials.userEmail = "core";
        authCredentials.publicKey = caCert;
        authCredentials.privateKey = EncryptionUtils.encrypt(caKey);
        return Operation.createPost(this, AuthCredentialsService.FACTORY_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(authCredentials);
    }

    private Operation createClientCredentials(String caCert, String caKey) {
        X509Certificate caCertificate = CertificateUtil.createCertificate(caCert);
        KeyPair caKeyPair = CertificateUtil.createKeyPair(caKey);

        AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
        authCredentials.documentSelfLink = ManagementUriParts.AUTH_CREDENTIALS_CLIENT_LINK;
        authCredentials.type = AuthCredentialsType.PublicKey.name();
        authCredentials.userEmail = "core";

        CertChainKeyPair signedForClient = CertificateUtil.generateSignedForClient("computeClient",
                caCertificate, caKeyPair.getPrivate());
        authCredentials.publicKey = CertificateUtil.toPEMformat(signedForClient.getCertificate());
        authCredentials.privateKey = EncryptionUtils.encrypt(
                KeyUtil.toPEMFormat(signedForClient.getPrivateKey()));
        return Operation.createPost(this, AuthCredentialsService.FACTORY_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(authCredentials);
    }

    private String loadContent(ConfigurationState state) {
        if (state.value == null || state.value.isEmpty()) {
            return null;
        }
        Path filePath = Paths.get(state.value);
        if (filePath.toFile().exists()) {
            File file = filePath.toFile();
            return loadFileContent(file.getAbsolutePath(), false);
        }
        return null;
    }

    private String loadFileContent(String pemFile, boolean isResource) {
        try (InputStream in = getInputStream(pemFile, isResource)) {

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

    private InputStream getInputStream(String resourceFile, boolean isResource)
            throws FileNotFoundException {
        InputStream inputStream = null;
        if (isResource) {
            inputStream = CaSigningCertService.class.getClassLoader()
                    .getResourceAsStream(resourceFile);
        } else {
            inputStream = new FileInputStream(resourceFile);
        }
        return inputStream;
    }

}
