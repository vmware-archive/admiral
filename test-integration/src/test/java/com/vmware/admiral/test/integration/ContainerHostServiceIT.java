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

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerHostServiceIT extends RequestBaseTest {
    private static final String VALID_DOCKER_HOST_ADDRESS = String.format("%s:%s",
            getSystemOrTestProp("docker.host.address"),
            getSystemOrTestProp("docker.host.port.API"));
    private ComputeState computeState;
    private ContainerHostSpec containerHostSpec;
    private URI containerHostUri;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ComputeDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ContainerHostService.SELF_LINK);
        waitForServiceAvailability(SslTrustCertificateService.FACTORY_LINK);
        ServerX509TrustManager.init(host);

        computeState = createComputeState();

        containerHostSpec = new ContainerHostSpec();
        containerHostSpec.hostState = computeState;

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
                        if (o.getStatusCode() != Operation.STATUS_CODE_BAD_REQUEST) {
                            host.log("Unexpected exception: %s", Utils.toString(e));
                            host.failIteration(new IllegalStateException(
                                    "Validation exception expected"));
                            return;
                        }
                        host.completeIteration();
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
        computeState.address = VALID_DOCKER_HOST_ADDRESS;

        Operation op = Operation.createPut(getContainerHostValidateUri())
                .setBody(containerHostSpec)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    if (o.getStatusCode() != HttpURLConnection.HTTP_OK) {
                        host.failIteration(new IllegalStateException(
                                "Expected status code 200 when ssl cert not accepted."));
                        return;
                    }
                    SslTrustCertificateState body = o
                            .getBody(SslTrustCertificateState.class);
                    if (body == null) {
                        host.failIteration(new IllegalStateException(
                                "Expected SslTrustCertificateState in the body to be accepted."));
                        return;
                    }

                    host.completeIteration();
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testValidateSelfSignWhenAccepted() throws Throwable {
        computeState.address = VALID_DOCKER_HOST_ADDRESS;
        containerHostSpec.acceptCertificate = true;

        Operation op = Operation
                .createPut(getContainerHostValidateUri())
                .setBody(containerHostSpec)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    if (o.getStatusCode() != HttpURLConnection.HTTP_NO_CONTENT) {
                        host.failIteration(new IllegalStateException(
                                "Expected status code 204 when ssl cert accepted. Status: "
                                        + o.getStatusCode()));
                        return;
                    }

                    if (o.getBodyRaw() != null) {
                        host.failIteration(new IllegalStateException(
                                "No body expected when ssl cert accepted."));
                        return;
                    }

                    host.completeIteration();
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testAddHostWhenSelfSignNotAccepted() throws Throwable {
        computeState.address = VALID_DOCKER_HOST_ADDRESS;

        Operation op = Operation
                .createPut(containerHostUri)
                .setBody(containerHostSpec)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    if (o.getStatusCode() != HttpURLConnection.HTTP_OK) {
                        host.failIteration(new IllegalStateException(
                                "Expected status code 200 when ssl cert not accepted. Status: "
                                        + o.getStatusCode()));
                        return;
                    }
                    SslTrustCertificateState body = o
                            .getBody(SslTrustCertificateState.class);
                    if (body == null) {
                        host.failIteration(new IllegalStateException(
                                "Expected SslTrustCertificateState in the body to be accepted."));
                        return;
                    }

                    host.completeIteration();
                });

        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testAddHostWhenSelfSignAccepted() throws Throwable {
        computeState.address = VALID_DOCKER_HOST_ADDRESS;
        containerHostSpec.acceptCertificate = true;

        Operation op = Operation
                .createPut(containerHostUri)
                .setBody(containerHostSpec)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    if (o.getStatusCode() != HttpURLConnection.HTTP_NO_CONTENT) {
                        host.failIteration(new IllegalStateException(
                                "Expected status code 204 when ssl cert accepted. Status: "
                                        + o.getStatusCode()));
                        return;
                    }

                    if (o.getBodyRaw() != null) {
                        host.failIteration(new IllegalStateException(
                                "No body expected when ssl cert accepted."));
                        return;
                    }

                    host.completeIteration();
                });
        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testAddHostWhenSelfSignNotAcceptedInitially() throws Throwable {
        computeState.address = VALID_DOCKER_HOST_ADDRESS;

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
                                        if (retryO.getStatusCode() != HttpURLConnection.HTTP_NO_CONTENT) {
                                            host.failIteration(new IllegalStateException(
                                                    "Expected status code 204 when ssl cert accepted. Status: "
                                                            + retryO.getStatusCode()));
                                            return;
                                        }

                                        if (retryO.getBodyRaw() != null) {
                                            host.failIteration(new IllegalStateException(
                                                    "No body expected when ssl cert accepted."));
                                            return;
                                        }

                                        host.completeIteration();
                                    });
                    host.send(retryOp);

                });
        host.testStart(1);
        host.send(op);
        host.testWait();
    }

    @Test
    public void testStoreHostSelfSignedCertificateAndAddHost() throws Throwable {
        computeState.address = VALID_DOCKER_HOST_ADDRESS;
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
                                                                    if (retryO.getStatusCode() != HttpURLConnection.HTTP_NO_CONTENT) {
                                                                        host.failIteration(new IllegalStateException(
                                                                                "Expected status code 204 when ssl cert accepted. Status: "
                                                                                        + retryO.getStatusCode()));
                                                                        return;
                                                                    }

                                                                    if (retryO.getBodyRaw() != null) {
                                                                        host.failIteration(new IllegalStateException(
                                                                                "No body expected when ssl cert accepted."));
                                                                        return;
                                                                    }

                                                                    host.completeIteration();
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
        computeState.address = VALID_DOCKER_HOST_ADDRESS;
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
                    if (o.getStatusCode() != HttpURLConnection.HTTP_NO_CONTENT) {
                        host.failIteration(new IllegalStateException(
                                "Expected status code 204 when ssl cert accepted. Status: "
                                        + o.getStatusCode()));
                        return;
                    }

                    if (o.getBodyRaw() != null) {
                        host.failIteration(new IllegalStateException(
                                "No body expected when ssl cert accepted."));
                        return;
                    }

                    result[0] = o.getResponseHeader(Operation.LOCATION_HEADER);
                    host.completeIteration();
                });
        host.testStart(1);
        host.send(op);
        host.testWait();

        String location = result[0];
        assertNotNull(location);

        ComputeState cs = getDocument(ComputeState.class, location);
        assertEquals(computeState.address, cs.address);
    }

    private URI getContainerHostValidateUri() {
        return UriUtils.buildUri(host, ContainerHostService.SELF_LINK,
                ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);
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
