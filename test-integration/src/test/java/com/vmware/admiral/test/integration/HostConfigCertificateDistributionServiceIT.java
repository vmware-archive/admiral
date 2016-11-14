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

import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService.ContainerHostDataCollectionState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Utils;

public class HostConfigCertificateDistributionServiceIT extends
        BaseCertificateDistributionServiceIT {

    @Before
    public void setUp() throws Exception {
        logger.info("---------- Adding host, to remove old certificate directory if any --------");
        setupCoreOsHost(ContainerHostService.DockerAdapterType.API);
        logger.info("---------- Removing old certificate directory --------");
        removeCertificateDirectoryOnCoreOsHost(dockerHostCompute.documentSelfLink,
                registryHostAndPort);
        removeHost(dockerHostCompute);

        logger.info("---------- Configure registries on a clean host --------");
        configureRegistries(registryAddress, null);
    }

    @Test
    public void testUploadRegistryCertificateOnDockerHostConfig() throws Exception {
        // Step 1: verify that certificate is added after host is added
        logger.info("---------- Adding host --------");
        setupCoreOsHost(ContainerHostService.DockerAdapterType.API);

        String hostId = Service.getId(dockerHostCompute.documentSelfLink);
        String systemContainerLink = SystemContainerDescriptions.getSystemContainerSelfLink(
                SystemContainerDescriptions.AGENT_CONTAINER_NAME, hostId);

        logger.info("---------- Waiting until certificate exists --------");
        boolean exists = waitUntilRegistryCertificateExists(dockerHostCompute.documentSelfLink,
                registryHostAndPort);
        assertTrue("Cert does not exist.", exists);
        logger.info("Certificate exists");

        // Step 2: verify that certificate is added after data collection starts system container
        logger.info("---------- Removing old certificate directory --------");
        removeCertificateDirectoryOnCoreOsHost(dockerHostCompute.documentSelfLink,
                registryHostAndPort);
        logger.info("---------- Stoping system container --------");
        stopContainer(systemContainerLink);
        requestDataCollection();

        logger.info("---------- Waiting until certificate exists --------");
        exists = waitUntilRegistryCertificateExists(dockerHostCompute.documentSelfLink,
                registryHostAndPort);
        assertTrue("Cert does not exist.", exists);
        logger.info("Certificate exists");

        // Step 3: verify that certificate is added after data collection creates the system container
        logger.info("---------- Removing old certificate directory --------");
        removeCertificateDirectoryOnCoreOsHost(dockerHostCompute.documentSelfLink,
                registryHostAndPort);
        logger.info("---------- Deleting system container --------");
        delete(systemContainerLink);

        AdapterRequest adapterRequest = new AdapterRequest();
        adapterRequest.resourceReference = URI.create(getBaseUrl() + systemContainerLink);
        adapterRequest.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        adapterRequest.operationTypeId = ContainerOperationType.DELETE.id;
        sendRequest(HttpMethod.PATCH, ManagementUriParts.ADAPTER_DOCKER,
                Utils.toJson(adapterRequest));

        requestDataCollection();

        logger.info("---------- Waiting until system container is back on --------");
        waitForStateChange(systemContainerLink, (body) -> {
            if (body == null) {
                logger.warning("Body returned for %s is Null.", systemContainerLink);
                return false;
            }
            ContainerState containerState = Utils.fromJson(body, ContainerState.class);
            return containerState.powerState == PowerState.RUNNING;
        });

        logger.info("---------- Waiting until certificate exists --------");
        exists = waitUntilRegistryCertificateExists(dockerHostCompute.documentSelfLink,
                registryHostAndPort);
        assertTrue("Cert does not exist.", exists);
        logger.info("Certificate exists");
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return null;
    }

    private void requestDataCollection() throws Exception {
        URI uri = URI.create(getBaseUrl() + buildServiceUri(
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK));
        ContainerHostDataCollectionState state = new ContainerHostDataCollectionState();
        state.computeContainerHostLinks = Collections.singleton(dockerHostCompute.documentSelfLink);

        SimpleHttpsClient.execute(
                SimpleHttpsClient.HttpMethod.PATCH, uri.toString(),
                Utils.toJson(state));
    }
}
