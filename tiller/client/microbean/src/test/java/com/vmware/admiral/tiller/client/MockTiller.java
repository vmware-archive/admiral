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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.grpc.ManagedChannel;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthGrpc.HealthStub;
import io.grpc.stub.StreamObserver;
import org.microbean.helm.Tiller;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class MockTiller extends Tiller {

    private HealthStub healthStubMock;

    public MockTiller() {
        this(ServingStatus.SERVING);
    }

    public MockTiller(ServingStatus servingStatus) {
        super(mock(ManagedChannel.class));
        this.healthStubMock = createHealthStubMock(servingStatus);
    }

    @Override
    public HealthStub getHealthStub() {
        return this.healthStubMock;
    }

    private HealthStub createHealthStubMock(ServingStatus servingStatus) {
        HealthStub healthStubMock = Mockito.mock(HealthStub.class);
        HealthCheckResponse responseMock = mock(HealthCheckResponse.class);
        when(responseMock.getStatus()).thenReturn(servingStatus);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<StreamObserver<HealthCheckResponse>> captor = ArgumentCaptor
                .forClass(StreamObserver.class);

        Mockito.doAnswer(invocation -> {
            StreamObserver<HealthCheckResponse> streamObserver = captor.getValue();
            streamObserver.onNext(responseMock);
            streamObserver.onCompleted();
            return null;
        }).when(healthStubMock).check(any(HealthCheckRequest.class), captor.capture());
        return healthStubMock;
    }
}
