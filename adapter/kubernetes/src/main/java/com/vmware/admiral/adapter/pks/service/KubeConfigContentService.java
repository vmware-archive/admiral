/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.pks.service;

import static com.vmware.admiral.common.util.UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML;
import static com.vmware.admiral.compute.ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME;

import java.io.IOException;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.vmware.admiral.adapter.pks.PKSConstants;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.AuthUtils;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.entities.config.KubeConfig;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Service that produces kubeconfig files for user authorization against kubernetes hosts.
 */
public class KubeConfigContentService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.PKS_KUBE_CONFIG_CONTENT;

    public static final String KUBERNETES_HOST_LINK_PARAM_NAME = "hostLink";

    private static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
    private static final String CONTENT_DISPOSITION_ATTACHMENT = "attachment; filename=\"kubeconfig\"";

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() != Action.GET) {
            Operation.failActionNotSupported(op);
            return;
        }

        handleGet(op);
    }

    @Override
    public void handleGet(Operation op) {
        try {
            Map<String, String> queryParams = UriUtils.parseUriQueryParams(op.getUri());

            String hostLink = queryParams.get(KUBERNETES_HOST_LINK_PARAM_NAME);
            AssertUtil.assertNotNullOrEmpty(hostLink, KUBERNETES_HOST_LINK_PARAM_NAME);

            getHostAndCredentials(op, hostLink,
                    (compute, credentials) -> constructKubeConfig(op, compute, credentials));
        } catch (Exception x) {
            logSevere(x);
            op.fail(x);
        }
    }

    private void constructKubeConfig(Operation op, ComputeState kubernetesHost,
            AuthCredentialsServiceState credentials) {

        String kubeConfig = null;

        if (KubernetesUtil.isPKSManagedHost(kubernetesHost)) {
            if (credentials.customProperties == null
                    || !credentials.customProperties.containsKey(PKSConstants.KUBE_CONFIG_PROP_NAME)) {
                op.fail(new IllegalStateException("KubeConfig cannot be retrieved"));
                return;
            }

            kubeConfig = credentials.customProperties.get(PKSConstants.KUBE_CONFIG_PROP_NAME);
        } else if (AuthUtils.BEARER_TOKEN_AUTH_TYPE.equals(credentials.type)) {
            KubeConfig config = KubernetesUtil.constructKubeConfig(kubernetesHost.address,
                    EncryptionUtils.decrypt(credentials.privateKey));
            kubeConfig = Utils.toJson(config);
        } else if (AuthCredentialsType.PublicKey.toString().equals(credentials.type)) {
            KubeConfig config = KubernetesUtil.constructKubeConfig(kubernetesHost.address,
                    credentials.publicKey, EncryptionUtils.decrypt(credentials.privateKey));
            kubeConfig = Utils.toJson(config);
        } else {
            op.fail(new Exception("Host authentication type not supported!"));
            return;
        }

        try {

            String kubeConfigYaml = serializeContent(kubeConfig);
            op.setBody(kubeConfigYaml);
            op.setContentType(MEDIA_TYPE_APPLICATION_YAML);
            op.addResponseHeader(CONTENT_DISPOSITION_HEADER, CONTENT_DISPOSITION_ATTACHMENT);

            op.complete();
        } catch (Exception e) {
            op.fail(e);
        }
    }

    private String serializeContent(String kubeConfig) throws IOException {
        return YamlMapper.fromJsonToYaml(kubeConfig);
    }

    private void getHostAndCredentials(Operation op, String hostLink,
            BiConsumer<ComputeState, AuthCredentialsServiceState> consumer) {

        getCompute(op, hostLink, (host) -> {
            if (!ContainerHostUtil.isKubernetesHost(host)) {
                op.fail(new IllegalArgumentException("host type must be KUBERNETES"));
                return;
            }

            String credentialsLink = host.customProperties.get(HOST_AUTH_CREDENTIALS_PROP_NAME);

            if (credentialsLink == null) {
                op.fail(new IllegalStateException("Missing credentials link"));
                return;
            }

            Operation.createGet(this, credentialsLink).setCompletion((o, e) -> {
                if (e != null) {
                    op.fail(e);
                    return;
                }

                AuthCredentialsServiceState credentials = o
                        .getBody(AuthCredentialsServiceState.class);

                consumer.accept(host, credentials);
            }).sendWith(this);
        });
    }

    private void getCompute(Operation op, String hostLink,
            Consumer<ComputeState> consumer) {

        Operation.createGet(this, hostLink).setCompletion((o, e) -> {
            if (e != null) {
                op.fail(e);
                return;
            }

            consumer.accept(o.getBody(ComputeState.class));
        }).sendWith(this);
    }
}
