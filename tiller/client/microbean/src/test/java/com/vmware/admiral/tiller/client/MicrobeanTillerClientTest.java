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

package com.vmware.admiral.tiller.client;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.stub.StreamObserver;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.microbean.helm.Tiller;

import com.vmware.admiral.tiller.client.MicrobeanTillerClient;
import com.vmware.admiral.tiller.client.TillerClientException;
import com.vmware.admiral.tiller.client.TillerConfig;

public class MicrobeanTillerClientTest {

    @Rule
    public Timeout timeout = new Timeout(10, TimeUnit.SECONDS);

    private MicrobeanTillerClient tillerClient;

    @Before
    public void setupTillerClient() {
        tillerClient = createTestTillerClient();
    }

    @Test
    public void testHealthCheck() throws Throwable {
        tillerClient.healthCheck().get();

        MicrobeanTillerClient brokenClient = createTestTillerClient(false);
        try {
            brokenClient.healthCheck().get();
            fail("Health check on a broken MicrobeanTillerClient should have failed");
        } catch (ExecutionException ex) {
            assertNotNull("Healthcheck should have failed", ex);
            assertNotNull("ex.getCause() == null", ex.getCause());
            final String expectedMessage = String.format(
                    MicrobeanTillerClient.HEALTH_CHECK_STATUS_MESSAGE_FORMAT,
                    ServingStatus.NOT_SERVING.toString());
            assertEquals(
                    "Unexpected exception message",
                    expectedMessage,
                    ex.getCause().getMessage());
        }
    }

    @Test
    public void testClose() {
        assertFalse("client should not be closed after initialization", tillerClient.isClosed());
        tillerClient.close();
        assertTrue("client should be closed after a call to close()", tillerClient.isClosed());
        try {
            tillerClient.healthCheck();
            fail("Should have failed to execute health check after the client was closed");
        } catch (TillerClientException ex) {
            assertEquals(MicrobeanTillerClient.CLIENT_IS_CLOSED_ERROR_MESSAGE, ex.getMessage());
        }
    }

    @Test
    public void testCreateUnaryCallStreamObserverForCompletableFuture() throws Throwable {
        CompletableFuture<String> completableFuture;
        StreamObserver<String> streamObserver;
        String value;

        // 1. test successful future completion
        completableFuture = new CompletableFuture<>();
        streamObserver = MicrobeanTillerClient
                .createUnaryCallStreamObserverForCompletableFuture(completableFuture);

        value = UUID.randomUUID().toString();
        streamObserver.onNext(value);
        // we haven't called onComplete yet
        assertFalse("CompletableFuture should not be done yet", completableFuture.isDone());
        assertNull("CompletableFuture.get() should still be null", completableFuture.getNow(null));
        streamObserver.onCompleted();
        assertTrue("CompletableFuture should have completed already", completableFuture.isDone());
        assertFalse("CompletableFuture should not have completed exceptionally",
                completableFuture.isCompletedExceptionally());
        assertFalse("CompletableFuture should not have been canceled",
                completableFuture.isCancelled());
        assertEquals("CompletableFuture has an unexpected value", value, completableFuture.get());

        // 2. test future failure to complete
        completableFuture = new CompletableFuture<>();
        streamObserver = MicrobeanTillerClient
                .createUnaryCallStreamObserverForCompletableFuture(completableFuture);
        streamObserver.onNext(value);
        value = UUID.randomUUID().toString();
        streamObserver.onNext(value);
        // we haven't called onError yet
        assertFalse("CompletableFuture should not be done yet", completableFuture.isDone());
        assertNull("CompletableFuture.get() should still be null", completableFuture.getNow(null));
        streamObserver.onError(new Exception(value));
        assertTrue("CompletableFuture should have failed already", completableFuture.isDone());
        assertTrue("CompletableFuture should have completed exceptionally",
                completableFuture.isCompletedExceptionally());
        assertFalse("CompletableFuture should not have been canceled",
                completableFuture.isCancelled());
        try {
            completableFuture.get();
            fail("CompletableFuture should have completed exceptionally");
        } catch (ExecutionException ex) {
            assertNotNull("ex == null", ex);
            assertNotNull("ex.getClause() == null", ex.getCause());
            assertEquals("Unexpected exception message", value, ex.getCause().getMessage());
        }
    }

    private MicrobeanTillerClient createTestTillerClient() {
        return createTestTillerClient(true);
    }

    private MicrobeanTillerClient createTestTillerClient(boolean serving) {
        return new MicrobeanTillerClient(null) {
            @Override
            Tiller getTillerInstance(TillerConfig tillerConfig) {
                return new MockTiller(serving ? ServingStatus.SERVING : ServingStatus.NOT_SERVING);
            }
        };
    }

}
