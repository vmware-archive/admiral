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

import static com.vmware.admiral.adapter.pks.PKSConstants.KUBE_CONFIG_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_PLAN_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_UUID_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_ENDPOINT_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_MASTER_HOST_FIELD;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_MASTER_NODES_IPS_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_MASTER_PORT_FIELD;
import static com.vmware.admiral.common.SwaggerDocumentation.BASE_PATH;
import static com.vmware.admiral.common.SwaggerDocumentation.LINE_BREAK;
import static com.vmware.admiral.common.SwaggerDocumentation.PARAM_TYPE_BODY;
import static com.vmware.admiral.common.SwaggerDocumentation.Tags.PKS_CLUSTER_CONFIG_TAG;
import static com.vmware.admiral.common.util.OperationUtil.PROJECT_ADMIRAL_HEADER;
import static com.vmware.admiral.compute.ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME;
import static com.vmware.admiral.compute.cluster.ClusterService.CLUSTER_NAME_CUSTOM_PROP;
import static com.vmware.admiral.compute.cluster.ClusterService.HOSTS_URI_PATH_SEGMENT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import javax.ws.rs.POST;
import javax.ws.rs.Path;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.pks.PKSConstants;
import com.vmware.admiral.adapter.pks.PKSOperationType;
import com.vmware.admiral.adapter.pks.entities.PKSCluster;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.AuthUtils;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.cluster.ClusterService;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.entities.config.KubeConfig;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

@Api(tags = {PKS_CLUSTER_CONFIG_TAG})
@Path(PKSClusterConfigService.SELF_LINK)
public class PKSClusterConfigService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.PKS_CLUSTERS_CONFIG;

    @ApiModel
    public static class AddClusterRequest extends MultiTenantDocument {

        public static final String FIELD_NAME_ENDPOINT_LINK = "endpointLink";
        public static final String FIELD_NAME_CLUSTER = "cluster";

        @ApiModelProperty(
                value = "The link of the existing PKS cluster.")
        public String existingClusterLink;
        @ApiModelProperty(
                value = "The link of the endpoint.",
                required = true)
        public String endpointLink;
        @ApiModelProperty(
                value = "The PKS cluster, returned from the PKS API.",
                required = true)
        public PKSCluster cluster;
        @ApiModelProperty(
                value = "Indicates whether to connect to the cluster by master IP or hostname.")
        public boolean preferMasterIP;

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

            String hostname = null;
            if (preferMasterIP) {
                if (cluster.masterIPs != null && cluster.masterIPs.length > 0) {
                    hostname = cluster.masterIPs[0];
                }
            } else {
                hostname = (String) cluster.parameters.get(PKS_MASTER_HOST_FIELD);
            }
            if (hostname == null) {
                return null;
            }

            String port = (String) cluster.parameters.get(PKS_MASTER_PORT_FIELD);

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
    @POST
    @Path(BASE_PATH)
    @ApiOperation(
            value = "Add a PKS host to either a new PKS cluster or to an existing one.",
            notes = "Adds a PKS host to an existing cluster when existing PKS cluster endpoint link is " +
                    "supplied in the body." + LINE_BREAK + LINE_BREAK + "Adds a new PKS host to a new cluster " +
                    "when the cluster information is supplied in the body")
    @ApiResponses({
            @ApiResponse(code = Operation.STATUS_CODE_OK, message = "PKS host successfully added.")})
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Add Cluster Request", value = "The type of add cluster request.", required = true,
                    paramType = PARAM_TYPE_BODY, dataType = "AddClusterRequest")})
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
        adapterRequest.customProperties.put(PKS_CLUSTER_NAME_PROP_NAME, clusterRequest.cluster.name);

        sendRequest(Operation.createPatch(getHost(), ManagementUriParts.ADAPTER_PKS)
                .setBodyNoCloning(adapterRequest)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logSevere("Adapter request for add PKS cluster failed. Error: %s",
                                Utils.toString(ex));
                        op.fail(ex);
                    } else {
                        KubeConfig kubeConfig = o.getBody(KubeConfig.class);
                        String token = KubernetesUtil.extractTokenFromKubeConfig(kubeConfig);
                        if (token == null) {
                            op.fail(new IllegalStateException("Missing token"));
                            return;
                        }

                        createCredentialsIfNotExist(op, token, kubeConfig, clusterRequest,
                                (credentialsLink) -> {
                                    addCluster(op, clusterRequest, credentialsLink);
                                });
                    }
                }));
    }

    private void addCluster(Operation op, AddClusterRequest clusterRequest,
            String credentialsLink) {

        ContainerHostSpec clusterSpec = createHostSpec(clusterRequest, credentialsLink);
        String link = (clusterRequest.existingClusterLink != null)
                ? UriUtils.buildUriPath(clusterRequest.existingClusterLink, HOSTS_URI_PATH_SEGMENT)
                : ClusterService.SELF_LINK;
        Operation.createPost(getHost(), link)
                .setBody(clusterSpec)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error creating/updating cluster state: %s", e.getMessage());
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
        kubernetesHost.customProperties.put(PKSConstants.PKS_CLUSTER_NAME_PROP_NAME,
                clusterRequest.cluster.name);
        kubernetesHost.customProperties.put(HOST_AUTH_CREDENTIALS_PROP_NAME,
                credentialsLink);
        kubernetesHost.customProperties.put(PKS_ENDPOINT_PROP_NAME,
                clusterRequest.endpointLink);
        kubernetesHost.customProperties.put(PKS_CLUSTER_UUID_PROP_NAME,
                clusterRequest.cluster.uuid);
        kubernetesHost.customProperties.put(PKS_CLUSTER_PLAN_NAME_PROP_NAME,
                clusterRequest.cluster.planName);
        if (clusterRequest.cluster.masterIPs != null) {
            kubernetesHost.customProperties.put(PKS_MASTER_NODES_IPS_PROP_NAME,
                    String.join(", ", clusterRequest.cluster.masterIPs));
        }

        ContainerHostSpec clusterSpec = new ContainerHostSpec();
        clusterSpec.hostState = kubernetesHost;
        clusterSpec.acceptCertificate = true;
        clusterSpec.acceptHostAddress =  DeploymentProfileConfig.getInstance().isTest();

        return clusterSpec;
    }

    private void createCredentialsIfNotExist(Operation op, String token, KubeConfig kubeConfig,
            AddClusterRequest clusterRequest, Consumer<String> consumer) {
        String credentialsLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                "pks-" + clusterRequest.cluster.uuid);

        Operation.createGet(this, credentialsLink)
                .setCompletion((o, e) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        createCredentials(op, credentialsLink, token, kubeConfig,
                                clusterRequest.tenantLinks, consumer);
                        return;
                    }
                    if (e != null) {
                        logSevere("Error getting credentials %s, reason: %s", credentialsLink,
                                e.getMessage());
                        op.fail(e);
                        return;
                    }
                    consumer.accept(credentialsLink);
                })
                .sendWith(this);
    }

    private void createCredentials(Operation op, String link, String token, KubeConfig kubeConfig,
            List<String> tenantLinks, Consumer<String> consumer) {
        AuthCredentialsServiceState credentials = new AuthCredentialsServiceState();
        credentials.documentSelfLink = link;
        credentials.privateKey = token;
        credentials.type = AuthCredentialsType.Bearer.toString();
        credentials.tenantLinks = tenantLinks;
        credentials.customProperties = new HashMap<>(4);
        credentials.customProperties.put(KUBE_CONFIG_PROP_NAME, Utils.toJson(kubeConfig));

        //Set the display name of the credential to the cluster name, if it exists
        if (CollectionUtils.isNotEmpty(kubeConfig.clusters) &&
                StringUtils.isNotEmpty(kubeConfig.clusters.get(0).name)) {
            credentials.customProperties.put(AuthUtils.AUTH_CREDENTIALS_NAME_PROP_NAME,
                    kubeConfig.clusters.get(0).name);
        }

        Operation.createPost(getHost(), AuthCredentialsService.FACTORY_LINK)
                .setBodyNoCloning(credentials)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error creating PKS credentials state: %s", e.getMessage());
                        op.fail(e);
                        return;
                    }
                    consumer.accept(link);
                })
                .sendWith(this);
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
