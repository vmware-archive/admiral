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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import static com.vmware.admiral.compute.ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME;

import java.net.URI;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.AuthUtils;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class KubeConfigContentServiceTest extends ComputeBaseTest {

    private static final String KUBE_CONFIG_JSON = "{\"clusters\":[{\"name\":\"cluster2\",\"cluster\":{\"server\":\"https://mshipkovenski-test:8443\",\"certificate-authority-data\":\"cert\"}}],\"contexts\":[{\"name\":\"cluster2\",\"context\":{\"cluster\":\"cluster2\",\"user\":\"bdf17412-f7ee-4df0-bf74-161d8b663d3c\"}}],\"users\":[{\"name\":\"bdf17412-f7ee-4df0-bf74-161d8b663d3c\",\"user\":{\"token\":\"token\"}}],\"current-context\":\"cluster2\",\"apiVersion\":\"v1\",\"kind\":\"Config\"}";
    private static final String KUBE_CONFIG_YAML = "---\nclusters:\n- name: \"cluster2\"\n  cluster:\n    server: \"https://mshipkovenski-test:8443\"\n    certificate-authority-data: \"cert\"\ncontexts:\n- name: \"cluster2\"\n  context:\n    cluster: \"cluster2\"\n    user: \"bdf17412-f7ee-4df0-bf74-161d8b663d3c\"\nusers:\n- name: \"bdf17412-f7ee-4df0-bf74-161d8b663d3c\"\n  user:\n    token: \"token\"\ncurrent-context: \"cluster2\"\napiVersion: \"v1\"\nkind: \"Config\"\n";

    @Before
    public void setUp() throws Throwable {
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                KubeConfigContentService.class)), new KubeConfigContentService());

        waitForServiceAvailability(KubeConfigContentService.SELF_LINK);
        waitForServiceAvailability(AuthCredentialsService.FACTORY_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
    }

    @Test
    public void testGetKubeConfig() throws Throwable {
        String authCredentialsLink = createCredentials(true).documentSelfLink;
        String hostLink = createCompute(authCredentialsLink,
                ContainerHostType.KUBERNETES.name()).documentSelfLink;

        URI serviceUri = UriUtils.buildUri(host, KubeConfigContentService.SELF_LINK,
                UriUtils.buildUriQuery("hostLink", hostLink));

        verifyOperation(Operation.createGet(serviceUri), o -> {
            assertEquals("attachment; filename=\"kubeconfig\"", o.getResponseHeader("Content-Disposition"));
            assertEquals(KUBE_CONFIG_YAML, o.getBody(String.class));
        });
    }

    @Test
    public void testShouldFailWhenHostTypeNotKubernetes() throws Throwable {
        String authCredentialsLink = createCredentials(true).documentSelfLink;
        String hostLink = createCompute(authCredentialsLink,
                ContainerHostType.DOCKER.name()).documentSelfLink;
        URI serviceUri = UriUtils.buildUri(host, KubeConfigContentService.SELF_LINK,
                UriUtils.buildUriQuery("hostLink", hostLink));
        try {
            doOperation(null, serviceUri, true, Action.GET);
            fail("Operation should have failed: only k8s hosts are supported");
        } catch (Exception e) {
            assertEquals("host type must be KUBERNETES", e.getMessage());
        }
    }

    @Test
    public void testShouldFailWhenKubeConfigContentIsMissing() throws Throwable {
        String authCredentialsLink = createCredentials(false).documentSelfLink;
        String hostLink = createCompute(authCredentialsLink,
                ContainerHostType.KUBERNETES.name()).documentSelfLink;
        URI serviceUri = UriUtils.buildUri(host, KubeConfigContentService.SELF_LINK,
                UriUtils.buildUriQuery("hostLink", hostLink));
        try {
            doOperation(null, serviceUri, true, Action.GET);
            fail("Operation should have failed: kubeconfig not set");
        } catch (Exception e) {
            assertEquals("KubeConfig cannot be retrieved", e.getMessage());
        }
    }

    @Test
    public void testShouldFailWhenHostLinkParamIsMissing() throws Throwable {
        URI serviceUri = UriUtils.buildUri(host, KubeConfigContentService.SELF_LINK);
        try {
            doOperation(null, serviceUri, true, Action.GET);
            fail("Operation should have failed: hostLink query param not set");
        } catch (Exception e) {
            assertEquals("'hostLink' is required", e.getMessage());
        }
    }

    private AuthCredentialsServiceState createCredentials(boolean setKubeConfig) throws Throwable {
        AuthCredentialsServiceState credentials = new AuthCredentialsServiceState();
        credentials.type = AuthUtils.BEARER_TOKEN_AUTH_TYPE;
        credentials.publicKey = "token";
        credentials.customProperties = new HashMap<>();
        if (setKubeConfig) {
            credentials.customProperties.put("__kubeConfig", KUBE_CONFIG_JSON);
        }

        return doPost(credentials, AuthCredentialsService.FACTORY_LINK);
    }

    private ComputeState createCompute(String authCredentialsLink, String hostType) throws Throwable {
        ComputeState kubernetesHost = new ComputeState();
        kubernetesHost.address = "hostname";
        kubernetesHost.descriptionLink = "description";
        kubernetesHost.customProperties = new HashMap<>();
        kubernetesHost.customProperties.put(CONTAINER_HOST_TYPE_PROP_NAME,
                hostType);
        kubernetesHost.customProperties.put(HOST_AUTH_CREDENTIALS_PROP_NAME,
                authCredentialsLink);
        return doPost(kubernetesHost, ComputeService.FACTORY_LINK);
    }
}
