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

import static com.vmware.admiral.adapter.pks.PKSConstants.CLUSTER_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.KUBERNETES_MASTER_HOST_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.KUBERNETES_MASTER_PORT_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.KUBE_CONFIG_PROP_NAME;
import static com.vmware.admiral.common.util.OperationUtil.PROJECT_ADMIRAL_HEADER;
import static com.vmware.admiral.compute.ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.PKS_CLUSTER_PLAN_NAME_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.PKS_CLUSTER_UUID_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.PKS_ENDPOINT_PROP_NAME;
import static com.vmware.admiral.compute.cluster.ClusterService.CLUSTER_NAME_CUSTOM_PROP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.pks.PKSOperationType;
import com.vmware.admiral.adapter.pks.entities.KubeConfig;
import com.vmware.admiral.adapter.pks.entities.PKSCluster;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.AuthUtils;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.cluster.ClusterService;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class PKSClusterConfigService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.PKS_CLUSTERS_CONFIG;

    public static class AddClusterRequest extends MultiTenantDocument {

        public static final String FIELD_NAME_ENDPOINT_LINK = "endpointLink";
        public static final String FIELD_NAME_CLUSTER = "cluster";

        public String endpointLink;
        public PKSCluster cluster;

        public void validate() {
            AssertUtil.assertNotNullOrEmpty(endpointLink, FIELD_NAME_ENDPOINT_LINK);
            AssertUtil.assertNotNull(cluster, FIELD_NAME_CLUSTER);

            AssertUtil.assertNotNull(cluster.uuid, "cluster.uuid");
            AssertUtil.assertNotNull(cluster.name, "cluster.name");
            AssertUtil.assertNotNull(getExternalAddress(), "external-hostname");
            AssertUtil.assertNotEmpty(tenantLinks, FIELD_NAME_TENANT_LINKS);
        }

        public String getExternalAddress() {
            if (cluster.parameters == null) {
                return null;
            }

            String hostname = (String) cluster.parameters.get(KUBERNETES_MASTER_HOST_PROP_NAME);
            if (hostname == null) {
                return null;
            }

            String port = (String) cluster.parameters.get(KUBERNETES_MASTER_PORT_PROP_NAME);

            return UriUtils.buildUri("https", hostname,
                    port != null ? Integer.parseInt(port) : -1, null, null).toString();
        }
    }

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() != Action.POST) {
            Operation.failActionNotSupported(op);
            return;
        }

        handlePost(op);
    }

    @Override
    public void handlePost(Operation op) {
        try {
            AddClusterRequest request = op.getBody(AddClusterRequest.class);
            setProjectLinkAsTenantLink(op, request);
            request.validate();

            handleAddRequest(op, request);
        } catch (Exception x) {
            logSevere(x);
            op.fail(x);
        }
    }

    private void handleAddRequest(Operation op, AddClusterRequest clusterRequest) {
        AdapterRequest adapterRequest = new AdapterRequest();
        adapterRequest.operationTypeId = PKSOperationType.CREATE_USER.id;
        adapterRequest.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        adapterRequest.resourceReference = UriUtils.buildUri(getHost(),
                clusterRequest.endpointLink);
        adapterRequest.customProperties = new HashMap<>();
        adapterRequest.customProperties.put(CLUSTER_NAME_PROP_NAME, clusterRequest.cluster.name);

        sendRequest(Operation.createPatch(getHost(), ManagementUriParts.ADAPTER_PKS)
                .setBodyNoCloning(adapterRequest)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logSevere("Adapter request for add PKS cluster failed. Error: %s",
                                Utils.toString(ex));
                        op.fail(ex);
                    } else {
                        KubeConfig kubeConfig = o.getBody(KubeConfig.class);
                        if (kubeConfig.users == null
                                || kubeConfig.users.isEmpty()
                                || kubeConfig.users.get(0).user == null
                                || kubeConfig.users.get(0).user.token == null) {
                            op.fail(new IllegalStateException("Missing token"));
                            return;
                        }

                        createCredentials(op, kubeConfig.users.get(0).user.token,
                                kubeConfig, clusterRequest.tenantLinks,
                                (credentialsLink) -> {
                                    addCluster(op, clusterRequest, credentialsLink);
                                });
                    }
                }));
    }

    private void addCluster(Operation op, AddClusterRequest clusterRequest,
            String credentialsLink) {

        ContainerHostSpec clusterSpec = createHostSpec(clusterRequest, credentialsLink);

        Operation.createPost(getHost(), ClusterService.SELF_LINK)
                .setBody(clusterSpec)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error creating cluster state: %s", Utils.toString(e));
                        op.fail(e);
                        return;
                    }

                    op.setBodyNoCloning(o.getBodyRaw()).complete();
                }).sendWith(this);
    }

    private ContainerHostSpec createHostSpec(AddClusterRequest clusterRequest,
            String credentialsLink) {

        ComputeState kubernetesHost = new ComputeState();
        kubernetesHost.address = clusterRequest.getExternalAddress();
        kubernetesHost.tenantLinks = clusterRequest.tenantLinks;
        kubernetesHost.customProperties = new HashMap<>();
        kubernetesHost.customProperties.put(CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.KUBERNETES.name());
        kubernetesHost.customProperties.put(HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.name());
        kubernetesHost.customProperties.put(CLUSTER_NAME_CUSTOM_PROP,
                clusterRequest.cluster.name);
        kubernetesHost.customProperties.put(HOST_AUTH_CREDENTIALS_PROP_NAME,
                credentialsLink);
        kubernetesHost.customProperties.put(PKS_ENDPOINT_PROP_NAME,
                clusterRequest.endpointLink);
        kubernetesHost.customProperties.put(PKS_CLUSTER_UUID_PROP_NAME,
                clusterRequest.cluster.uuid);
        kubernetesHost.customProperties.put(PKS_CLUSTER_PLAN_NAME_PROP_NAME,
                clusterRequest.cluster.planName);

        ContainerHostSpec clusterSpec = new ContainerHostSpec();
        clusterSpec.hostState = kubernetesHost;
        clusterSpec.acceptCertificate = true;

        return clusterSpec;
    }

    private void createCredentials(Operation op, String token, KubeConfig kubeConfig,
            List<String> tenantLinks, Consumer<String> consumer) {

        AuthCredentialsServiceState credentials = new AuthCredentialsServiceState();
        credentials.privateKey = token;
        credentials.type = AuthUtils.BEARER_TOKEN_AUTH_TYPE;
        credentials.tenantLinks = tenantLinks;
        credentials.customProperties = new HashMap<>();
        credentials.customProperties.put(KUBE_CONFIG_PROP_NAME, Utils.toJson(kubeConfig));

        Operation.createPost(getHost(), AuthCredentialsService.FACTORY_LINK)
                .setBody(credentials)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error creating PKS credentials state: %s", Utils.toString(e));
                        op.fail(e);
                        return;
                    }
                    consumer.accept(o.getBody(AuthCredentialsServiceState.class).documentSelfLink);
                }).sendWith(this);
    }

    private void setProjectLinkAsTenantLink(Operation op, AddClusterRequest request) {
        String projectLink = op.getRequestHeader(PROJECT_ADMIRAL_HEADER);
        if (projectLink != null) {
            if (request.tenantLinks == null) {
                request.tenantLinks = new ArrayList<>();
            }
            request.tenantLinks.add(projectLink);
        }
    }

}
