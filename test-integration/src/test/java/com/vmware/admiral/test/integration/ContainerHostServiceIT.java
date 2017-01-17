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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getSystemOrTestProp;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.HostPortProfileService;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerHostServiceIT extends RequestBaseTest {
    private static final String VALID_DOCKER_HOST_NODE1_ADDRESS = String.format("%s:%s",
            getSystemOrTestProp("docker.host.cluster.node1.address"),
            getSystemOrTestProp("docker.host.port.API"));
    private static final String VALID_DOCKER_HOST_NODE2_ADDRESS = String.format("%s:%s",
            getSystemOrTestProp("docker.host.cluster.node2.address"),
            getSystemOrTestProp("docker.host.port.API"));

    private ComputeState computeState;
    private ComputeState vicHostState;
    private ContainerHostSpec containerHostSpec;
    private ContainerHostSpec vicHostSpec;
    private URI containerHostUri;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ComputeDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ContainerHostService.SELF_LINK);
        waitForServiceAvailability(HostPortProfileService.FACTORY_LINK);
        waitForServiceAvailability(SslTrustCertificateService.FACTORY_LINK);
        ServerX509TrustManager.init(host);

        computeState = createDockerHostState();
        containerHostSpec = new ContainerHostSpec();
        containerHostSpec.hostState = computeState;

        vicHostState = createVicHostState();
        vicHostSpec = new ContainerHostSpec();
        vicHostSpec.hostState = vicHostState;

        containerHostUri = UriUtils.buildUri(host, ContainerHostService.SELF_LINK);
        DeploymentProfileConfig.getInstance().setTest(false);
    }

    @After
    public void tearDown() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);
    }

    @Test
    public void testValidateShouldFailWithInvalidUri() throws Throwable {
        computeState.address = null;
        Operation op = Operation.createPut(getContainerHostValidateUri())
                .setBody(containerHostSpec)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        try {
                            verifyStatusCode(o.getStatusCode(), Operation.STATUS_CODE_BAD_REQUEST);
                            host.completeIteration();
                        } catch (IllegalStateException ex) {
                            host.log("Expected validation exception but got %s", Utils.toString(e));
                            host.failIteration(ex);
                        }
                    } else {
                        host.failIteration(new IllegalStateException(
                                "Should fail when address not valid"));
                    }
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testValidateSelfSignNotAccepted() throws Throwable {
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;

        Operation op = Operation.createPut(getContainerHostValidateUri())
                .setBody(containerHostSpec)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    try {
                        verifyStatusCode(o.getStatusCode(), Operation.STATUS_CODE_OK);
                        SslTrustCertificateState body = o
                                .getBody(SslTrustCertificateState.class);
                        if (body == null) {
                            host.failIteration(new IllegalStateException(
                                    "Expected SslTrustCertificateState in the body to be accepted."));
                        } else {
                            host.completeIteration();
                        }
                    } catch (IllegalStateException ex) {
                        host.failIteration(ex);
                    }

                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testValidateSelfSignWhenAccepted() throws Throwable {
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        containerHostSpec.acceptCertificate = true;

        Operation op = Operation
                .createPut(getContainerHostValidateUri())
                .setBody(containerHostSpec)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    try {
                        verifyStatusCode(o.getStatusCode(), HttpURLConnection.HTTP_NO_CONTENT);
                        if (o.getBodyRaw() != null) {
                            host.failIteration(new IllegalStateException(
                                    "No body expected when ssl cert accepted."));
                        } else {
                            host.completeIteration();
                        }
                    } catch (IllegalStateException ex) {
                        host.failIteration(ex);
                    }

                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testValidateShouldPassForVicHost() throws Throwable {
        vicHostSpec.acceptCertificate = true;
        vicHostState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        markHostForVicValidation(vicHostState);

        Operation op = Operation.createPut(getContainerHostValidateUri())
                .setBody(vicHostSpec)
                .setCompletion((o, e) -> {
                    try {
                        verifyStatusCode(o.getStatusCode(), HttpURLConnection.HTTP_NO_CONTENT);
                        host.completeIteration();
                    } catch (IllegalStateException ex) {
                        if (e != null) {
                            host.log("Unexpected exception: %s", Utils.toString(e));
                        }
                        host.failIteration(ex);
                    }
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testValidateShouldFailWhenDockerHostClaimsToBeVic() throws Throwable {
        containerHostSpec.acceptCertificate = true;
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        markHostForVicValidation(computeState);

        Operation op = Operation.createPut(getContainerHostValidateUri())
                .setBody(containerHostSpec)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        try {
                            verifyStatusCode(o.getStatusCode(), Operation.STATUS_CODE_BAD_REQUEST);
                            String error = e.getMessage();
                            if (error.equals(
                                    ContainerHostService.CONTAINER_HOST_IS_NOT_VIC_MESSAGE)) {
                                host.completeIteration();
                            } else {
                                String message = String.format(
                                        "Error message should be '%s' but was '%s'",
                                        ContainerHostService.CONTAINER_HOST_IS_NOT_VIC_MESSAGE,
                                        error);
                                host.failIteration(new IllegalStateException(message));
                            }
                        } catch (IllegalStateException ex) {
                            host.log("Unexpected exception: %s", Utils.toString(e));
                            host.failIteration(ex);
                        }
                    } else {
                        host.failIteration(new IllegalStateException(
                                "Should fail when docker host claims to be a VIC host"));
                    }
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testAddHostWhenSelfSignNotAccepted() throws Throwable {
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;

        Operation op = Operation
                .createPut(containerHostUri)
                .setBody(containerHostSpec)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    try {
                        verifyStatusCode(o.getStatusCode(), Operation.STATUS_CODE_OK);
                        SslTrustCertificateState body = o
                                .getBody(SslTrustCertificateState.class);
                        if (body == null) {
                            host.failIteration(new IllegalStateException(
                                    "Expected SslTrustCertificateState in the body to be accepted."));
                        } else {
                            host.completeIteration();
                        }
                    } catch (IllegalStateException ex) {
                        host.failIteration(ex);
                    }

                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testAddHostWhenSelfSignAccepted() throws Throwable {
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        containerHostSpec.acceptCertificate = true;

        Operation op = Operation
                .createPut(containerHostUri)
                .setBody(containerHostSpec)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    try {
                        verifyStatusCode(o.getStatusCode(), HttpURLConnection.HTTP_NO_CONTENT);
                        if (o.getBodyRaw() != null) {
                            host.failIteration(new IllegalStateException(
                                    "No body expected when ssl cert accepted."));
                        } else {
                            host.completeIteration();
                        }
                    } catch (IllegalStateException ex) {
                        host.failIteration(ex);
                    }

                });
        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testAddHostWhenSelfSignNotAcceptedInitially() throws Throwable {
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;

        Operation op = Operation
                .createPut(containerHostUri)
                .setBody(containerHostSpec)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    SslTrustCertificateState body = o
                            .getBody(SslTrustCertificateState.class);

                    // Adding the SslTrustCertificateState as a property to be accepted.
                    containerHostSpec.sslTrust = body;

                    Operation retryOp = Operation
                            .createPut(containerHostUri)
                            .setBody(containerHostSpec)
                            .setCompletion(
                                    (retryO, retryE) -> {
                                        if (retryE != null) {
                                            host.failIteration(retryE);
                                            return;
                                        }
                                        try {
                                            verifyStatusCode(retryO.getStatusCode(),
                                                    HttpURLConnection.HTTP_NO_CONTENT);
                                            if (retryO.getBodyRaw() != null) {
                                                host.failIteration(new IllegalStateException(
                                                        "No body expected when ssl cert accepted."));
                                            } else {
                                                host.completeIteration();
                                            }
                                        } catch (IllegalStateException ex) {
                                            host.failIteration(ex);
                                        }
                                    });
                    host.send(retryOp);

                });
        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testStoreHostSelfSignedCertificateAndAddHost() throws Throwable {
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        SslTrustCertificateState[] certs = new SslTrustCertificateState[] { null };
        Operation op = Operation
                .createPut(containerHostUri)
                .setBody(containerHostSpec)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.failIteration(e);
                                return;
                            }
                            SslTrustCertificateState body = o
                                    .getBody(SslTrustCertificateState.class);
                            Operation storeCertOperation = Operation
                                    .createPost(UriUtils.buildUri(host,
                                            SslTrustCertificateService.FACTORY_LINK))
                                    .setBody(body)
                                    .setCompletion(
                                            (certO, certE) -> {
                                                if (certE != null) {
                                                    host.failIteration(certE);
                                                    return;
                                                }

                                                SslTrustCertificateState state = certO
                                                        .getBody(SslTrustCertificateState.class);
                                                certs[0] = state;

                                                Operation retryOp = Operation
                                                        .createPut(containerHostUri)
                                                        .setBody(containerHostSpec)
                                                        .setCompletion(
                                                                (retryO, retryE) -> {
                                                                    if (retryE != null) {
                                                                        host.failIteration(retryE);
                                                                        return;
                                                                    }
                                                                    try {
                                                                        verifyStatusCode(
                                                                                retryO.getStatusCode(),
                                                                                HttpURLConnection.HTTP_NO_CONTENT);
                                                                        if (retryO
                                                                                .getBodyRaw() != null) {
                                                                            host.failIteration(
                                                                                    new IllegalStateException(
                                                                                            "No body expected when ssl cert accepted."));
                                                                        } else {
                                                                            host.completeIteration();
                                                                        }
                                                                    } catch (IllegalStateException ex) {
                                                                        host.failIteration(ex);
                                                                    }

                                                                });
                                                host.send(retryOp);
                                            });
                            host.send(storeCertOperation);
                        });

        host.testStart(1);
        host.send(op);
        host.testWait();

        safeDelete(certs[0]);
    }

    @Test
    public void testAddHostWithoutValidation() throws Throwable {
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        containerHostSpec.acceptHostAddress = true;
        String[] result = new String[] { null };
        Operation op = Operation
                .createPut(containerHostUri)
                .setBody(containerHostSpec)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    try {
                        verifyStatusCode(o.getStatusCode(), HttpURLConnection.HTTP_NO_CONTENT);
                        if (o.getBodyRaw() != null) {
                            host.failIteration(new IllegalStateException(
                                    "No body expected when ssl cert accepted."));
                        } else {
                            result[0] = o.getResponseHeader(Operation.LOCATION_HEADER);
                            host.completeIteration();
                        }
                    } catch (IllegalStateException ex) {
                        host.failIteration(ex);
                    }
                });
        host.testStart(1);
        host.send(op);
        host.testWait();

        String location = result[0];
        assertNotNull(location);

        ComputeState cs = getDocument(ComputeState.class, location);
        assertEquals(computeState.address, cs.address);
    }

    @Test
    public void testAddShouldPassForVicHost() throws Throwable {
        vicHostSpec.acceptCertificate = true;
        vicHostState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        markHostForVicValidation(vicHostState);

        addHost(vicHostSpec);
    }

    @Test
    public void testAddShouldFailWhenDockerHostClaimsToBeVic() throws Throwable {
        containerHostSpec.acceptCertificate = true;
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        markHostForVicValidation(computeState);

        addHost(containerHostSpec, ContainerHostService.CONTAINER_HOST_IS_NOT_VIC_MESSAGE);
    }

    @Test
    public void testAddSecondDockerHostShouldPass() throws Throwable {
        // First add a docker host
        containerHostSpec.acceptCertificate = true;
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        addHost(containerHostSpec);

        // Adding a second docker host should pass
        ComputeState hostState2 = createDockerHostState();
        hostState2.address = VALID_DOCKER_HOST_NODE2_ADDRESS;
        ContainerHostSpec hostSpec2 = new ContainerHostSpec();
        hostSpec2.acceptCertificate = true;
        hostSpec2.hostState = hostState2;
        addHost(hostSpec2);
    }

    @Test
    public void testAddDockerHostToPlacementZoneWithVicHostShouldFail() throws Throwable {
        // First add a VIC host
        vicHostSpec.acceptCertificate = true;
        vicHostState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        markHostForVicValidation(vicHostState);
        addHost(vicHostSpec);
        // data collection is needed to patch the host state with the data that marks it as VIC
        dataCollectHost(vicHostState);

        // Adding a Docker host should now fail because there is a scheduler in the placement zone
        ComputeState dockerHostState = createDockerHostState();
        dockerHostState.address = VALID_DOCKER_HOST_NODE2_ADDRESS;
        ContainerHostSpec dockerHostSpec = new ContainerHostSpec();
        dockerHostSpec.acceptCertificate = true;
        dockerHostSpec.hostState = dockerHostState;
        addHost(dockerHostSpec, ContainerHostService.PLACEMENT_ZONE_CONTAINS_SCHEDULERS_MESSAGE);
    }

    @Test
    public void testAddVicHostToPlacementZoneWithVicHostShouldFail() throws Throwable {
        // First add a VIC host
        vicHostSpec.acceptCertificate = true;
        vicHostState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        markHostForVicValidation(vicHostState);
        addHost(vicHostSpec);
        // data collection is needed to patch the host state with the data that marks it as VIC
        dataCollectHost(vicHostState);

        // Adding another VIC host should now fail because the placement zone is not empty
        ComputeState vicHostState2 = createVicHostState();
        vicHostState2.address = VALID_DOCKER_HOST_NODE2_ADDRESS;
        markHostForVicValidation(vicHostState2);
        ContainerHostSpec vicHostSpec2 = new ContainerHostSpec();
        vicHostSpec2.acceptCertificate = true;
        vicHostSpec2.hostState = vicHostState2;
        addHost(vicHostSpec2, ContainerHostService.PLACEMENT_ZONE_NOT_EMPTY_MESSAGE);
    }

    @Test
    public void testAddVicHostToPlacementZoneWithDockerHostShouldFail() throws Throwable {
        // First add the docker host
        containerHostSpec.acceptCertificate = true;
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        addHost(containerHostSpec);

        // Adding a VIC host should now fail because the placement zone is not empty
        ComputeState vicHostState = createVicHostState();
        vicHostState.address = VALID_DOCKER_HOST_NODE2_ADDRESS;
        markHostForVicValidation(vicHostState);
        ContainerHostSpec vicHostSpec = new ContainerHostSpec();
        vicHostSpec.acceptCertificate = true;
        vicHostSpec.hostState = vicHostState;
        addHost(vicHostSpec, ContainerHostService.PLACEMENT_ZONE_NOT_EMPTY_MESSAGE);
    }

    private URI getContainerHostValidateUri() {
        return UriUtils.buildUri(host, ContainerHostService.SELF_LINK,
                ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);
    }

    private void addHost(ContainerHostSpec hostSpec) {
        addHost(hostSpec, null);
    }

    private void addHost(ContainerHostSpec hostSpec, String expectedError) {
        Operation op = Operation.createPut(containerHostUri)
                .setBody(hostSpec)
                .setCompletion((o, e) -> {
                    if (expectedError == null) {
                        // add should succeed
                        try {
                            verifyStatusCode(o.getStatusCode(), HttpURLConnection.HTTP_NO_CONTENT);
                            hostSpec.hostState.documentSelfLink = o
                                    .getResponseHeader(Operation.LOCATION_HEADER);
                            host.completeIteration();
                        } catch (IllegalStateException ex) {
                            host.log("Unexpected exception: %s", Utils.toString(e));
                            host.failIteration(ex);
                        }
                    } else {
                        // add should fail
                        if (e != null) {
                            try {
                                verifyStatusCode(o.getStatusCode(),
                                        Operation.STATUS_CODE_BAD_REQUEST);
                                String error = e.getMessage();
                                if (error.equals(expectedError)) {
                                    host.completeIteration();
                                } else {
                                    String message = String.format(
                                            "Error message should be '%s' but was '%s'",
                                            expectedError, error);
                                    host.failIteration(new IllegalStateException(message));
                                }
                            } catch (IllegalStateException ex) {
                                host.log("Expected validation exception but got: %s",
                                        Utils.toString(e));
                                host.failIteration(ex);
                            }

                        } else {
                            String error = String.format("Should fail with '%s'", expectedError);
                            host.failIteration(new IllegalStateException(error));
                        }
                    }
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    private void verifyStatusCode(int statusCode, int expectedStatusCode) {
        if (statusCode != expectedStatusCode) {
            String errorMessage = String.format("Expected status code %d but was %d",
                    expectedStatusCode, statusCode);
            throw new IllegalStateException(errorMessage);
        }

    }

    private void markHostForVicValidation(ComputeState cs) {
        cs.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.VIC.toString());
    }

    private void dataCollectHost(ComputeState cs) {
        AdapterRequest request = new AdapterRequest();
        request.operationTypeId = ContainerHostOperationType.INFO.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        request.resourceReference = UriUtils.buildUri(host, cs.documentSelfLink);
        request.customProperties = new HashMap<>();
        request.customProperties.put(MockDockerHostAdapterService.CONTAINER_HOST_TYPE_PROP_NAME,
                cs.customProperties.get(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME));

        Operation op = Operation.createPatch(host, ManagementUriParts.ADAPTER_DOCKER_HOST)
                .setBody(request)
                .setReferer("/")
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    } else {
                        host.completeIteration();
                    }
                });

        // wait for data collection to complete
        host.testStart(1);
        op.sendWith(host);
        host.testWait();
    }

    private ComputeState createDockerHostState() {
        ComputeState state = createComputeState();
        state.customProperties.put(MockDockerHostAdapterService.CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.DOCKER.toString());
        return state;
    }

    private ComputeState createVicHostState() {
        ComputeState state = createComputeState();
        state.customProperties.put(MockDockerHostAdapterService.CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.VIC.toString());
        return state;
    }

    private ComputeState createComputeState() {
        ComputeState computeState = new ComputeState();
        computeState.address = "https://test-server";
        computeState.resourcePoolLink = "test-resource-pool";
        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                "authCredentialsLink");
        computeState.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());

        return computeState;
    }
}
