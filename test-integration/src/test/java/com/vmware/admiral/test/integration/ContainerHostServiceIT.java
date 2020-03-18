/*
 * Copyright (c) 2016-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.integration;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getSystemOrTestProp;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.PlacementZoneConstants;
import com.vmware.admiral.compute.PlacementZoneConstants.PlacementZoneType;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.HostPortProfileService;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

public class ContainerHostServiceIT extends RequestBaseTest {
    private static final String SCHEDULER_PLACEMENT_ZONE_ID = "test-scheduler-placement-zone";

    private static final String VALID_DOCKER_HOST_NODE1_ADDRESS = String.format("%s:%s",
            getSystemOrTestProp("docker.host.cluster.node1.address"),
            getSystemOrTestProp("docker.host.port.API"));
    private static final String VALID_DOCKER_HOST_NODE2_ADDRESS = String.format("%s:%s",
            getSystemOrTestProp("docker.host.cluster.node2.address"),
            getSystemOrTestProp("docker.host.port.API"));

    private ResourcePoolState schedulerPlacementZone;
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

        // Make sure the trust manager will start monitoring certificates and will therefore
        // acknowledge cert additions and deletions. ServerX509TrustManager.create does the same as
        // ServerX509TrustManager.init but also starts a subsctiption manager that monitors the
        // certificates. The invalidate method is called to ensure that the subscription manager
        // will be started even if somebody else has already called the init method.
        ServerX509TrustManager.invalidate();
        ServerX509TrustManager.create(host);

        schedulerPlacementZone = createSchedulerPlacementZone();

        computeState = createDockerHostState();
        containerHostSpec = new ContainerHostSpec();
        containerHostSpec.hostState = computeState;

        vicHostState = createVicHostState();
        vicHostSpec = new ContainerHostSpec();
        vicHostSpec.hostState = vicHostState;

        containerHostUri = UriUtils.buildUri(host, ContainerHostService.SELF_LINK);
        DeploymentProfileConfig.getInstance().setTest(false);
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
    public void testValidateDockerHostDeclaredAsVicShouldFail() throws Throwable {
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
                                    ContainerHostService.CONTAINER_HOST_IS_NOT_VCH_MESSAGE)) {
                                host.completeIteration();
                            } else {
                                String message = String.format(
                                        "Error message should be '%s' but was '%s'",
                                        ContainerHostService.CONTAINER_HOST_IS_NOT_VCH_MESSAGE,
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
                            if (!o.hasBody()) {
                                host.failIteration(new IllegalStateException(
                                        "Expected certificate to be returned in body"));
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
        // use a scheduler placement zone
        computeState.resourcePoolLink = schedulerPlacementZone.documentSelfLink;

        addHost(containerHostSpec, ContainerHostService.CONTAINER_HOST_IS_NOT_VCH_MESSAGE);
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
        vicHostState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        markHostForVicValidation(vicHostState);
        // force add this docker host to a scheduler placement zone for the purpose of the test
        vicHostState.resourcePoolLink = computeState.resourcePoolLink;
        directAddHost(vicHostState);
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
        // force add this vic host to a docker placement zone for the purpose of the test
        computeState.resourcePoolLink = vicHostState.resourcePoolLink;
        directAddHost(computeState);
        // data collection is needed to patch the host state with the data that marks it as VIC
        dataCollectHost(computeState);

        // Adding a VIC host should now fail because the placement zone is not empty
        ComputeState vicHostState = createVicHostState();
        vicHostState.address = VALID_DOCKER_HOST_NODE2_ADDRESS;
        markHostForVicValidation(vicHostState);
        ContainerHostSpec vicHostSpec = new ContainerHostSpec();
        vicHostSpec.acceptCertificate = true;
        vicHostSpec.hostState = vicHostState;
        addHost(vicHostSpec, ContainerHostService.PLACEMENT_ZONE_NOT_EMPTY_MESSAGE);
    }

    @Test
    public void testAddDockerHostWithNoPlacementZoneShouldPass() throws Throwable {
        containerHostSpec.acceptCertificate = true;
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        computeState.resourcePoolLink = null;
        addHost(containerHostSpec);
    }

    @Test
    public void testAddDockerHostDeclaredAsVicWithSchedulerPlacementZoneShouldFail() {
        containerHostSpec.acceptCertificate = true;
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        computeState.resourcePoolLink = schedulerPlacementZone.documentSelfLink;
        markHostForVicValidation(computeState);
        addHost(containerHostSpec, ContainerHostService.CONTAINER_HOST_IS_NOT_VCH_MESSAGE);
    }

    @Test
    public void testAddDockerHostDeclaredAsVicWithNoPlacementZoneShouldFail() throws Throwable {
        containerHostSpec.acceptCertificate = true;
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        computeState.resourcePoolLink = null;
        markHostForVicValidation(computeState);

        long placementZonesCount = getDocumentsCount(ResourcePoolState.class);
        long placementsCount = getDocumentsCount(GroupResourcePlacementState.class);

        addHost(containerHostSpec, ContainerHostService.CONTAINER_HOST_IS_NOT_VCH_MESSAGE);
        waitFor(() -> {
            return (getDocumentsCount(ResourcePoolState.class) == placementZonesCount)
                    && (getDocumentsCount(GroupResourcePlacementState.class) == placementsCount);
        });
    }

    private <T extends ServiceDocument> long getDocumentsCount(Class<T> documentKind) {
        AtomicLong documentsCount = new AtomicLong(0);
        QueryTask queryTask = QueryUtil.buildPropertyQuery(documentKind);
        QueryUtil.addCountOption(queryTask);

        host.testStart(1);
        new ServiceDocumentQuery<>(host, documentKind).query(queryTask, (r) -> {
            if (r.hasException()) {
                host.failIteration(r.getException());
            } else if (r.hasResult()) {
                documentsCount.set(r.getCount());
                host.completeIteration();
            }
        });
        host.testWait();

        return documentsCount.get();
    }

    @Test
    public void testAddVicHostWithNoPlacementZoneShouldCreatePlacementZoneAndPlacement() throws Throwable {
        vicHostSpec.acceptCertificate = true;
        vicHostState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        vicHostState.resourcePoolLink = null;
        markHostForVicValidation(vicHostState);

        // add and verify host
        ComputeState addedHost = addHost(vicHostSpec, null);
        assertNotNull("added host must not be null", addedHost);
        assertNotNull("created placement zone link must not be null", addedHost.resourcePoolLink);
        assertNotNull(addedHost.customProperties);
        assertTrue(Boolean.parseBoolean(addedHost.customProperties.get(
                ComputeConstants.AUTOGENERATED_PLACEMENT_ZONE_PROP_NAME)));

        // verify placement zone
        StringBuffer placementZoneLink = new StringBuffer();
        host.testStart(1);
        Operation.createGet(host, addedHost.resourcePoolLink)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    } else {
                        ResourcePoolState placementZone = o.getBody(ResourcePoolState.class);
                        assertNotNull(placementZone);
                        assertNotNull(placementZone.customProperties);
                        assertEquals(PlacementZoneType.SCHEDULER.toString(),
                                placementZone.customProperties.get(
                                        PlacementZoneConstants.PLACEMENT_ZONE_TYPE_CUSTOM_PROP_NAME));
                        placementZoneLink.append(placementZone.documentSelfLink);
                        host.completeIteration();
                    }
                }).sendWith(host);
        host.testWait();

        // get the created placement
        host.testStart(1);
        QueryTask queryTask = QueryUtil.buildPropertyQuery(GroupResourcePlacementState.class,
                GroupResourcePlacementState.FIELD_NAME_RESOURCE_POOL_LINK,
                addedHost.resourcePoolLink,
                GroupResourcePlacementState.FIELD_NAME_RESOURCE_TYPE,
                ResourceType.CONTAINER_TYPE.getName());
        QueryUtil.addExpandOption(queryTask);
        ArrayList<GroupResourcePlacementState> results = new ArrayList<>();
        new ServiceDocumentQuery<>(host, GroupResourcePlacementState.class).query(queryTask,
                (r) -> {
                    if (r.hasException()) {
                        host.failIteration(r.getException());
                    } else if (r.hasResult()) {
                        results.add(r.getResult());
                    } else {
                        host.completeIteration();
                    }
                });
        host.testWait();

        // verify the placement
        assertEquals(1, results.size());
        GroupResourcePlacementState createdPlacement = results.iterator().next();
        assertNotNull(createdPlacement);
        assertEquals(placementZoneLink.toString(), createdPlacement.resourcePoolLink);
        assertEquals(ResourceType.CONTAINER_TYPE.getName(), createdPlacement.resourceType);
        assertNotNull(createdPlacement.customProperties);
        assertTrue(Boolean.parseBoolean(createdPlacement.customProperties
                .get(GroupResourcePlacementState.AUTOGENERATED_PLACEMENT_PROP_NAME)));
    }

    @Test
    public void testAddVicHostWithTagsShouldFail() throws Throwable {
        vicHostSpec.acceptCertificate = true;
        vicHostState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        vicHostState.tagLinks = new HashSet<>();
        vicHostState.tagLinks.add("sample-tag-link");
        markHostForVicValidation(vicHostState);

        addHost(vicHostSpec,
                String.format(AssertUtil.PROPERTY_MUST_BE_EMPTY_MESSAGE_FORMAT, "tagLinks"));
    }

    @Test
    public void testAddDockerHostToSchedulerPlacementZoneShouldFail() {
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        computeState.resourcePoolLink = schedulerPlacementZone.documentSelfLink;
        containerHostSpec.acceptCertificate = true;

        addHost(containerHostSpec,
                String.format(ContainerHostService.INCORRECT_PLACEMENT_ZONE_TYPE_MESSAGE_FORMAT,
                        PlacementZoneType.DOCKER.toString(),
                        PlacementZoneType.SCHEDULER.toString()));
    }

    @Test
    public void testAddVicHostToDockerPlacementZoneShouldFail() {
        vicHostState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        vicHostState.resourcePoolLink = resourcePool.documentSelfLink;
        vicHostSpec.acceptCertificate = true;
        markHostForVicValidation(vicHostState);

        addHost(vicHostSpec,
                String.format(ContainerHostService.INCORRECT_PLACEMENT_ZONE_TYPE_MESSAGE_FORMAT,
                        PlacementZoneType.SCHEDULER.toString(),
                        PlacementZoneType.DOCKER.toString()));
    }

    @Test
    public void testAddVicHostDeclaredAsDockerShouldPass() {
        vicHostState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        vicHostState.resourcePoolLink = resourcePool.documentSelfLink;
        vicHostSpec.acceptCertificate = true;
        // since the host is not marked for VIC validation, it will be treated as docker host

        addHost(vicHostSpec);
    }

    @Test
    public void testAddVicHostDeclaredAsDockerToPlacementZoneWithDockerHostShouldPass() {
        // First add a docker host
        computeState.address = VALID_DOCKER_HOST_NODE1_ADDRESS;
        computeState.resourcePoolLink = resourcePool.documentSelfLink;
        containerHostSpec.acceptCertificate = true;

        addHost(containerHostSpec);

        // Then try to add a VIC host that is declared as docker. It should pass
        vicHostState.address = VALID_DOCKER_HOST_NODE2_ADDRESS;
        vicHostState.resourcePoolLink = resourcePool.documentSelfLink;
        vicHostSpec.acceptCertificate = true;
        // since the host is not marked for VIC validation, it will be treated as docker host

        addHost(vicHostSpec);
    }

    private URI getContainerHostValidateUri() {
        return UriUtils.buildUri(host, ContainerHostService.SELF_LINK,
                ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);
    }

    private void addHost(ContainerHostSpec hostSpec) {
        addHost(hostSpec, null);
    }

    /**
     * Tries to add the host from the provided host spec. Will throw an error if an unexpected error
     * occurs, will return null if an expected error is caught or will return the added
     * {@link ComputeState} on success.
     *
     * @param hostSpec
     *            a hostSpec that contains the host to add
     * @param expectedError
     *            the expected error message. Set to <code>null</code> if none is expected.
     */
    private ComputeState addHost(ContainerHostSpec hostSpec, String expectedError) {
        ComputeState addedHost = new ComputeState();

        Operation op = Operation.createPut(containerHostUri)
                .setBody(hostSpec)
                .setCompletion((o, e) -> {
                    if (expectedError == null) {
                        // add should succeed
                        try {
                            verifyStatusCode(o.getStatusCode(), HttpURLConnection.HTTP_NO_CONTENT);
                            // store the self link of the added ComputeState instance
                            hostSpec.hostState.documentSelfLink = o
                                    .getResponseHeader(Operation.LOCATION_HEADER);

                            // do some verification of the added ComputeState instance
                            Operation.createGet(host, hostSpec.hostState.documentSelfLink)
                                    .setReferer("/")
                                    .setCompletion((verificationOp, verificationEx) -> {
                                        if (verificationEx != null) {
                                            host.failIteration(verificationEx);
                                            return;
                                        }
                                        try {
                                            ComputeState body = verificationOp.getBody(ComputeState.class);
                                            verifyAddedComputeState(hostSpec.hostState, body);
                                            body.copyTo(addedHost);
                                            addedHost.resourcePoolLink = body.resourcePoolLink;
                                            host.completeIteration();
                                        } catch (IllegalArgumentException ex) {
                                            host.failIteration(ex);
                                        }
                                    }).sendWith(host);
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

        return expectedError != null ? null : addedHost;
    }

    // skip verification and certificates and just add the compute state
    private void directAddHost(ComputeState cs) {
        cs.id = UUID.randomUUID().toString();
        cs.descriptionLink = "no-description-link";
        Operation post = Operation.createPost(host, ComputeService.FACTORY_LINK)
                .setBody(cs)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                    } else {
                        try {
                            verifyStatusCode(o.getStatusCode(), Operation.STATUS_CODE_OK);
                            cs.documentSelfLink = o.getBody(ComputeState.class).documentSelfLink;
                            host.completeIteration();
                        } catch (IllegalStateException ex) {
                            host.failIteration(ex);
                        }
                    }
                });

        host.testStart(1);
        host.send(post);
        host.testWait();
    }

    private void verifyStatusCode(int statusCode, int expectedStatusCode) {
        if (statusCode != expectedStatusCode) {
            String errorMessage = String.format("Expected status code %d but was %d",
                    expectedStatusCode, statusCode);
            throw new IllegalStateException(errorMessage);
        }
    }

    private void verifyAddedComputeState(ComputeState expectedState, ComputeState resultState) {
        AssertUtil.assertNotNull(resultState, "resultState");
        if (expectedState.resourcePoolLink != null) {
            AssertUtil.assertTrue(
                    expectedState.resourcePoolLink.equals(resultState.resourcePoolLink),
                    "resourcePoolLinks don't match");
        }
    }

    private void markHostForVicValidation(ComputeState cs) {
        cs.customProperties.put(ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.VCH.toString());
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
        state.resourcePoolLink = resourcePool.documentSelfLink;
        state.customProperties.put(MockDockerHostAdapterService.CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.DOCKER.toString());
        return state;
    }

    private ComputeState createVicHostState() {
        ComputeState state = createComputeState();
        state.resourcePoolLink = schedulerPlacementZone.documentSelfLink;
        state.customProperties.put(MockDockerHostAdapterService.CONTAINER_HOST_TYPE_PROP_NAME,
                ContainerHostType.VCH.toString());
        return state;
    }

    private ComputeState createComputeState() {
        ComputeState computeState = new ComputeState();
        computeState.address = "https://test-server";
        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                "authCredentialsLink");
        computeState.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());
        computeState.customProperties.put(ContainerHostUtil.PROPERTY_NAME_DRIVER,
                "overlay");

        return computeState;
    }

    private ResourcePoolState createSchedulerPlacementZone() throws Throwable {

        ResourcePoolState placementZone = TestRequestStateFactory.createResourcePool();
        placementZone.id = SCHEDULER_PLACEMENT_ZONE_ID;
        placementZone.name = placementZone.id;
        placementZone.documentSelfLink = ResourcePoolService.FACTORY_LINK + "/" + placementZone.id;

        placementZone.customProperties = new HashMap<>();
        placementZone.customProperties.put(
                PlacementZoneConstants.PLACEMENT_ZONE_TYPE_CUSTOM_PROP_NAME,
                PlacementZoneType.SCHEDULER.toString());

        return doPost(placementZone, ResourcePoolService.FACTORY_LINK);
    }
}
