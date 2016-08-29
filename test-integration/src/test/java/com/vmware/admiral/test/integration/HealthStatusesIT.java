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

package com.vmware.admiral.test.integration;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getTestRequiredProp;
import static com.vmware.admiral.test.integration.data.IntegratonTestStateFactory.CONTAINER_IMAGE_DOWNLOAD_URL_FORMAT;

import java.net.URI;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.maintenance.ContainerStats;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.Utils;

@Ignore
public class HealthStatusesIT extends BaseProvisioningOnCoreOsIT {

    private String containerDescLink = null;
    HealthConfig healthConfig = null;

    @Before
    public void setUp() throws Exception {
        setupCoreOsHost(DockerAdapterType.API);
    }

    @After
    public void resetParams() {
        containerDescLink = null;
        healthConfig = null;
    }

    @Test
    public void testHealthStatusOverHttp() throws Exception {

        healthConfig = new HealthConfig();
        healthConfig.protocol = HealthConfig.RequestProtocol.HTTP;
        healthConfig.urlPath = "/";
        healthConfig.httpMethod = Action.GET;
        healthConfig.port = 80;
        healthConfig.healthyThreshold = 1;
        healthConfig.unhealthyThreshold = 2;

        containerDescLink = getResourceDescriptionLink(false, null);

        RequestBrokerState containerRequest = requestContainer(containerDescLink);

        validateAfterStart(containerDescLink, containerRequest);

        validateContainerRunning(containerRequest.resourceLinks.get(0));

        healthConfig.urlPath = "/any";
        containerDescLink = getResourceDescriptionLink(false, null);

        validateContainerDegraded(containerRequest.resourceLinks.get(0));
    }

    @Test
    public void testHealthStatusOverTcp() throws Exception {

        healthConfig = new HealthConfig();
        healthConfig.protocol = HealthConfig.RequestProtocol.TCP;
        healthConfig.port = 80;
        healthConfig.healthyThreshold = 1;
        healthConfig.unhealthyThreshold = 2;
        healthConfig.timeoutMillis = 1000;
        containerDescLink = getResourceDescriptionLink(false, null);

        RequestBrokerState containerRequest = requestContainer(containerDescLink);

        validateAfterStart(containerDescLink, containerRequest);

        validateContainerRunning(containerRequest.resourceLinks.get(0));

        healthConfig.port = 81;
        containerDescLink = getResourceDescriptionLink(false, null);

        validateContainerDegraded(containerRequest.resourceLinks.get(0));

        requestContainerDelete(containerRequest.resourceLinks, true);
    }

    @Test
    public void testHealthStatusOverCommand() throws Exception {

        healthConfig = new HealthConfig();
        healthConfig.protocol = HealthConfig.RequestProtocol.COMMAND;
        healthConfig.command = "ls -l";
        healthConfig.healthyThreshold = 1;
        healthConfig.unhealthyThreshold = 2;
        containerDescLink = getResourceDescriptionLink(false, null);

        RequestBrokerState containerRequest = requestContainer(containerDescLink);

        validateAfterStart(containerDescLink, containerRequest);

        validateContainerRunning(containerRequest.resourceLinks.get(0));

        healthConfig.command = "exit 1";
        containerDescLink = getResourceDescriptionLink(false, null);

        validateContainerDegraded(containerRequest.resourceLinks.get(0));
    }

    private void validateContainerRunning(String containerLink) throws Exception {
        waitForStateChange(containerLink + ServiceHost.SERVICE_URI_SUFFIX_STATS,
                (body) -> {
                    ServiceStats serviceStats = Utils.fromJson(body, ServiceStats.class);
                    ContainerStats stats = ContainerStats.transform(serviceStats);
                    if (stats.healthCheckSuccess == null) {
                        return false;
                    }
                    return stats.healthCheckSuccess;
                });

        waitForStateChange(containerLink, (body) -> {
            ContainerState container = Utils.fromJson(body, ContainerState.class);
            return container.status.equals(ContainerState.CONTAINER_RUNNING_STATUS);
        });
    }

    private void validateContainerDegraded(String containerLink) throws Exception {
        waitForStateChange(containerLink + ServiceHost.SERVICE_URI_SUFFIX_STATS,
                (body) -> {
                    ServiceStats serviceStats = Utils.fromJson(body, ServiceStats.class);
                    ContainerStats stats = ContainerStats.transform(serviceStats);
                    if (stats.healthCheckSuccess == null) {
                        return false;
                    }
                    return !stats.healthCheckSuccess;
                });

        waitForStateChange(containerLink, (body) -> {
            ContainerState container = Utils.fromJson(body, ContainerState.class);
            return container.status.equals(ContainerState.CONTAINER_DEGRADED_STATUS);
        });
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage, RegistryType registryType)
            throws Exception {
        ContainerDescription containerDesc = new ContainerDescription();

        if (downloadImage) {
            containerDesc.image = "registry.hub.docker.com/httpd";
            containerDesc.imageReference = buildDownloadURI("registry.hub.docker.com/httpd");
        } else {
            containerDesc.image = "httpd";
        }

        containerDesc.name = "test-health-status";
        containerDesc.portBindings = new PortBinding[] { PortBinding
                .fromDockerPortMapping(DockerPortMapping.fromString("8080::80")) };

        if (containerDescLink != null) {
            containerDesc.documentSelfLink = containerDescLink;
        }

        containerDesc.healthConfig = healthConfig;

        containerDesc = postDocument(ContainerDescriptionService.FACTORY_LINK, containerDesc);
        documentsForDeletion.add(containerDesc);

        return containerDesc.documentSelfLink;
    }

    private URI buildDownloadURI(String imageName) {
        // replace ':' with '-' in the imageName
        if (imageName.indexOf(':') >= 0) {
            imageName = imageName.replaceAll(":", "-");
        }

        String downloadImageUrl = getTestRequiredProp("docker.images.download.url");
        String result = String.format(
                CONTAINER_IMAGE_DOWNLOAD_URL_FORMAT, downloadImageUrl, imageName);

        return URI.create(result);
    }
}
