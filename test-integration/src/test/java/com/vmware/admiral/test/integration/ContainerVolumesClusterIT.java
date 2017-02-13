/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Collections;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpResponse;
import com.vmware.xenon.common.Operation;

public class ContainerVolumesClusterIT extends BaseProvisioningOnCoreOsIT {

    private static final String VOLUMES_PATH = "volumes.html";
    private static final String VOLUMES_CONTENT = "<h2>Admiral volumes E2E test<\\/h2>";
    private static final int STATUS_CODE_WAIT_POLLING_RETRY_COUNT = 30;

    private static final String TEMPLATE_FILE = "ngingx_cluster_volumes.yaml";

    private String compositeDescriptionLink;

    @BeforeClass
    public static void beforeClass() {
        serviceClient = ServiceClientFactory.createServiceClient(null);
    }

    @AfterClass
    public static void afterClass() {
        serviceClient.stop();
    }

    @Before
    public void setUp() throws Exception {
        compositeDescriptionLink = importTemplate(serviceClient, TEMPLATE_FILE);
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage, RegistryType registryType)
            throws Exception {

        return compositeDescriptionLink;
    }

    @Test
    public void testProvision() throws Exception {
        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API);
    }

    @Override
    protected void validateAfterStart(String resourceDescLink, RequestBrokerState request)
            throws Exception {
        AssertUtil.assertNotNull(compositeDescriptionLink, "'compositeDescriptionLink'");
        CompositeComponent cc = getDocument(request.resourceLinks.iterator().next(),
                CompositeComponent.class);

        String containerLink1 = getResourceContaining(cc.componentLinks, "nginx-1");
        String containerLink2 = getResourceContaining(cc.componentLinks, "nginx-2");
        ContainerState cont1 = getDocument(containerLink1, ContainerState.class);
        ContainerState cont2 = getDocument(containerLink2, ContainerState.class);

        // Add a file volumes.html with custom contents (use sed for that)
        runContainerCommand(cont1.documentSelfLink,
                new String[] { "cp", "/website_files/index.html", "/website_files/volumes.html" });
        // remove some useless lines
        runContainerCommand(cont1.documentSelfLink,
                new String[] { "sed", "-i", "-e", "2,6d", "/website_files/volumes.html" });
        // add volumes content to volumes.html
        runContainerCommand(cont1.documentSelfLink, new String[] { "sed", "-i",
                "2i" + VOLUMES_CONTENT, "/website_files/volumes.html" });

        assertEquals("Unexpected number of ports", 1, cont1.ports.size());
        String cont1Port = cont1.ports.get(0).hostPort;

        assertEquals("Unexpected number of ports", 1, cont2.ports.size());
        String cont2Port = cont2.ports.get(0).hostPort;

        String nginx1Host = getHostnameOfComputeHost(cont1.parentLink);
        connectToNginx(nginx1Host, cont1Port, 1);

        String nginx2Host = getHostnameOfComputeHost(cont2.parentLink);
        connectToNginx(nginx2Host, cont2Port, 2);
    }

    private void connectToNginx(String nginxHost, String nginxPort, int count) throws Exception {
        URI uri = URI.create(String.format("http://%s:%s/%s", nginxHost, nginxPort, VOLUMES_PATH));
        logger.info(
                "------------- 4.%d. connecting to nginx volumes page %s. -------------",
                count, uri);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK,
                STATUS_CODE_WAIT_POLLING_RETRY_COUNT);
        HttpResponse httpResponse = SimpleHttpsClient.execute(HttpMethod.GET,
                uri.toString(), null, Collections.emptyMap(), getUnsecuredSSLSocketFactory());
        String body = httpResponse.responseBody;
        String expectedContent = VOLUMES_CONTENT.replaceAll("\\\\", "");
        // verify volumes.html has the expected content
        assertTrue(body.contains(expectedContent));
    }
}
