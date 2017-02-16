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

import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.registry.service.RegistryAdapterService;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.DelegatingX509TrustManager;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.compute.EndpointCertificateUtil;
import com.vmware.admiral.compute.RegistryConfigCertificateDistributionService;
import com.vmware.admiral.compute.RegistryHostConfigService;
import com.vmware.admiral.compute.RegistryHostConfigService.RegistryHostSpec;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.admiral.service.common.SslTrustImportService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class RegistryHostValidatingHelperServiceIT extends BaseTestCase {
    private static final String TEST_REGISTRY_ADDRESS = getSystemOrTestProp("test.registry");
    private static final String TEST_REGISTRY_ADDRESS_INVALID = "https://127.0.0.1:-1";
    private RegistryHostSpec hostState;
    private RegistryState registryState;

    private URI helperUri;
    private URI helperWithValidationUri;

    @Before
    public void setUp() throws Throwable {
        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        ConfigurationFactoryService.class)),
                new ConfigurationFactoryService());

        host.startFactory(new SslTrustCertificateService());

        host.startService(
                Operation.createPost(UriUtils.buildUri(host, SslTrustImportService.class)),
                new SslTrustImportService());

        host.startFactory(new RegistryService());

        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        RegistryHostConfigService.class)),
                new RegistryHostConfigService());

        host.startService(
                Operation.createPost(UriUtils.buildUri(host, RegistryAdapterService.class)),
                new RegistryAdapterService());

        host.startService(
                Operation.createPost(UriUtils.buildUri(host,
                        RegistryConfigCertificateDistributionService.class)),
                new RegistryConfigCertificateDistributionService());

        waitForServiceAvailability(ConfigurationFactoryService.SELF_LINK);
        waitForServiceAvailability(SslTrustCertificateService.FACTORY_LINK);
        waitForServiceAvailability(SslTrustImportService.SELF_LINK);
        waitForServiceAvailability(RegistryService.FACTORY_LINK);
        waitForServiceAvailability(RegistryHostConfigService.SELF_LINK);
        waitForServiceAvailability(RegistryAdapterService.SELF_LINK);
        waitForServiceAvailability(RegistryConfigCertificateDistributionService.SELF_LINK);

        ServerX509TrustManager.init(host);

        registryState = createRegistryState();

        hostState = new RegistryHostSpec();
        hostState.hostState = registryState;

        helperUri = UriUtils.buildUri(host, RegistryHostConfigService.SELF_LINK);
        helperWithValidationUri = UriUtils.extendUriWithQuery(helperUri,
                EndpointCertificateUtil.REQUEST_PARAM_VALIDATE_OPERATION_NAME, "true");

        DeploymentProfileConfig.getInstance().setTest(false);
    }

    public void tearDown() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);
    }

    @Test
    public void testValidateShouldFailWithInvalidUri() throws Throwable {
        Operation op = Operation
                .createPut(helperWithValidationUri)
                .setBody(hostState)
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
        registryState.address = TEST_REGISTRY_ADDRESS;

        Operation op = Operation.createPut(helperWithValidationUri)
                .setBody(hostState)
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
        registryState.address = TEST_REGISTRY_ADDRESS;
        hostState.acceptCertificate = true;

        Operation op = Operation
                .createPut(helperWithValidationUri)
                .setBody(hostState)
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
        registryState.address = TEST_REGISTRY_ADDRESS;
        // remove previously added certificates to simulate 'not accepted' case
        cleanTrustCertificate();

        Operation op = Operation
                .createPut(helperUri)
                .setBody(hostState)
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

    private void cleanTrustCertificate() throws NoSuchFieldException, IllegalAccessException {
        ServerX509TrustManager x509TrustManager = ServerX509TrustManager.init(null);
        Field f = ServerX509TrustManager.class.getDeclaredField("delegatingTrustManager");
        f.setAccessible(true);
        DelegatingX509TrustManager delegatingX509TrustManager = (DelegatingX509TrustManager)
                f.get(x509TrustManager);

        f = DelegatingX509TrustManager.class.getDeclaredField("delegates");
        f.setAccessible(true);
        @SuppressWarnings("rawtypes")
        Map delegates = (Map) f.get(delegatingX509TrustManager);
        delegates.clear();
    }

    @Test
    public void testAddHostWhenSelfSignAccepted() throws Throwable {
        registryState.address = TEST_REGISTRY_ADDRESS;
        hostState.acceptCertificate = true;

        Operation op = Operation
                .createPut(helperUri)
                .setBody(hostState)
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
    public void testAddHostWithoutValidation() throws Throwable {
        registryState.address = TEST_REGISTRY_ADDRESS;
        hostState.acceptHostAddress = true;
        String[] result = new String[] { null };
        Operation op = Operation
                .createPut(helperUri)
                .setBody(hostState)
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

        RegistryState rs = getDocument(RegistryState.class, location);
        assertEquals(registryState.address, rs.address);
    }

    @Test
    public void testAddHostWithTrailingForwardSlashes() throws Throwable {
        RegistryState rs = new RegistryState();
        rs.address = TEST_REGISTRY_ADDRESS + "///";
        rs.name = getClass().getName();
        rs.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;

        RegistryHostSpec hs = new RegistryHostSpec();
        hs.hostState = rs;
        hs.acceptHostAddress = true;

        String[] result = new String[] { null };

        Operation createRegistryOp = Operation
                .createPut(helperUri)
                .setBody(hs)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }

                    result[0] = o.getResponseHeader(Operation.LOCATION_HEADER);
                    host.completeIteration();
                });

        host.testStart(1);
        host.send(createRegistryOp);
        host.testWait();

        String location = result[0];
        assertNotNull(location);

        RegistryState regState = getDocument(RegistryState.class, location);
        assertEquals(TEST_REGISTRY_ADDRESS, regState.address);
    }

    private RegistryState createRegistryState() {
        RegistryState registryState = new RegistryState();
        registryState.name = getClass().getName();
        registryState.address = TEST_REGISTRY_ADDRESS_INVALID;
        registryState.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;

        return registryState;
    }

}
