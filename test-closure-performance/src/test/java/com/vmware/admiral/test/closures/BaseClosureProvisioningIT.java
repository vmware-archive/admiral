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

package com.vmware.admiral.test.closures;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.AfterClass;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeComponentRegistry.ComponentMeta;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public abstract class BaseClosureProvisioningIT extends BasePerformanceSupportIT {

    private static final long DEFAULT_OPERATION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);
    public static final long DEFAULT_DOCUMENT_EXPIRATION_MICROS = Long.getLong(
            "dcp.document.test.expiration.time.seconds", TimeUnit.MINUTES.toMicros(30));

    public static final String REGISTRATION_DOCKER_ID = "test-docker-registration-id";
    public static final String AUTH_CREDENTIALS_ID = "test-credentials-id";
    public static final String DOCKER_COMPUTE_ID = "test-docker-host-compute-id";

    protected static ComputeState dockerHostCompute;

    protected static List<ComputeState> dockerHostsInCluster;
    private static AuthCredentialsServiceState dockerHostAuthCredentials;

    private static SslTrustCertificateState dockerHostSslTrust;
    private final Set<String> containersToDelete = new HashSet<>();

    private final Set<String> externalNetworksToDelete = new HashSet<>();

    @After
    public void provisioningTearDown() throws Exception {
        // remove remaining containers created in the current test run if they are still found
        Iterator<String> it = containersToDelete.iterator();
        while (it.hasNext()) {
            String containerLink = it.next();
            ContainerState containerState = getDocument(containerLink, ContainerState.class);
            if (containerState == null) {
                logger.warning(String.format("Unable to find container %s", containerLink));
                continue;
            }

            try {
                logger.info("---------- Clean up: Request Delete the container instance. --------");
                requestContainerDelete(Collections.singleton(containerLink), false);
            } catch (Throwable t) {
                logger.warning(String.format("Unable to remove container %s: %s", containerLink,
                        t.getMessage()));
            }
        }

        // remove the external networks, if any
        it = externalNetworksToDelete.iterator();
        while (it.hasNext()) {
            String networkLink = it.next();

            ContainerNetworkState network = getDocument(networkLink, ContainerNetworkState.class);
            assertNotNull(String.format("Unable to find network %s", networkLink), network);
            assertTrue(network.connectedContainersCount == 0);

            try {
                logger.info("---------- Clean up: Request Delete the network instance. --------");
                requestExternalNetworkDelete(networkLink);
            } catch (Throwable t) {
                logger.warning(String.format("Unable to remove network %s: %s", networkLink,
                        t.getMessage()));
            }
        }

        if (dockerHostsInCluster != null) {
            for (ComputeState dockerHost : dockerHostsInCluster) {
                removeHost(dockerHost);
            }
        }
    }

    @AfterClass
    public static void afterClassTearDown() throws Exception {
        // remove the host
        if (dockerHostCompute != null) {
            removeHost(dockerHostCompute);
        }
    }

    protected static void setupCoreOsHost(DockerAdapterType adapterType, boolean setupOnCluster)
            throws Exception {
        logger.info("********************************************************************");
        logger.info("----------  Setup: Add CoreOS VM as DockerHost ComputeState --------");
        logger.info("********************************************************************");
        logger.info("---------- 1. Create a Docker Host Container Description. --------");

        logger.info(
                "---------- 2. Setup auth credentials for the CoreOS VM (Container Host). --------");
        dockerHostAuthCredentials = createAuthCredentials(true);
        dockerHostAuthCredentials.type = AuthCredentialsType.PublicKey.name();

        switch (adapterType) {
        case API:
            dockerHostAuthCredentials.privateKey = getFileContent(
                    TestPropertiesUtil.getTestRequiredProp("docker.client.key.file"));
            dockerHostAuthCredentials.publicKey = getFileContent(
                    TestPropertiesUtil.getTestRequiredProp("docker.client.cert.file"));
            break;

        default:
            throw new IllegalArgumentException("Unexpected adapter type: " + adapterType);
        }

        dockerHostAuthCredentials = postDocument(AuthCredentialsService.FACTORY_LINK,
                dockerHostAuthCredentials, TestDocumentLifeCycle.FOR_DELETE_AFTER_CLASS);

        assertNotNull("Failed to create host credentials", dockerHostAuthCredentials);

        String hostPort = TestPropertiesUtil
                .getTestRequiredProp("docker.host.port." + adapterType.name());
        String credLink = dockerHostAuthCredentials.documentSelfLink;

        if (!setupOnCluster) {
            logger.info("---------- 3. Create Docker Host ComputeState for CoreOS VM. --------");
            dockerHostCompute = createDockerHost(
                    TestPropertiesUtil.getTestRequiredProp("docker.host.address"),
                    hostPort, credLink, adapterType, null);
        } else {
            dockerHostsInCluster = new ArrayList<>();
            logger.info(
                    "---------- 3. Create Docker Hosts ComputeState Node 1 and Node 2 of cluster for CoreOS VM. --------");
            String node1Addresses = TestPropertiesUtil
                    .getTestRequiredProp("docker.host.performance.addresses");
            String[] nodes = node1Addresses.split(",");
            dockerHostsInCluster.add(createDockerHost(nodes[0],
                    hostPort, credLink, adapterType, UriUtilsExtended.extractHost(nodes[0])));

            //            String node2Address = getTestRequiredProp("docker.host.cluster.node2.address");
            dockerHostsInCluster.add(createDockerHost(nodes[1],
                    hostPort, credLink, adapterType, UriUtilsExtended.extractHost(nodes[1])));
        }

        logger.info("---------- 4. Add the Docker Host SSL Trust Certificate. --------");
        dockerHostSslTrust = createSslTrustCertificateState(
                TestPropertiesUtil.getTestRequiredProp("docker.host.ssl.trust.file"),
                REGISTRATION_DOCKER_ID);

        postDocument(SslTrustCertificateService.FACTORY_LINK, dockerHostSslTrust,
                TestDocumentLifeCycle.FOR_DELETE_AFTER_CLASS);
    }

    protected void requestContainerDelete(Set<String> resourceLinks, boolean verifyDelete)
            throws Exception {

        RequestBrokerState day2DeleteRequest = new RequestBrokerState();
        String resourceLink = resourceLinks.iterator().next();
        if (resourceLink.startsWith(CompositeComponentFactoryService.SELF_LINK)) {
            day2DeleteRequest.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        } else {
            ComponentMeta metaByStateLink = CompositeComponentRegistry
                    .metaByStateLink(resourceLink);
            day2DeleteRequest.resourceType = metaByStateLink.resourceType;
        }
        day2DeleteRequest.operation = ContainerOperationType.DELETE.id;
        day2DeleteRequest.resourceLinks = resourceLinks;
        day2DeleteRequest = postDocument(RequestBrokerFactoryService.SELF_LINK, day2DeleteRequest);

        waitForTaskToComplete(day2DeleteRequest.documentSelfLink);

        if (!verifyDelete) {
            return;
        }

        for (String containerLink : resourceLinks) {
            ContainerState conState = getDocument(containerLink, ContainerState.class);
            assertNull(conState);
            String computeStateLink = UriUtils
                    .buildUriPath(ComputeService.FACTORY_LINK, extractId(containerLink));
            ComputeState computeState = getDocument(computeStateLink, ComputeState.class);
            assertNull(computeState);
            containersToDelete.remove(containerLink);
        }
    }

    protected void requestExternalNetworkDelete(String resourceLink) throws Exception {

        RequestBrokerState day2DeleteRequest = new RequestBrokerState();
        ComponentMeta metaByStateLink = CompositeComponentRegistry.metaByStateLink(resourceLink);

        day2DeleteRequest.resourceType = metaByStateLink.resourceType;
        day2DeleteRequest.operation = NetworkOperationType.DELETE.id;
        day2DeleteRequest.resourceLinks = new HashSet<>(Arrays.asList(resourceLink));
        day2DeleteRequest = postDocument(RequestBrokerFactoryService.SELF_LINK, day2DeleteRequest);

        waitForTaskToComplete(day2DeleteRequest.documentSelfLink);
    }

    protected Operation sendRequest(ServiceClient serviceClient, Operation op)
            throws InterruptedException, ExecutionException,
            TimeoutException {
        return sendRequest(serviceClient, op, DEFAULT_OPERATION_TIMEOUT_MILLIS);
    }

    private Operation sendRequest(ServiceClient serviceClient, Operation op, long timeoutMilis)
            throws InterruptedException, ExecutionException,
            TimeoutException {

        CompletableFuture<Operation> c = new CompletableFuture<>();
        serviceClient.send(op
                .setReferer(URI.create("/"))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        c.completeExceptionally(ex);

                    } else {
                        c.complete(o);
                    }
                }));

        return c.get(timeoutMilis, TimeUnit.MILLISECONDS);
    }

    private static ComputeState createDockerHost(String address, String port, String credLink,
            DockerAdapterType adapterType, String id) throws Exception {
        ComputeState compute = createDockerComputeHost();
        if (id != null) {
            compute.id = id;
            compute.documentSelfLink = id;
        }
        compute.address = address;
        compute.customProperties.put(ContainerHostService.DOCKER_HOST_PORT_PROP_NAME, port);
        //        compute.customProperties.put(ContainerHostService.DOCKER_HOST_SCHEME_PROP_NAME, "http");

        compute.customProperties.put(
                ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                adapterType.name());

        // link credentials to host
        compute.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME, credLink);
        return addHost(compute);
    }

    public static AuthCredentialsServiceState createAuthCredentials(boolean uniqueSelfLink) {
        AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
        authCredentials.documentSelfLink = AUTH_CREDENTIALS_ID;
        if (uniqueSelfLink) {
            authCredentials.documentSelfLink += "-" + UUID.randomUUID();
        }
        authCredentials.type = AuthCredentialsType.PublicKey.name();
        authCredentials.userEmail = "core";
        authCredentials.privateKey = getFileContent("certs/client-key.pem");
        return authCredentials;
    }

    // TODO: This method seems pretty similar to FileUtil.getResourceAsString...
    public static String getFileContent(String fileName) {
        try (InputStream is = BaseClosureProvisioningIT.class.getClassLoader()
                .getResourceAsStream(fileName)) {
            if (is != null) {
                return readFile(new InputStreamReader(is));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try (FileReader fileReader = new FileReader(fileName)) {
            return readFile(fileReader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public static SslTrustCertificateState createSslTrustCertificateState(String pemFileName,
            String id) {

        SslTrustCertificateState sslTrustState = new SslTrustCertificateState();
        sslTrustState.documentSelfLink = id;
        sslTrustState.certificate = getFileContent(pemFileName);
        return sslTrustState;
    }

    private static String readFile(Reader reader) throws IOException {
        BufferedReader br = new BufferedReader(reader);
        StringBuilder sb = new StringBuilder();
        String read = br.readLine();
        String newLine = System.getProperty("line.separator");
        while (read != null) {
            sb.append(read);
            sb.append(newLine);
            read = br.readLine();
        }
        return sb.toString();
    }

    public static ComputeState createDockerComputeHost() {
        ComputeState cs = new ComputeState();
        cs.id = DOCKER_COMPUTE_ID;
        cs.documentSelfLink = cs.id;
        cs.primaryMAC = UUID.randomUUID().toString();
        cs.address = "ssh://somehost:22"; // this will be used for ssh to access the host
        cs.powerState = ComputeService.PowerState.ON;
        cs.resourcePoolLink = GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK;
        cs.adapterManagementReference = URI.create("http://localhost:8081"); // not real reference
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.name());

        addDefaultDocumentTimeout(cs);
        return cs;
    }

    public static void addDefaultDocumentTimeout(ServiceDocument serviceDocument) {
        serviceDocument.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                + DEFAULT_DOCUMENT_EXPIRATION_MICROS;
    }

}
