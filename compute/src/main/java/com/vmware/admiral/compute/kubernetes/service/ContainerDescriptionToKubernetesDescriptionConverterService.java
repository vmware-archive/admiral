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

package com.vmware.admiral.compute.kubernetes.service;

import java.io.IOException;
import java.util.logging.Level;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.content.kubernetes.KubernetesConverter;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.entities.deployments.Deployment;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class ContainerDescriptionToKubernetesDescriptionConverterService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.CONTAINER_DESCRIPTION_TO_KUBERNETES_DESCRIPTION_CONVERTER;

    @Override
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            post.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ContainerDescription containerDescription = post.getBody(ContainerDescription.class);

        Deployment deployment = KubernetesConverter
                .fromContainerDescriptionToDeployment(containerDescription, containerDescription.name);
        try {
            String serializeKubernetesEntity = KubernetesUtil.serializeKubernetesEntity(deployment);

            log(Level.INFO, "serializeKubernetesEntity: [%s]", serializeKubernetesEntity);

            KubernetesDescription kubernetesDescription = new KubernetesDescription();
            kubernetesDescription.kubernetesEntity = serializeKubernetesEntity;
            kubernetesDescription.type = KubernetesUtil.DEPLOYMENT_TYPE;

            sendRequest(Operation
                    .createPost(UriUtils.buildUri(getHost(), KubernetesDescriptionService.FACTORY_LINK))
                    .setReferer(getHost().getUri())
                    .setBody(kubernetesDescription)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            logSevere("Failed to create kubernetes description: [%s]", e.getMessage());
                            o.fail(e);
                            return;
                        }

                        KubernetesDescription createdKubDesc = o.getBody(KubernetesDescription.class);
                        post.setBody(createdKubDesc);
                        post.complete();
                    }));
        } catch (IOException e) {
            logSevere("Failed to serialize kubernetes entity: [%s]", e);
            post.fail(e);
        }
    }
}
