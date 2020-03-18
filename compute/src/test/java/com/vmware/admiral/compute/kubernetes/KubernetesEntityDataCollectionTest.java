/*
 * Copyright (c) 2017-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.kubernetes;

import static com.vmware.admiral.compute.kubernetes.KubernetesEntityDataCollection.DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_LINK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.KubernetesEntityDataCollection.EntityListCallback;
import com.vmware.admiral.compute.kubernetes.KubernetesEntityDataCollection.KubernetesEntityDataCollectionState;
import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;
import com.vmware.admiral.compute.kubernetes.entities.common.ObjectMeta;
import com.vmware.admiral.compute.kubernetes.service.BaseKubernetesState;
import com.vmware.admiral.compute.kubernetes.service.DeploymentService.DeploymentState;
import com.vmware.admiral.compute.kubernetes.service.PodFactoryService;
import com.vmware.admiral.compute.kubernetes.service.PodService.PodState;
import com.vmware.admiral.compute.kubernetes.service.ReplicationControllerService.ReplicationControllerState;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityFactoryHandler;
import com.vmware.admiral.compute.kubernetes.service.ServiceEntityHandler.ServiceState;
import com.vmware.admiral.service.test.MockKubernetesAdapterService;
import com.vmware.admiral.service.test.MockKubernetesHostAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.common.test.TestRequestSender;
import com.vmware.xenon.services.common.QueryTask;

public class KubernetesEntityDataCollectionTest extends ComputeBaseTest {
    private static final String COMPUTE_HOST_ID = "k8s-host";
    private static final String COMPUTE_HOST_LINK = UriUtils.buildUriPath(
            ComputeService.FACTORY_LINK, COMPUTE_HOST_ID);
    private static List<String> forDelete = new ArrayList<>();
    private EntityListCallback listCallback;

    @Before
    public void setUp() throws Throwable {
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockKubernetesAdapterService.class)), new MockKubernetesAdapterService());
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockKubernetesHostAdapterService.class)), new MockKubernetesHostAdapterService());

        waitForServiceAvailability(
                ComputeService.FACTORY_LINK,
                ComputeDescriptionService.FACTORY_LINK,
                KubernetesEntityDataCollection.DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_LINK,
                PodFactoryService.SELF_LINK,
                MockKubernetesAdapterService.SELF_LINK,
                MockKubernetesHostAdapterService.SELF_LINK
        );

        listCallback = new EntityListCallback();
        listCallback.computeHostLink = COMPUTE_HOST_LINK;

        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc = doPost(computeDesc, ComputeDescriptionService.FACTORY_LINK);

        ComputeState cs = new ComputeState();
        cs.id = COMPUTE_HOST_ID;
        cs.documentSelfLink = COMPUTE_HOST_ID;
        cs.descriptionLink = computeDesc.documentSelfLink;
        cs.customProperties = new HashMap<>();

        doPost(cs, ComputeService.FACTORY_LINK);
    }

    @After
    public void tearDown() {
        MockKubernetesAdapterService.clearKubernetesEntities();
        if (forDelete.size() != 0) {
            host.testStart(forDelete.size());
            forDelete.forEach(link -> host.sendRequest(
                    Operation
                            .createDelete(UriUtils.buildUri(host, link))
                            .setReferer(host.getUri())
                            .setCompletion(host.getCompletion())
            ));
            forDelete.clear();
            host.testWait();
        }
    }

    private synchronized void addForDelete(String link) {
        forDelete.add(link);
    }

    private void startDataCollectionAndWait() throws Throwable {
        host.testStart(1);
        host.sendRequest(Operation
                .createPatch(UriUtils.buildUri(host,
                        KubernetesEntityDataCollection
                                .DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_LINK))
                .setBody(listCallback)
                .setReferer(host.getUri())
                .setCompletion(host.getCompletion()));
        host.testWait();
        waitForDataCollectionToFinish();
    }

    private void waitForDataCollectionToFinish() throws Throwable {
        AtomicBoolean finished = new AtomicBoolean();

        String dataCollectionLink = KubernetesEntityDataCollection.DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_LINK;
        waitFor(() -> {
            ServiceDocumentQuery<KubernetesEntityDataCollectionState> query = new ServiceDocumentQuery<>(
                    host, KubernetesEntityDataCollectionState.class);
            query.queryDocument(dataCollectionLink, (r) -> {
                if (r.hasException()) {
                    host.log(
                            "Exception while retrieving default host network list data collection: "
                                    + (r.getException() instanceof CancellationException
                                    ? r.getException().getMessage()
                                    : Utils.toString(r.getException())));
                    finished.set(true);
                } else if (r.hasResult()) {
                    if (r.getResult().computeHostLinks.isEmpty()) {
                        finished.set(true);
                    }
                }
            });
            return finished.get();
        });
    }

    private <T extends BaseKubernetesState> List<T> getEntities(Class<T> tClass)
            throws Throwable {
        List<T> entitiesFound = new ArrayList<>();
        TestContext ctx = testCreate(1);
        ServiceDocumentQuery<T> query = new ServiceDocumentQuery<>(host, tClass);

        QueryTask queryTask = QueryUtil.buildPropertyQuery(tClass,
                BaseKubernetesState.FIELD_NAME_PARENT_LINK, COMPUTE_HOST_LINK);
        QueryUtil.addExpandOption(queryTask);
        QueryUtil.addBroadcastOption(queryTask);

        query.query(queryTask, (r) -> {
            if (r.hasException()) {
                host.log("Exception while retrieving ContainerNetworkState: "
                        + (r.getException() instanceof CancellationException
                        ? r.getException().getMessage()
                        : Utils.toString(r.getException())));
                ctx.fail(r.getException());
            } else if (r.hasResult()) {
                entitiesFound.add(r.getResult());
            } else {
                ctx.complete();
            }
        });
        ctx.await();
        return entitiesFound;
    }

    private BaseKubernetesState makeEntity(String id, String name, String type,
            String compositeId) {
        BaseKubernetesState result = KubernetesUtil.createKubernetesEntityState(type);
        if (result == null) {
            return null;
        }
        result.id = id;
        result.name = name;
        result.parentLink = COMPUTE_HOST_LINK;
        ObjectMeta metadata = new ObjectMeta();
        metadata.name = name;
        metadata.selfLink = "test";
        metadata.namespace = "test";
        metadata.uid = id;
        BaseKubernetesObject object = new BaseKubernetesObject();
        object.metadata = metadata;
        object.kind = type;
        if (compositeId != null) {
            object.metadata.labels = new HashMap<>();
            object.metadata.labels.put(KubernetesUtil.KUBERNETES_LABEL_APP_ID, compositeId);
        }
        result.setKubernetesEntityFromJson(Utils.toJson(object));
        return result;
    }

    @Test
    public void testPost() {
        TestRequestSender sender = host.getTestRequestSender();
        TestRequestSender.FailureResponse failureResponse = sender.sendAndWaitFailure(Operation
                .createPost(host, DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_LINK));
        assertTrue(IllegalArgumentException.class.equals(failureResponse.failure.getClass()));

        failureResponse = sender.sendAndWaitFailure(Operation
                .createPost(host, DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_LINK)
                .setBodyNoCloning(new KubernetesEntityDataCollectionState()));
        assertTrue(LocalizableValidationException.class.equals(failureResponse.failure.getClass()));
        assertTrue(failureResponse.failure.getMessage().startsWith("Only one instance"));

        KubernetesEntityDataCollectionState dc = new KubernetesEntityDataCollectionState();
        dc.documentSelfLink = DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_LINK;
        Operation response = sender.sendAndWait(Operation
                .createPost(host, DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_LINK)
                .setBodyNoCloning(dc));
        assertTrue(Operation.STATUS_CODE_OK == response.getStatusCode());
    }

    @Test
    public void testPut() throws Throwable {
        TestRequestSender sender = host.getTestRequestSender();
        Operation response = sender.sendAndWait(Operation
                .createPut(host, DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)
                .setBodyNoCloning(new KubernetesEntityDataCollectionState()));
        assertTrue(Operation.STATUS_CODE_OK == response.getStatusCode());

        TestRequestSender.FailureResponse failureResponse = sender.sendAndWaitFailure(Operation
                .createPut(host, DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_LINK));
        assertTrue(IllegalArgumentException.class.equals(failureResponse.failure.getClass()));

        response = sender.sendAndWait(Operation
                .createPut(host, DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_LINK)
                .setBodyNoCloning(new KubernetesEntityDataCollectionState()));
        assertTrue(Operation.STATUS_CODE_OK == response.getStatusCode());
    }

    @Test
    public void testDiscoverSingleEntity() throws Throwable {
        BaseKubernetesState entity = new PodState();
        entity.name = "entity";
        entity.id = "id";
        MockKubernetesAdapterService
                .addEntity(makeEntity(entity.id, entity.name, KubernetesUtil.POD_TYPE, null));

        startDataCollectionAndWait();

        List<PodState> entities = getEntities(PodState.class);
        Assert.assertEquals(1, entities.size());
        Assert.assertEquals(entity.id, entities.get(0).id);
        Assert.assertEquals(entity.name, entities.get(0).name);
    }

    @Test
    public void testRequestWithNoHostLink() {
        EntityListCallback request = new EntityListCallback();
        host.testStart(1);
        host.sendRequest(
                Operation.createPatch(UriUtils.buildUri(host, KubernetesEntityDataCollection
                        .DEFAULT_KUBERNETES_ENTITY_DATA_COLLECTION_LINK))
                        .setBody(request)
                        .setReferer(host.getUri())
                        .setCompletion(host.getCompletion()));
        host.testWait();
    }

    @Test
    public void testDiscoverMultipleEntities() throws Throwable {
        List<BaseKubernetesState> testEntities = new ArrayList<>();
        testEntities.add(makeEntity("pod-1", "my_prog_1", KubernetesUtil.POD_TYPE, null));
        testEntities.add(makeEntity("pod-2", "name-for-pod", KubernetesUtil.POD_TYPE, null));
        testEntities.add(makeEntity("no-name", null, KubernetesUtil.POD_TYPE, null));
        testEntities.add(makeEntity("ser-1", "my_service_1", KubernetesUtil.SERVICE_TYPE, null));
        testEntities.add(makeEntity("depl-1", "my_app_1", KubernetesUtil.DEPLOYMENT_TYPE, null));

        testEntities.forEach(MockKubernetesAdapterService::addEntity);
        startDataCollectionAndWait();

        List<PodState> pods = getEntities(PodState.class);
        List<ServiceState> services = getEntities(ServiceState.class);
        List<DeploymentState> deployments = getEntities(DeploymentState.class);

        Assert.assertEquals(2, pods.size());
        Assert.assertEquals(1, services.size());
        Assert.assertEquals(1, deployments.size());
    }

    @Test
    public void testDiscoveredEntitiesWillNotDuplicateExisting() throws Throwable {
        BaseKubernetesState pod = new PodState();
        pod.id = "pod-1";
        pod.name = "my_prog_1";

        List<BaseKubernetesState> testEntities = new ArrayList<>();
        testEntities.add(makeEntity(pod.id, pod.name, KubernetesUtil.POD_TYPE, null));
        testEntities.add(makeEntity("service-2", "bread-baker", KubernetesUtil.SERVICE_TYPE, null));
        testEntities.add(makeEntity("rc-1", "test-controller",
                KubernetesUtil.REPLICATION_CONTROLLER_TYPE, null));

        PodState existingPod = new PodState();
        existingPod.id = pod.id;
        existingPod.name = pod.name + "_second";
        existingPod.parentLink = COMPUTE_HOST_LINK;
        existingPod.documentSelfLink = existingPod.id;

        ServiceState existingService = new ServiceState();
        existingService.id = "service-2";
        existingService.name = "my-test-service";
        existingService.parentLink = COMPUTE_HOST_LINK;
        existingService.documentSelfLink = existingService.id;

        host.testStart(2);
        host.sendRequest(
                Operation.createPost(UriUtils.buildUri(host, PodFactoryService.SELF_LINK))
                        .setBody(existingPod)
                        .setReferer(host.getUri())
                        .setCompletion((o, ex) -> {
                            if (ex != null) {
                                host.failIteration(ex);
                            } else {
                                addForDelete(o.getBody(ResourceState.class).documentSelfLink);
                                host.completeIteration();
                            }
                        }));
        host.sendRequest(
                Operation.createPost(UriUtils.buildUri(host, ServiceEntityFactoryHandler.SELF_LINK))
                        .setBody(existingService)
                        .setReferer(host.getUri())
                        .setCompletion((o, ex) -> {
                            if (ex != null) {
                                host.failIteration(ex);
                            } else {
                                addForDelete(o.getBody(ResourceState.class).documentSelfLink);
                                host.completeIteration();
                            }
                        }));
        host.testWait();

        testEntities.forEach(MockKubernetesAdapterService::addEntity);
        startDataCollectionAndWait();
        List<PodState> pods = getEntities(PodState.class);
        List<ServiceState> services = getEntities(ServiceState.class);
        List<ReplicationControllerState> rcs = getEntities(ReplicationControllerState.class);

        Assert.assertEquals(1, pods.size());
        // Only service-2 is listed, so service-1 will be deleted
        Assert.assertEquals(1, services.size());
        Assert.assertEquals(1, rcs.size());
    }

    @Test
    public void testDataCollectionDeleteMissingState() throws Throwable {
        BaseKubernetesState pod = new PodState();
        pod.id = "pod-1";
        pod.name = "my_prog_1";

        PodState existingPod = new PodState();
        existingPod.id = pod.id;
        existingPod.name = pod.name + "_second";
        existingPod.parentLink = COMPUTE_HOST_LINK;
        existingPod.documentSelfLink = existingPod.id;

        host.testStart(1);
        host.sendRequest(
                Operation.createPost(UriUtils.buildUri(host, PodFactoryService.SELF_LINK))
                        .setBody(existingPod)
                        .setReferer(host.getUri())
                        .setCompletion((o, ex) -> {
                            if (ex != null) {
                                host.failIteration(ex);
                            } else {
                                addForDelete(o.getBody(ResourceState.class).documentSelfLink);
                                host.completeIteration();
                            }
                        }));
        host.testWait();

        startDataCollectionAndWait();

        List<PodState> pods = getEntities(PodState.class);
        Assert.assertEquals(0, pods.size());
    }

    @Test
    public void testDataCollectionDiscoverAdmiralApplication() throws Throwable {
        String testCompositeId = "test-composite-id";
        List<BaseKubernetesState> testEntities = new ArrayList<>();
        testEntities
                .add(makeEntity("pod-1", "my_prog_1", KubernetesUtil.POD_TYPE, testCompositeId));
        testEntities
                .add(makeEntity("pod-2", "name-for-pod", KubernetesUtil.POD_TYPE, testCompositeId));
        testEntities.add(makeEntity("ser-1", "my_service_1", KubernetesUtil.SERVICE_TYPE,
                testCompositeId));
        testEntities.add(makeEntity("depl-1", "my_app_1", KubernetesUtil.DEPLOYMENT_TYPE,
                testCompositeId));

        testEntities.forEach(MockKubernetesAdapterService::addEntity);
        startDataCollectionAndWait();

        CompositeComponent compositeComponent = getDocument(CompositeComponent.class,
                "/resources/composite-components/" + testCompositeId);

        assertNotNull(compositeComponent);
        assertEquals(4, compositeComponent.componentLinks.size());

    }
}
