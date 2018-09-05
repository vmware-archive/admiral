/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.pks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.vmware.admiral.adapter.pks.entities.PKSCluster;
import com.vmware.admiral.adapter.pks.entities.PKSPlan;
import com.vmware.admiral.adapter.pks.entities.UAATokenResponse;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.compute.kubernetes.entities.config.KubeConfig;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.test.VerificationHost;

public class PKSRemoteClientServiceTest {

    private static ServiceHost host;

    @BeforeClass
    public static void before() throws Throwable {
        host = createHost();
    }

    @Before
    public void setUp() throws Exception {
        try {
            PKSRemoteClientService.getInstance().stop();
        } catch (Exception ignored) {
        }
    }

    private static VerificationHost createHost() throws Throwable {
        ServiceHost.Arguments args = new ServiceHost.Arguments();
        args.sandbox = null; // ask runtime to pick a random storage location
        args.port = 0; // ask runtime to pick a random port
        args.isAuthorizationEnabled = false;

        VerificationHost h = new VerificationHost();
        h = VerificationHost.initialize(h, args);
        h.start();

        return h;
    }

    @Test
    public void testCreateInstance() {
        try {
            PKSRemoteClientService.getInstance();
            Assert.fail("should not reach here");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }

        ServerX509TrustManager trustManager = ServerX509TrustManager.init(host);
        PKSRemoteClientService client = new PKSRemoteClientService(trustManager, host);
        assertNotNull(client);

        try {
            new PKSRemoteClientService(null, null);
            Assert.fail("should not reach here");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }

        PKSRemoteClientService client2 = PKSRemoteClientService.getInstance();
        assertSame(client, client2);
    }

    @Test
    public void testStop() {
        PKSRemoteClientService client = new PKSRemoteClientService(null, null);
        client.stop();
        try {
            PKSRemoteClientService.getInstance();
            fail("should not reach here");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }
    }

    @Test
    public void testMaintenance() throws Throwable {
        PKSRemoteClientService client = new PKSRemoteClientService(null, null);
        assertNotNull(client);

        ServiceClient mockClient = mockClient(client);
        ArgumentCaptor<Operation> valueCapture = ArgumentCaptor.forClass(Operation.class);

        Operation op = Operation.createGet(host, "/");
        client.handleMaintenance(op);
        verify(mockClient, times(1)).handleMaintenance(any(Operation.class));
    }

    @Test
    public void testGetClusters() throws Throwable {
        PKSRemoteClientService client = new PKSRemoteClientService(null, host);
        assertNotNull(client);

        PKSContext ctx = new PKSContext();
        ctx.pksAPIUri = URI.create("http://some.host");

        ServiceClient mockClient = mockClient(client);
        ArgumentCaptor<Operation> valueCapture = ArgumentCaptor.forClass(Operation.class);
        doNothing().when(mockClient).send(valueCapture.capture());

        // test casual exception
        DeferredResult<List<PKSCluster>> result = client.getClusters(null);
        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof NullPointerException);
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
        verify(mockClient, never()).send(any(Operation.class));

        // test straight forward case
        result = client.getClusters(ctx);

        Operation op = valueCapture.getValue();
        op.setBodyNoCloning("[{\"name\": \"cluster1\",\"plan_name\": \"small\",\"last_action\": \"CREATE\",\"last_action_state\": \"succeeded\",\"last_action_description\": \"Instance provisioning completed\",\"uuid\": \"3925786b-4b09-4f63-a1f8-50b706b38ced\",\"kubernetes_master_ips\": [\"30.0.1.2\"],\"parameters\": {\"kubernetes_master_host\": \"192.168.150.100\",\"kubernetes_master_port\": 8443,\"worker_haproxy_ip_addresses\": null,\"kubernetes_worker_instances\": 2,\"authorization_mode\": null}}]");
        op.complete();

        List<PKSCluster> pksClusters = result.toCompletionStage().toCompletableFuture().get();
        assertNotNull(pksClusters);
        assertEquals(1, pksClusters.size());
        assertEquals("cluster1", pksClusters.get(0).name);

        result = client.getClusters(ctx);
        op = valueCapture.getValue();
        op.fail(new Exception("custom-err"));

        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException e) {
            assertEquals("custom-err", e.getCause().getMessage());
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    public void testGetCluster() throws Throwable {
        PKSRemoteClientService client = new PKSRemoteClientService(null, host);
        assertNotNull(client);

        PKSContext ctx = new PKSContext();
        ctx.pksAPIUri = URI.create("http://some.host");

        ServiceClient mockClient = mockClient(client);
        ArgumentCaptor<Operation> valueCapture = ArgumentCaptor.forClass(Operation.class);
        doNothing().when(mockClient).send(valueCapture.capture());

        // test casual exception
        DeferredResult<PKSCluster> result = client.getCluster(null, null);
        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof NullPointerException);
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
        verify(mockClient, never()).send(any(Operation.class));

        // test straight forward case
        result = client.getCluster(ctx, "clus ter1");

        Operation op = valueCapture.getValue();
        assertTrue(op.getUri().getPath().endsWith("/clus%20ter1"));
        op.setBodyNoCloning("{\"name\":\"clus ter1\",\"plan_name\":\"small\",\"last_action\":\"CREATE\",\"last_action_state\":\"succeeded\",\"last_action_description\":\"Instance provisioning completed\",\"uuid\":\"3925786b-4b09-4f63-a1f8-50b706b38ced\",\"kubernetes_master_ips\":[\"30.0.1.2\"],\"parameters\":{\"kubernetes_master_host\":\"192.168.150.100\",\"kubernetes_master_port\":8443,\"worker_haproxy_ip_addresses\":null,\"kubernetes_worker_instances\":2,\"authorization_mode\":null}}");
        op.complete();

        PKSCluster pksCluster = result.toCompletionStage().toCompletableFuture().get();
        assertNotNull(pksCluster);
        assertEquals("clus ter1", pksCluster.name);

        // test exceptionally clause
        result = client.getCluster(ctx, "cluster-missing");
        op = valueCapture.getValue();
        op.fail(new Exception("custom-err"));

        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException e) {
            assertEquals("custom-err", e.getCause().getMessage());
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    public void testGetPlans() throws Throwable {
        PKSRemoteClientService client = new PKSRemoteClientService(null, host);
        assertNotNull(client);

        PKSContext ctx = new PKSContext();
        ctx.pksAPIUri = URI.create("http://some.host");

        ServiceClient mockClient = mockClient(client);
        ArgumentCaptor<Operation> valueCapture = ArgumentCaptor.forClass(Operation.class);
        doNothing().when(mockClient).send(valueCapture.capture());

        // test casual exception
        DeferredResult<List<PKSPlan>> result = client.getPlans(null);
        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof NullPointerException);
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
        verify(mockClient, never()).send(any(Operation.class));

        // test straight forward case
        result = client.getPlans(ctx);

        Operation op = valueCapture.getValue();
        op.setBodyNoCloning("[{\"id\":\"8A0E21A8-8072-4D80-B365-D1F502085560\",\"name\":\"small\",\"description\":\"Example: This plan will configure a lightweight kubernetes cluster. Not recommended for production workloads.\",\"worker_instances\":3}]");
        op.complete();

        List<PKSPlan> pksPlans = result.toCompletionStage().toCompletableFuture().get();
        assertNotNull(pksPlans);
        assertEquals(1, pksPlans.size());
        assertEquals("small", pksPlans.get(0).name);

        // test exceptionally clause
        result = client.getPlans(ctx);
        op = valueCapture.getValue();
        op.fail(new Exception("custom-err"));

        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException e) {
            assertEquals("custom-err", e.getCause().getMessage());
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    public void testCreateUser() throws Throwable {
        PKSRemoteClientService client = new PKSRemoteClientService(null, host);
        assertNotNull(client);

        PKSContext ctx = new PKSContext();
        ctx.pksAPIUri = URI.create("http://some.host");

        ServiceClient mockClient = mockClient(client);
        ArgumentCaptor<Operation> valueCapture = ArgumentCaptor.forClass(Operation.class);
        doNothing().when(mockClient).send(valueCapture.capture());

        // test casual exception
        DeferredResult<KubeConfig> result = client.createUser(null, null);
        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof NullPointerException);
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
        verify(mockClient, never()).send(any(Operation.class));

        // test straight forward case
        result = client.createUser(ctx, "cluster1");

        Operation op = valueCapture.getValue();
        op.setBodyNoCloning("{\"users\": [{\"name\": \"u\",\"user\": {\"token\": \"tok\"}}]}");
        op.complete();

        KubeConfig kubeConfig = result.toCompletionStage().toCompletableFuture().get();
        assertNotNull(kubeConfig);
        assertEquals("u", kubeConfig.users.get(0).name);
        assertEquals("tok", kubeConfig.users.get(0).user.token);

        // test exceptionally clause
        result = client.createUser(ctx, "cluster-missing");
        op = valueCapture.getValue();
        op.fail(new Exception("custom-err"));

        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException e) {
            assertEquals("custom-err", e.getCause().getMessage());
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    public void testLogin() throws Throwable {
        PKSRemoteClientService client = new PKSRemoteClientService(null, host);
        assertNotNull(client);

        ServiceClient mockClient = mockClient(client);
        ArgumentCaptor<Operation> valueCapture = ArgumentCaptor.forClass(Operation.class);
        doNothing().when(mockClient).sendRequest(valueCapture.capture());

        // test casual exception
        DeferredResult<UAATokenResponse> result = client.login(null, null, null);
        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof NullPointerException);
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
        verify(mockClient, never()).send(any(Operation.class));

        // test straight forward case
        result = client.login("http://uaa.host", "user", "pass");

        Operation op = valueCapture.getValue();
        assertTrue(op.getBodyRaw() instanceof String);
        assertEquals(Operation.MEDIA_TYPE_APPLICATION_X_WWW_FORM_ENCODED, op.getContentType());
        assertTrue(op.isRemote());

        op.setBodyNoCloning("{\"access_token\":\"acc-tok\",\"token_type\":\"bearer\",\"refresh_token\":\"ref-tok\",\"expires_in\":59,\"scope\":\"pks.clusters.admin\",\"jti\":\"cb\"}");
        op.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);
        op.complete();

        UAATokenResponse token = result.toCompletionStage().toCompletableFuture().get();
        assertNotNull(token);
        assertEquals("acc-tok", token.accessToken);
        assertEquals("ref-tok", token.refreshToken);
        assertEquals("59", token.expiresIn);

        // test exceptionally clause
        result = client.login("http://uaa.host", "user", "pass");
        op = valueCapture.getValue();
        op.fail(new Exception("custom-err"));

        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException e) {
            assertEquals("custom-err", e.getCause().getMessage());
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());

        // test exceptionally case with invalid response
        result = client.login("http://uaa.host", "user", "pass");

        op = valueCapture.getValue();

        op.setBodyNoCloning("-invalid-");
        op.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);
        op.complete();

        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException ignored) {
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    public void testDeleteCluster() throws Throwable {
        PKSRemoteClientService client = new PKSRemoteClientService(null, host);
        assertNotNull(client);

        PKSContext ctx = new PKSContext();
        ctx.pksAPIUri = URI.create("http://some.host");

        ServiceClient mockClient = mockClient(client);
        ArgumentCaptor<Operation> valueCapture = ArgumentCaptor.forClass(Operation.class);
        doNothing().when(mockClient).send(valueCapture.capture());

        // test casual exception
        DeferredResult<Void> result = client.deleteCluster(null, null);
        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof NullPointerException);
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
        verify(mockClient, never()).send(any(Operation.class));

        // test straight forward case
        result = client.deleteCluster(ctx, "some-cluster");
        Operation op = valueCapture.getValue();
        op.setStatusCode(HttpURLConnection.HTTP_NO_CONTENT);
        op.complete();

        CompletableFuture<Void> future = result.toCompletionStage().toCompletableFuture();
        future.get();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());

        result = client.deleteCluster(ctx, "some-cluster");
        op = valueCapture.getValue();
        op.setStatusCode(Operation.STATUS_CODE_OK).complete();
        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException ignored) {
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());

        result = client.deleteCluster(ctx, "some-cluster");
        op = valueCapture.getValue();
        op.fail(Operation.STATUS_CODE_BAD_REQUEST);
        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException ignored) {
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());

        result = client.deleteCluster(ctx, "some-cluster");
        op = valueCapture.getValue();
        op.fail(new Exception("custom-err"));
        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException e) {
            assertEquals("custom-err", e.getCause().getMessage());
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    public void testCreateCluster() throws Throwable {
        PKSRemoteClientService client = new PKSRemoteClientService(null, host);
        assertNotNull(client);

        PKSContext ctx = new PKSContext();
        ctx.pksAPIUri = URI.create("http://some.host");

        ServiceClient mockClient = mockClient(client);
        ArgumentCaptor<Operation> valueCapture = ArgumentCaptor.forClass(Operation.class);
        doNothing().when(mockClient).send(valueCapture.capture());

        // test casual exception
        DeferredResult<PKSCluster> result = client.createCluster(null, null);
        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof NullPointerException);
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
        verify(mockClient, never()).send(any(Operation.class));

        // test straight forward case
        PKSCluster cluster = new PKSCluster();
        result = client.createCluster(ctx, cluster);
        Operation op = valueCapture.getValue();
        op.setBodyNoCloning(cluster).setStatusCode(Operation.STATUS_CODE_ACCEPTED).complete();

        CompletableFuture<PKSCluster> future = result.toCompletionStage().toCompletableFuture();
        future.get();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertFalse(future.isCompletedExceptionally());

        result = client.createCluster(ctx, cluster);
        op = valueCapture.getValue();
        op.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST).complete();
        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException e) {
            PKSException pe = (PKSException) e.getCause();
            assertEquals(Operation.STATUS_CODE_BAD_REQUEST, pe.getErrorCode());
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    public void testResizeCluster() throws Throwable {
        PKSRemoteClientService client = new PKSRemoteClientService(null, host);
        assertNotNull(client);

        PKSContext ctx = new PKSContext();
        ctx.pksAPIUri = URI.create("http://some.host");

        ServiceClient mockClient = mockClient(client);
        ArgumentCaptor<Operation> valueCapture = ArgumentCaptor.forClass(Operation.class);
        doNothing().when(mockClient).send(valueCapture.capture());

        // test casual exception
        DeferredResult<Void> result = client.resizeCluster(null, null);
        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof NullPointerException);
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
        verify(mockClient, never()).send(any(Operation.class));

        // test straight forward case
        PKSCluster cluster = new PKSCluster();
        cluster.name = "name";
        result = client.resizeCluster(ctx, cluster);
        Operation op = valueCapture.getValue();
        op.setBodyNoCloning(cluster).setStatusCode(Operation.STATUS_CODE_ACCEPTED).complete();

        CompletableFuture<Void> future = result.toCompletionStage().toCompletableFuture();
        future.get();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());

        result = client.resizeCluster(ctx, cluster);
        op = valueCapture.getValue();
        op.setStatusCode(Operation.STATUS_CODE_OK).complete();
        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException ignored) {
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());

        result = client.resizeCluster(ctx, cluster);
        op = valueCapture.getValue();
        op.fail(new Exception("custom-err"));
        try {
            result.toCompletionStage().toCompletableFuture().get();
            fail("should not reach here");
        } catch (ExecutionException e) {
            assertEquals("custom-err", e.getCause().getMessage());
        }
        assertTrue(result.toCompletionStage().toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    public void logout() {
    }

    @Test
    public void handleMaintenance() {
    }

    private ServiceClient mockClient(PKSRemoteClientService client) throws Throwable {
        ServiceClient mockServiceClient = mock(ServiceClient.class);

        Field f = PKSRemoteClientService.class.getDeclaredField("serviceClient");
        f.setAccessible(true);

        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(f, f.getModifiers() & ~Modifier.FINAL);

        f.set(client, mockServiceClient);

        return mockServiceClient;
    }

}