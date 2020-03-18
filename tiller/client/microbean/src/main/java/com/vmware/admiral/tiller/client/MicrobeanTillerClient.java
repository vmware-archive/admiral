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

package com.vmware.admiral.tiller.client;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLException;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.stub.StreamObserver;
import org.microbean.helm.Tiller;

import com.vmware.admiral.tiller.TillerFactory;

public class MicrobeanTillerClient implements TillerClient {

    protected static final String CLIENT_IS_CLOSED_ERROR_MESSAGE = "Client is closed";
    protected static final String HEALTH_CHECK_STATUS_MESSAGE_FORMAT = "Health check status is %s";

    private Tiller tiller;
    private boolean closed = false;

    public MicrobeanTillerClient(TillerConfig tillerConfig) {
        this.tiller = getTillerInstance(tillerConfig);
    }

    @Override
    public CompletableFuture<Void> healthCheck() {
        ensureNotClosed();
        CompletableFuture<HealthCheckResponse> completableFuture = new CompletableFuture<>();
        StreamObserver<HealthCheckResponse> observer = createUnaryCallStreamObserverForCompletableFuture(
                completableFuture);
        tiller.getHealthStub().check(HealthCheckRequest.getDefaultInstance(), observer);
        return completableFuture.thenAccept(response -> {
            if (response.getStatus() != ServingStatus.SERVING) {
                throw new TillerClientException(String.format(HEALTH_CHECK_STATUS_MESSAGE_FORMAT,
                        response.getStatus().toString()));
            }
        });
    }

    @Override
    public void close() {
        ensureNotClosed();
        try {
            tiller.close();
            closed = true;
        } catch (IOException e) {
            throw new TillerClientException("Failed to close tiller instance", e);
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    private void ensureNotClosed() {
        if (closed) {
            throw new TillerClientException(
                    CLIENT_IS_CLOSED_ERROR_MESSAGE);
        }
    }

    Tiller getTillerInstance(TillerConfig tillerConfig) {
        try {
            return TillerFactory.newTiller(tillerConfig);
        } catch (MalformedURLException | SSLException e) {
            throw new TillerClientException("Could not construct a Tiller instance", e);
        }
    }

    static <T> StreamObserver<T> createUnaryCallStreamObserverForCompletableFuture(
            CompletableFuture<T> completableFuture) {

        return new StreamObserver<T>() {

            private T value;

            @Override
            public void onNext(T value) {
                this.value = value;
            }

            @Override
            public void onError(Throwable t) {
                completableFuture.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                completableFuture.complete(this.value);
            }
        };
    }

}
