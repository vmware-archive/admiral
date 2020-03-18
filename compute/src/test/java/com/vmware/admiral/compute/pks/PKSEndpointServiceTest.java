/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.pks;

import static com.vmware.admiral.compute.ContainerHostService.CONTAINER_HOST_TYPE_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME;
import static com.vmware.admiral.compute.cluster.ClusterService.CLUSTER_NAME_CUSTOM_PROP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.pks.PKSConstants;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.CertificateUtilExtended;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.cluster.ClusterService;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterDto;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.pks.PKSEndpointService.Endpoint;
import com.vmware.admiral.compute.pks.PKSEndpointService.Endpoint.PlanSet;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.admiral.service.test.MockKubernetesHostAdapterService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestRequestSender;

public class PKSEndpointServiceTest extends ComputeBaseTest {

    private TestRequestSender sender;

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(PKSEndpointFactoryService.SELF_LINK);
        sender = host.getTestRequestSender();

        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockKubernetesHostAdapterService.class)), new MockKubernetesHostAdapterService());
        waitForServiceAvailability(MockKubernetesHostAdapterService.SELF_LINK);
    }

    @Test
    public void testCreate() {
        Endpoint endpoint = new Endpoint();
        endpoint.apiEndpoint = "http://localhost";
        endpoint.uaaEndpoint = "https://localhost";

        createEndpoint(endpoint);

        endpoint.apiEndpoint = null;
        endpoint.uaaEndpoint = "http://localhost";
        createEndpointExpectFailure(endpoint, ser -> {
            assertEquals(Operation.STATUS_CODE_BAD_REQUEST, ser.statusCode);
            assertEquals("'API endpoint' is required", ser.message);
        });

        endpoint.uaaEndpoint = null;
        endpoint.apiEndpoint = "http://localhost";
        createEndpointExpectFailure(endpoint, ser -> {
            assertEquals(Operation.STATUS_CODE_BAD_REQUEST, ser.statusCode);
            assertEquals("'UAA endpoint' is required", ser.message);
        });

        endpoint.uaaEndpoint = "file://malformed-url";
        endpoint.apiEndpoint = "http://localhost";
        createEndpointExpectFailure(endpoint, ser -> {
            assertEquals(Operation.STATUS_CODE_BAD_REQUEST, ser.statusCode);
            assertTrue(ser.message.startsWith("Unsupported scheme, must be http or https"));
        });
    }

    @Test
    public void testCreateWithPlanAssignments() {
        final String project1 = QueryUtil.PROJECT_IDENTIFIER + "project1";
        final String project2 = QueryUtil.PROJECT_IDENTIFIER + "project2";
        final String plan1InProject1 = "plan1";
        final String plan2InProject1 = "plan2";
        final String plan1InProject2 = "plan1";
        final Set<String> project1Plans = Stream.of(plan1InProject1, plan2InProject1)
                .collect(Collectors.toSet());
        final Set<String> project2Plans = Stream.of(plan1InProject2)
                .collect(Collectors.toSet());
        final PlanSet project1PlanSet = new PlanSet();
        project1PlanSet.plans = project1Plans;
        final PlanSet project2PlanSet = new PlanSet();
        project2PlanSet.plans = project2Plans;

        Endpoint endpoint = new Endpoint();
        endpoint.apiEndpoint = "http://localhost";
        endpoint.uaaEndpoint = "https://localhost";
        endpoint.planAssignments = new HashMap<>();
        endpoint.planAssignments.put(project1, project1PlanSet);
        endpoint.planAssignments.put(project2, project2PlanSet);

        Endpoint createdEndpoint = createEndpoint(endpoint);
        assertNotNull(createdEndpoint);
        assertNotNull(createdEndpoint.planAssignments);
        assertEquals(2, createdEndpoint.planAssignments.size());
        assertPlanAssignmentEntryEquals(project1Plans,
                createdEndpoint.planAssignments.get(project1));
        assertPlanAssignmentEntryEquals(project2Plans,
                createdEndpoint.planAssignments.get(project2));
    }

    @Test
    public void testListFilterByProjectHeader() {
        final String epName1 = "ep-in-project-1";
        final String apiEp1 = "http://localhost:7000";
        final String uaaEp1 = "http://localhost:7001";
        final String projectLink1 = QueryUtil.PROJECT_IDENTIFIER + "project-1";

        final String epName2 = "ep-in-project-2";
        final String apiEp2 = "http://localhost:8000";
        final String uaaEp2 = "http://localhost:8001";
        final String projectLink2 = QueryUtil.PROJECT_IDENTIFIER + "project-2";

        final String epName3 = "ep-no-project";
        final String apiEp3 = "http://localhost:9000";
        final String uaaEp3 = "http://localhost:9001";

        Endpoint endpoint1 = new Endpoint();
        endpoint1.name = epName1;
        endpoint1.apiEndpoint = apiEp1;
        endpoint1.uaaEndpoint = uaaEp1;
        endpoint1.tenantLinks = Collections.singletonList(projectLink1);
        createEndpoint(endpoint1);

        Endpoint endpoint2 = new Endpoint();
        endpoint2.name = epName2;
        endpoint2.apiEndpoint = apiEp2;
        endpoint2.uaaEndpoint = uaaEp2;
        endpoint2.tenantLinks = Collections.singletonList(projectLink2);
        createEndpoint(endpoint2);

        Endpoint endpoint3 = new Endpoint();
        endpoint3.name = epName3;
        endpoint3.apiEndpoint = apiEp3;
        endpoint3.uaaEndpoint = uaaEp3;
        endpoint3.tenantLinks = null;
        createEndpoint(endpoint3);

        assertListConsistsOfEndpointsByName(listEndpoints(null), epName1, epName2, epName3);
        assertListConsistsOfEndpointsByName(listEndpoints(projectLink1), epName1);
        assertListConsistsOfEndpointsByName(listEndpoints(projectLink2), epName2);
        assertListConsistsOfEndpointsByName(
                listEndpoints(QueryUtil.PROJECT_IDENTIFIER + "wrong-project"), (String[]) null);
    }

    @Test
    public void testTrustCertificateIsDeletedOnEndpointDeletion() throws Throwable {
        SslTrustCertificateState trustCert = createSslTrustCert();
        long initialCerts = getTrustCertsCount();

        Endpoint endpoint = new Endpoint();
        endpoint.name = "some-endpoint";
        endpoint.apiEndpoint = "https://localhost:9000";
        endpoint.uaaEndpoint = "https://localhost:9001";
        endpoint.tenantLinks = null;
        endpoint.customProperties = Collections.singletonMap(
                CertificateUtilExtended.CUSTOM_PROPERTY_PKS_UAA_TRUST_CERT_LINK,
                trustCert.documentSelfLink);
        endpoint = createEndpoint(endpoint);

        assertEquals(initialCerts, getTrustCertsCount());

        delete(endpoint.documentSelfLink);
        assertEquals(initialCerts - 1, getTrustCertsCount());

        try {
            delete(trustCert.documentSelfLink);
        } catch (Throwable ex) {
            host.log(Level.WARNING, "Failed to cleanup trust cert: [%s]", Utils.toString(ex));
        }
    }

    private List<Endpoint> listEndpoints(String projectHeader) {
        URI uri = UriUtils.extendUriWithQuery(
                UriUtils.buildUri(host, PKSEndpointFactoryService.SELF_LINK),
                UriUtils.URI_PARAM_ODATA_EXPAND,
                Boolean.toString(true));

        Operation get = Operation.createGet(uri)
                .setReferer("/");

        if (projectHeader != null && !projectHeader.isEmpty()) {
            get.addRequestHeader(OperationUtil.PROJECT_ADMIRAL_HEADER, projectHeader);
        }
        ServiceDocumentQueryResult result = sender.sendAndWait(get,
                ServiceDocumentQueryResult.class);

        assertNotNull(result);
        assertNotNull(result.documents);

        return result.documents.values()
                .stream()
                .map(o -> Utils.fromJson(Utils.toJson(o), Endpoint.class))
                .collect(Collectors.toList());
    }

    private void assertListConsistsOfEndpointsByName(List<Endpoint> endpoints,
            String... endpointNames) {
        if (endpoints == null || endpoints.isEmpty()) {
            assertTrue(
                    "list of endpoints is null or empty but list of expected endpoint names is not empty",
                    endpointNames == null || endpointNames.length == 0);
            return;
        }

        assertNotNull("list of endpoint names is null but list of endpoints is not", endpointNames);
        assertEquals("number of endpoints does not match number of expected endpoint names",
                endpointNames.length, endpoints.size());
        for (String name : endpointNames) {
            assertTrue("list of endpoints does not contain an endpoint with name " + name,
                    endpoints.stream().anyMatch(ep -> name.equals(ep.name)));
        }
    }

    @Test
    public void testUpdate() {
        Endpoint endpoint = new Endpoint();
        endpoint.apiEndpoint = "http://localhost";
        endpoint.uaaEndpoint = "https://localhost";

        final Endpoint endpoint1 = createEndpoint(endpoint);

        final Endpoint patch1 = new Endpoint();
        patch1.documentSelfLink = endpoint1.documentSelfLink;
        patch1.apiEndpoint = "http://localhost";

        updateEndpoint(patch1, (o, r) -> {
            assertEquals(Operation.STATUS_CODE_NOT_MODIFIED, o.getStatusCode());
            assertEquals(patch1.apiEndpoint, r.apiEndpoint);
            assertEquals(endpoint1.uaaEndpoint, r.uaaEndpoint);
        });

        final Endpoint patch2 = new Endpoint();
        patch2.documentSelfLink = endpoint1.documentSelfLink;
        patch2.apiEndpoint = "http://other-host";

        updateEndpoint(patch2, (o, r) -> {
            assertEquals(Operation.STATUS_CODE_OK, o.getStatusCode());
            assertEquals(patch2.apiEndpoint, r.apiEndpoint);
            assertEquals(endpoint1.uaaEndpoint, r.uaaEndpoint);
        });
    }

    @Test
    public void testUpdatePlanAssignments() {
        final String project = QueryUtil.PROJECT_IDENTIFIER + "some-project";
        Set<String> initialPlans = Stream.of("some-plan", "another-plan")
                .collect(Collectors.toSet());
        Set<String> updatedPlans = Collections.singleton("best-plan");

        PlanSet initialPlanSet = new PlanSet();
        initialPlanSet.plans = initialPlans;
        PlanSet updatedPlanSet = new PlanSet();
        updatedPlanSet.plans = updatedPlans;

        Endpoint endpoint = new Endpoint();
        endpoint.apiEndpoint = "http://localhost";
        endpoint.uaaEndpoint = "https://localhost";
        endpoint.planAssignments = new HashMap<>();
        endpoint.planAssignments.put(project, initialPlanSet);

        final Endpoint createdEndpoint = createEndpoint(endpoint);
        assertNotNull(createdEndpoint);
        assertNotNull(createdEndpoint.planAssignments);
        assertEquals(1, createdEndpoint.planAssignments.size());
        assertPlanAssignmentEntryEquals(initialPlans, createdEndpoint.planAssignments.get(project));

        Endpoint patchEndpoint = new Endpoint();
        patchEndpoint.documentSelfLink = createdEndpoint.documentSelfLink;
        patchEndpoint.planAssignments = new HashMap<>();
        patchEndpoint.planAssignments.put(project, updatedPlanSet);

        updateEndpoint(patchEndpoint, (op, updatedEndpoint) -> {
            assertNotNull(updatedEndpoint);
            assertNotNull(updatedEndpoint.planAssignments);
            assertEquals(1, updatedEndpoint.planAssignments.size());
            assertPlanAssignmentEntryEquals(updatedPlans,
                    updatedEndpoint.planAssignments.get(project));
        });
    }

    @Test
    public void testPatchTenantLinksModifiesOnlyProjectLinks() {
        final String tenantLink = UriUtils.buildUriPath(QueryUtil.TENANT_IDENTIFIER, "test-tenant");
        final String projectLink1 = UriUtils.buildUriPath(QueryUtil.PROJECT_IDENTIFIER,
                "test-project-1");
        final String projectLink2 = UriUtils.buildUriPath(QueryUtil.PROJECT_IDENTIFIER,
                "test-project-2");
        final String otherLink = "/other/link";

        Endpoint endpoint = new Endpoint();
        endpoint.apiEndpoint = "http://localhost";
        endpoint.uaaEndpoint = "https://localhost";
        endpoint.tenantLinks = Arrays.asList(tenantLink, projectLink1);

        // first create and endpoint with a tenant and a project link
        final Endpoint createdEndpoint = createEndpoint(endpoint);
        assertNotNull(createdEndpoint);
        assertNotNull(createdEndpoint.tenantLinks);
        assertEquals(2, createdEndpoint.tenantLinks.size());
        assertTrue(createdEndpoint.tenantLinks.contains(tenantLink));
        assertTrue(createdEndpoint.tenantLinks.contains(projectLink1));

        // patch without tenant links. Old tenant links should be preserved
        Endpoint patchEndpoint = new Endpoint();
        patchEndpoint.documentSelfLink = createdEndpoint.documentSelfLink;
        Endpoint updatedEndpoint = updateEndpoint(patchEndpoint, null);
        assertNotNull(updatedEndpoint);
        assertNotNull(updatedEndpoint.tenantLinks);
        assertEquals(2, updatedEndpoint.tenantLinks.size());
        assertTrue(updatedEndpoint.tenantLinks.contains(tenantLink));
        assertTrue(updatedEndpoint.tenantLinks.contains(projectLink1));

        // patch the endpoint with a new project link. The tenant link should be unaffected.
        // The old project should be replaced with the new one.
        patchEndpoint.tenantLinks = Collections.singletonList(projectLink2);
        updatedEndpoint = updateEndpoint(patchEndpoint, null);
        assertNotNull(updatedEndpoint);
        assertNotNull(updatedEndpoint.tenantLinks);
        assertEquals(2, updatedEndpoint.tenantLinks.size());
        assertTrue(updatedEndpoint.tenantLinks.contains(tenantLink));
        assertTrue(updatedEndpoint.tenantLinks.contains(projectLink2));
        assertFalse(updatedEndpoint.tenantLinks.contains(projectLink1));

        // patch the endpoint with a new random link. Projects should be discarded.
        patchEndpoint.tenantLinks = Collections.singletonList(otherLink);
        updatedEndpoint = updateEndpoint(patchEndpoint, null);
        assertNotNull(updatedEndpoint);
        assertNotNull(updatedEndpoint.tenantLinks);
        assertEquals(1, updatedEndpoint.tenantLinks.size());
        assertTrue(updatedEndpoint.tenantLinks.contains(tenantLink));
        assertFalse(updatedEndpoint.tenantLinks.contains(projectLink2));
        assertFalse(updatedEndpoint.tenantLinks.contains(projectLink1));
    }

    @Test
    public void testDelete() throws Throwable {
        Endpoint endpoint = new Endpoint();
        endpoint.apiEndpoint = "http://localhost";
        endpoint.uaaEndpoint = "https://localhost";

        endpoint = createEndpoint(endpoint);

        delete(endpoint.documentSelfLink);
        endpoint = getDocumentNoWait(Endpoint.class, endpoint.documentSelfLink);
        assertNull(endpoint);
    }

    @Test
    public void testDeleteEndpointAndClusters() throws Throwable {
        Endpoint endpoint = new Endpoint();
        endpoint.apiEndpoint = "http://localhost";
        endpoint.uaaEndpoint = "https://localhost";

        endpoint = createEndpoint(endpoint);
        ClusterDto clusterDto = createCluster(endpoint.documentSelfLink);

        delete(endpoint.documentSelfLink);
        endpoint = getDocumentNoWait(Endpoint.class, endpoint.documentSelfLink);
        assertNull(endpoint);

        clusterDto = getDocumentNoWait(ClusterDto.class, clusterDto.documentSelfLink);
        assertNull(endpoint);
    }

    private Endpoint createEndpoint(Endpoint endpoint) {
        Operation o = Operation
                .createPost(host, PKSEndpointFactoryService.SELF_LINK)
                .setBodyNoCloning(endpoint);

        Endpoint result = sender.sendAndWait(o, Endpoint.class);
        assertNotNull(result);
        assertNotNull(result.documentSelfLink);
        assertEquals(endpoint.uaaEndpoint, result.uaaEndpoint);
        assertEquals(endpoint.apiEndpoint, result.apiEndpoint);

        return result;
    }

    private void createEndpointExpectFailure(Endpoint e, Consumer<ServiceErrorResponse> consumer) {
        Operation o = Operation
                .createPost(host, PKSEndpointFactoryService.SELF_LINK)
                .setBodyNoCloning(e);
        TestRequestSender.FailureResponse failure = sender.sendAndWaitFailure(o);
        assertTrue(failure.failure instanceof LocalizableValidationException);
        ServiceErrorResponse errorResponse = failure.op.getBody(ServiceErrorResponse.class);
        assertNotNull(errorResponse);

        consumer.accept(errorResponse);
    }

    private Endpoint updateEndpoint(Endpoint patch, BiConsumer<Operation, Endpoint> consumer) {
        Operation o = Operation
                .createPatch(host, patch.documentSelfLink)
                .setBodyNoCloning(patch);
        o = sender.sendAndWait(o);
        assertNotNull(o);

        Operation get = Operation.createGet(host, patch.documentSelfLink);
        Endpoint e = sender.sendAndWait(get, Endpoint.class);
        assertNotNull(e);

        if (consumer != null) {
            consumer.accept(o, e);
        }

        return e;
    }

    private void assertPlanAssignmentEntryEquals(Set<String> expectedPlans,
            PlanSet actualPlans) {
        if (expectedPlans == null || expectedPlans.isEmpty()) {
            assertTrue("there are no expected plans but some plans were actually returned",
                    actualPlans == null || actualPlans.plans == null
                            || actualPlans.plans.isEmpty());
        }

        assertNotNull("actualPlans are null but plans are expected", actualPlans);
        assertNotNull("actualPlans.plans are null but plans are expected", actualPlans.plans);
        assertEquals("unexpected number of plans", expectedPlans.size(), actualPlans.plans.size());
        expectedPlans.forEach(expectedPlan -> {
            assertTrue("expected plan was not found: " + expectedPlan,
                    actualPlans.plans.stream().anyMatch(plan -> expectedPlan.equals(plan)));
        });
    }

    private ContainerHostSpec createContainerHostSpec(final String endpointLink) {
        ContainerHostSpec clusterSpec = new ContainerHostSpec();
        ComputeService.ComputeState computeState = new ComputeService.ComputeState();
        computeState.tenantLinks = Collections
                .singletonList(UriUtils.buildUriPath(ManagementUriParts.PROJECTS, "test"));
        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put(CLUSTER_NAME_CUSTOM_PROP, "test-cluster");
        computeState.customProperties.put(CONTAINER_HOST_TYPE_PROP_NAME,
                ClusterService.ClusterType.KUBERNETES.name());
        computeState.customProperties.put(HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());
        computeState.customProperties.put(ClusterService.CREATE_EMPTY_CLUSTER_PROP, "true");
        computeState.customProperties.put(ClusterService.ENFORCED_CLUSTER_STATUS_PROP,
                ClusterService.ClusterStatus.PROVISIONING.name());
        computeState.customProperties.put(PKSConstants.PKS_ENDPOINT_PROP_NAME,
                endpointLink);

        clusterSpec.hostState = computeState;

        return clusterSpec;
    }

    private ClusterDto createCluster(String endpointLink) {
        ContainerHostSpec hostSpec = createContainerHostSpec(endpointLink);

        ArrayList<ClusterDto> result = new ArrayList<>(1);

        Operation create = Operation.createPost(host, ClusterService.SELF_LINK)
                .setReferer(host.getUri())
                .setBody(hostSpec)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to create cluster: %s", Utils.toString(ex));
                        host.failIteration(ex);
                    } else {
                        try {
                            result.add(o.getBody(ClusterDto.class));
                            host.completeIteration();
                        } catch (Throwable er) {
                            host.log(Level.SEVERE,
                                    "Failed to retrieve created cluster DTO from response: %s",
                                    Utils.toString(er));
                            host.failIteration(er);
                        }
                    }
                });

        host.testStart(1);
        host.send(create);
        host.testWait();

        assertEquals(1, result.size());
        ClusterDto dto = result.iterator().next();
        assertNotNull(dto);
        return dto;
    }

    private SslTrustCertificateState createSslTrustCert() throws Throwable {
        String sslTrustPem = CommonTestStateFactory.getFileContent("certs/ca.pem").trim();
        SslTrustCertificateState sslTrustCert = new SslTrustCertificateState();
        sslTrustCert.certificate = sslTrustPem;

        sslTrustCert = doPost(sslTrustCert, SslTrustCertificateService.FACTORY_LINK);
        return sslTrustCert;
    }

    private long getTrustCertsCount() throws Throwable {
        Operation get = Operation.createGet(host, SslTrustCertificateService.FACTORY_LINK)
                .setReferer(host.getUri());

        return host.sendWithDeferredResult(get, ServiceDocumentQueryResult.class)
                .toCompletionStage()
                .toCompletableFuture()
                .get().documentCount;
    }

}
