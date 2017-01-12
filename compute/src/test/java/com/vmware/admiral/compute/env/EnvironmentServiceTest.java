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

package com.vmware.admiral.compute.env;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.compute.env.ComputeProfileService.ComputeProfile;
import com.vmware.admiral.compute.env.EnvironmentService.EnvironmentState;
import com.vmware.admiral.compute.env.EnvironmentService.EnvironmentStateExpanded;
import com.vmware.admiral.compute.env.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.env.StorageProfileService.StorageProfile;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;

/**
 * Tests for the {@link EnvironmentService} class.
 */
public class EnvironmentServiceTest extends ComputeBaseTest {
    @Test
    public void testAwsDefault() throws Throwable {
        String awsEnvLink = UriUtils.buildUriPath(EnvironmentService.FACTORY_LINK,
                EndpointType.aws.name());
        waitForServiceAvailability(awsEnvLink);

        EnvironmentStateExpanded env = getDocument(EnvironmentStateExpanded.class, awsEnvLink,
                UriUtils.URI_PARAM_ODATA_EXPAND,
                ServiceDocumentQueryResult.FIELD_NAME_DOCUMENT_LINKS);

        assertNotNull(env);
        assertNotNull(env.name);
        assertEquals(EndpointType.aws.name(), env.endpointType);
        assertNotNull(env.computeProfile);
        assertEquals("t2.micro", env.computeProfile.instanceTypeMapping.get("small").instanceType);
        assertNull(env.computeProfile.imageMapping.get("coreos").image);
        assertEquals("ami-220f2b35",
                env.computeProfile.imageMapping.get("coreos").imageByRegion.get("us-east-1"));
        assertEquals("ami-5f52183f",
                env.computeProfile.imageMapping.get("coreos").imageByRegion.get("us-west-1"));
    }

    @Test
    public void testVsphereDefault() throws Throwable {
        String awsEnvLink = UriUtils.buildUriPath(EnvironmentService.FACTORY_LINK,
                EndpointType.vsphere.name());
        waitForServiceAvailability(awsEnvLink);

        EnvironmentStateExpanded env = getDocument(EnvironmentStateExpanded.class, awsEnvLink,
                UriUtils.URI_PARAM_ODATA_EXPAND,
                ServiceDocumentQueryResult.FIELD_NAME_DOCUMENT_LINKS);

        assertNotNull(env);
        assertNotNull(env.name);
        assertEquals(EndpointType.vsphere.name(), env.endpointType);
        assertNotNull(env.computeProfile);
        assertNull(env.computeProfile.instanceTypeMapping.get("small").instanceType);
        assertEquals(1, env.computeProfile.instanceTypeMapping.get("small").cpuCount);
        assertEquals(1024, env.computeProfile.instanceTypeMapping.get("small").memoryMb);
        assertEquals(
                "https://stable.release.core-os.net/amd64-usr/current/coreos_production_vmware_ova.ova",
                env.computeProfile.imageMapping.get("coreos").image);
    }

    @Test
    public void testAzureDefault() throws Throwable {
        String awsEnvLink = UriUtils.buildUriPath(EnvironmentService.FACTORY_LINK,
                EndpointType.azure.name());
        waitForServiceAvailability(awsEnvLink);

        EnvironmentStateExpanded env = getDocument(EnvironmentStateExpanded.class, awsEnvLink,
                UriUtils.URI_PARAM_ODATA_EXPAND,
                ServiceDocumentQueryResult.FIELD_NAME_DOCUMENT_LINKS);

        assertNotNull(env);
        assertNotNull(env.name);
        assertEquals(EndpointType.azure.name(), env.endpointType);
        assertNotNull(env.computeProfile);
        assertNotNull(env.storageProfile);
        assertEquals("Basic_A2", env.computeProfile.instanceTypeMapping.get("large").instanceType);
        assertEquals(3, env.storageProfile.bootDiskPropertyMapping.size());
    }

    @Test
    public void testExpanded() throws Throwable {
        ComputeProfile compute = new ComputeProfile();
        compute = doPost(compute, ComputeProfileService.FACTORY_LINK);
        StorageProfile storage = new StorageProfile();
        storage = doPost(storage, StorageProfileService.FACTORY_LINK);
        NetworkProfile network = new NetworkProfile();
        network = doPost(network, NetworkProfileService.FACTORY_LINK);

        EnvironmentState env = new EnvironmentState();
        env.name = "test env";
        env.endpointType = EndpointType.vsphere.name();
        env.computeProfileLink = compute.documentSelfLink;
        env.storageProfileLink = storage.documentSelfLink;
        env.networkProfileLink = network.documentSelfLink;
        env = doPost(env, EnvironmentService.FACTORY_LINK);

        EnvironmentState retrievedEnv = getDocument(EnvironmentState.class, env.documentSelfLink);
        assertEquals(compute.documentSelfLink, retrievedEnv.computeProfileLink);
        assertEquals(storage.documentSelfLink, retrievedEnv.storageProfileLink);
        assertEquals(network.documentSelfLink, retrievedEnv.networkProfileLink);

        EnvironmentStateExpanded retrievedExpandedEnv = getDocument(EnvironmentStateExpanded.class,
                env.documentSelfLink,
                UriUtils.URI_PARAM_ODATA_EXPAND,
                ServiceDocumentQueryResult.FIELD_NAME_DOCUMENT_LINKS);
        assertEquals(compute.documentSelfLink, retrievedExpandedEnv.computeProfileLink);
        assertEquals(storage.documentSelfLink, retrievedExpandedEnv.storageProfileLink);
        assertEquals(network.documentSelfLink, retrievedExpandedEnv.networkProfileLink);
        assertEquals(compute.documentSelfLink, retrievedExpandedEnv.computeProfile.documentSelfLink);
        assertEquals(storage.documentSelfLink, retrievedExpandedEnv.storageProfile.documentSelfLink);
        assertEquals(network.documentSelfLink, retrievedExpandedEnv.networkProfile.documentSelfLink);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testMissingEndpointParams() throws Throwable {
        EnvironmentState env = new EnvironmentState();
        env.name = "test env";
        env.computeProfileLink = "test-link";
        env.storageProfileLink = "test-link";
        env.networkProfileLink = "test-link";
        doPost(env, EnvironmentService.FACTORY_LINK);
    }

    @Test(expected = LocalizableValidationException.class)
    public void testBothEndpointLinkAndTypeParams() throws Throwable {
        EnvironmentState env = new EnvironmentState();
        env.name = "test env";
        env.computeProfileLink = "test-link";
        env.storageProfileLink = "test-link";
        env.networkProfileLink = "test-link";
        env.endpointLink = "test-link";
        env.endpointType = EndpointType.aws.name();
        doPost(env, EnvironmentService.FACTORY_LINK);
    }
}
