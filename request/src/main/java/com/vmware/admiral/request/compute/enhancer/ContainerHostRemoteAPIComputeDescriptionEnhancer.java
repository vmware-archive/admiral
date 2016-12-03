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

import java.net.URI;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.CertificateUtil;
import com.vmware.admiral.common.util.CertificateUtil.CertChainKeyPair;
import com.vmware.admiral.common.util.KeyUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class ContainerHostRemoteAPIComputeDescriptionEnhancer extends ComputeDescriptionEnhancer {
    private static final Pattern REMOTE_API_PORT = Pattern
            .compile("\\{\\{ remote_api_port \\}\\}");

    private ServiceHost host;
    private URI referer;

    public ContainerHostRemoteAPIComputeDescriptionEnhancer(ServiceHost host, URI referer) {
        this.host = host;
        this.referer = referer;
    }

    @Override
    public void enhance(EnhanceContext context, ComputeDescription cd,
            BiConsumer<ComputeDescription, Throwable> callback) {
        String adapterType = getCustomProperty(cd,
                ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME);
        if (adapterType == null || !DockerAdapterType.API.name().equals(adapterType)) {
            callback.accept(cd, null);
            return;
        }

        applyPort(context, cd);
        processCaCertSign(context, cd, callback);
    }

    private void applyPort(EnhanceContext context, ComputeDescription cd) {
        String portValue = getCustomProperty(cd, ContainerHostService.DOCKER_HOST_PORT_PROP_NAME);
        int port = 443;
        if (portValue != null) {
            try {
                port = Integer.parseInt(portValue);
            } catch (NumberFormatException e) {
                host.log(Level.WARNING, "The remote API port is not a valid number: %s", portValue);
            }
        } else {
            cd.customProperties.put(ContainerHostService.DOCKER_HOST_PORT_PROP_NAME,
                    String.valueOf(port));
        }

        Map<String, Object> content = context.content;

        replace(content, REMOTE_API_PORT, String.valueOf(port));

    }

    @SuppressWarnings({ "unchecked" })
    private void replace(Map<String, Object> content, Pattern pattern, String replacement) {
        content.forEach((k, v) -> {
            if (v instanceof String) {
                String val = (String) v;
                Matcher m = pattern.matcher(val);
                if (m.find()) {
                    String replaced = m.replaceAll(replacement);
                    content.put(k, replaced);
                }
            } else if (v instanceof Map) {
                replace((Map<String, Object>) v, pattern, replacement);
            } else if (v instanceof List) {
                replace((List<Object>) v, pattern, replacement);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void replace(List<Object> list, Pattern pattern, String replacement) {
        list.replaceAll(el -> {
            if (el instanceof String) {
                String val = (String) el;
                Matcher m = pattern.matcher(val);
                if (m.find()) {
                    return m.replaceAll(replacement);
                }
            } else if (el instanceof Map) {
                replace((Map<String, Object>) el, pattern, replacement);
            } else if (el instanceof List) {
                replace((List<Object>) el, pattern, replacement);
            }
            return el;
        });
    }

    private void processCaCertSign(EnhanceContext context,
            ComputeDescription cd,
            BiConsumer<ComputeDescription, Throwable> callback) {

        Operation.createGet(host, ManagementUriParts.AUTH_CREDENTIALS_CA_LINK)
                .setReferer(referer)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.SEVERE,
                                "Exception retrieving ca credentials. Error: %s",
                                Utils.toString(e));
                        callback.accept(cd, e);
                        return;
                    }
                    AuthCredentialsServiceState caCred = o
                            .getBody(AuthCredentialsServiceState.class);
                    addServerCerts(context, caCred);
                    cd.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                            ManagementUriParts.AUTH_CREDENTIALS_CLIENT_LINK);
                    callback.accept(cd, null);
                })
                .sendWith(host);
    }

    void addServerCerts(EnhanceContext context, AuthCredentialsServiceState cred) {

        KeyPair caKeyPair = CertificateUtil.createKeyPair(cred.privateKey);
        X509Certificate caCertificate = CertificateUtil.createCertificate(cred.publicKey);
        CertChainKeyPair signedForServer = CertificateUtil.generateSigned("computeServer",
                caCertificate, caKeyPair.getPrivate());
        try {
            Map<String, Object> content = context.content;
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) content.get("write_files");
            if (list == null) {
                list = new ArrayList<>();
            }

            list.add(new WriteFiles("/etc/docker/ca.pem", "0644", cred.publicKey));
            list.add(new WriteFiles("/etc/docker/server.pem", "0644",
                    CertificateUtil.toPEMformat(signedForServer.getCertificate())));
            list.add(new WriteFiles("/etc/docker/server-key.pem", "0600",
                    KeyUtil.toPEMFormat(signedForServer.getPrivateKey())));
            content.put("write_files", list);

        } catch (Exception e) {
            host.log(Level.WARNING,
                    () -> String.format("Error writing server certs in cloud-init file",
                            Utils.toString(e)));
        }
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
