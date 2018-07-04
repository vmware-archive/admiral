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

package com.vmware.admiral.tiller;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Iterator;

import hapi.services.tiller.ReleaseServiceGrpc.ReleaseServiceBlockingStub;
import hapi.services.tiller.Tiller.ListReleasesRequest;
import hapi.services.tiller.Tiller.ListReleasesResponse;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;

import org.junit.Test;
import org.microbean.helm.Tiller;

import com.vmware.admiral.adapter.tiller.TillerFactory;
import com.vmware.admiral.adapter.tiller.client.MicrobeanTillerClientProvider;
import com.vmware.admiral.adapter.tiller.client.TillerClientProvider;
import com.vmware.admiral.adapter.tiller.client.TillerClientProviderUtil;

// TODO execute ITs as part of the pipeline
public class MicrobeanPlaygroundIT extends BaseTillerIntegrationSupportIT {

    @Test
    public void testGetPods() {
        try (DefaultKubernetesClient client = new DefaultKubernetesClient()) {
            client.pods().list().getItems().forEach(p -> {
                System.out.println("Found pod " + p.getMetadata().getName());
            });
        }
    }

    @Test
    public void testGetReleasesDefaultNamespaceUnsecured() throws Throwable {
        try (Tiller tiller = TillerFactory.newTiller(buildPlaintextTillerConfig())) {
            listReleases(tiller);
        }
    }

    @Test
    public void testGetReleaseCustomNamespaceCertAuth() throws Throwable {
        try (Tiller tiller = TillerFactory.newTiller(buildTlsTillerConfig())) {
            listReleases(tiller);
        }
    }

    @Test
    public void testHealthcheckCustomNamespaceCertAuth() throws Throwable {
        try (Tiller tiller = TillerFactory.newTiller(buildTlsTillerConfig())) {
            HealthCheckRequest request = HealthCheckRequest.getDefaultInstance();
            HealthCheckResponse check = tiller.getHealthBlockingStub().check(request);
            assertEquals(ServingStatus.SERVING, check.getStatus());
        }
    }

    @Test
    public void testTillerClientProvider() {
        TillerClientProvider provider = TillerClientProviderUtil.getTillerClientProvider();
        assertNotNull(provider);
        assertEquals(MicrobeanTillerClientProvider.class, provider.getClass());
    }

    private void listReleases(Tiller tiller) {

        ListReleasesRequest listReleasesRequest = ListReleasesRequest.newBuilder().build();
        ReleaseServiceBlockingStub releaseServiceBlockingStub = tiller
                .getReleaseServiceBlockingStub();
        Iterator<ListReleasesResponse> releases = releaseServiceBlockingStub
                .listReleases(listReleasesRequest);

        releases.forEachRemaining(lrp -> {
            lrp.getReleasesList().forEach(r -> {
                System.out.println("Found release " + r.getName());
            });
        });
    }

}
