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

package com.vmware.admiral.request.compute.enhancer;

import static com.vmware.admiral.request.compute.enhancer.ComputeDescriptionEnhancer.getCustomProperty;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.CertificateUtil;
import com.vmware.admiral.common.util.CertificateUtil.CertChainKeyPair;
import com.vmware.admiral.common.util.KeyUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class ServerCertComputeDescriptionEnhancer implements ComputeDescriptionEnhancer {
    private static final Pattern CERTS_PLACEHOLDER = Pattern
            .compile("\\{\\{serverCerts\\}\\}:");

    private static ObjectMapper objectMapper;

    {
        YAMLFactory factory = new YAMLFactory();
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);

        objectMapper = new ObjectMapper(factory);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
    }

    private StatefulService sender;

    public ServerCertComputeDescriptionEnhancer(StatefulService sender) {
        this.sender = sender;
    }

    @Override
    public void enhance(EnhanceContext context, ComputeDescription cd,
            BiConsumer<ComputeDescription, Throwable> callback) {
        String fileContent = getCustomProperty(cd,
                ComputeConstants.COMPUTE_CONFIG_CONTENT_PROP_NAME);
        if (fileContent == null) {
            callback.accept(cd, null);
            return;
        }

        processCaCertSign(sender, cd, callback, fileContent);
    }

    private void processCaCertSign(StatefulService sender, ComputeDescription cd,
            BiConsumer<ComputeDescription, Throwable> callback, String fileContent) {
        Matcher matcher = CERTS_PLACEHOLDER.matcher(fileContent);
        if (matcher.find()) {
            Operation.createGet(sender, ManagementUriParts.AUTH_CREDENTIALS_CA_LINK)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            sender.logSevere(
                                    "Exception retrieving ca credentials. Error: %s",
                                    Utils.toString(e));
                            callback.accept(cd, e);
                            return;
                        }
                        AuthCredentialsServiceState caCred = o
                                .getBody(AuthCredentialsServiceState.class);
                        String content = generateAndReplaceServerCerts(matcher, fileContent,
                                caCred);
                        cd.customProperties.put(ComputeConstants.COMPUTE_CONFIG_CONTENT_PROP_NAME,
                                content);
                        cd.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                                ManagementUriParts.AUTH_CREDENTIALS_CLIENT_LINK);
                        callback.accept(cd, null);
                    })
                    .sendWith(sender);
        } else {
            callback.accept(cd, null);
        }
    }

    private String generateAndReplaceServerCerts(Matcher matcher, String fileContent,
            AuthCredentialsServiceState cred) {

        KeyPair caKeyPair = CertificateUtil.createKeyPair(cred.privateKey);
        X509Certificate caCertificate = CertificateUtil.createCertificate(cred.publicKey);
        CertChainKeyPair signedForServer = CertificateUtil.generateSigned("computeServer",
                caCertificate, caKeyPair.getPrivate());
        try {
            ArrayList<WriteFiles> list = new ArrayList<>();
            list.add(new WriteFiles("/etc/docker/ca.pem", "0644", cred.publicKey));
            list.add(new WriteFiles("/etc/docker/server.pem", "0644",
                    CertificateUtil.toPEMformat(signedForServer.getCertificate())));
            list.add(new WriteFiles("/etc/docker/server-key.pem", "0600",
                    KeyUtil.toPEMFormat(signedForServer.getPrivateKey())));
            Map<String, Object> writeFiles = new LinkedHashMap<>();
            writeFiles.put("write_files", list);

            String value = objectMapper.writeValueAsString(writeFiles);

            fileContent = matcher.replaceFirst(value);

        } catch (Exception e) {
            sender.logInfo(() -> String.format("Error writing server certs in cloud-init file",
                    Utils.toString(e)));
        }
        return fileContent;
    }

    @SuppressWarnings("unused")
    private static class WriteFiles {
        public String path;
        public String permissions;
        public String content;

        public WriteFiles(String path, String permissions, String content) {
            this.path = path;
            this.permissions = permissions;
            this.content = content;
        }
    }
}
