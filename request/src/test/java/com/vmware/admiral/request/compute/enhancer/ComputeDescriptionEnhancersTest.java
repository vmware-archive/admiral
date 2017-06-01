/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.compute.enhancer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.profile.ComputeImageDescription;
import com.vmware.admiral.compute.profile.ComputeProfileService;
import com.vmware.admiral.compute.profile.ComputeProfileService.ComputeProfile;
import com.vmware.admiral.compute.profile.ImageProfileService;
import com.vmware.admiral.compute.profile.InstanceTypeDescription;
import com.vmware.admiral.compute.profile.InstanceTypeService;
import com.vmware.admiral.compute.profile.NetworkProfileService;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.compute.profile.ProfileService.ProfileState;
import com.vmware.admiral.compute.profile.ProfileService.ProfileStateExpanded;
import com.vmware.admiral.compute.profile.StorageProfileService;
import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfile;
import com.vmware.admiral.host.CaSigningCertService;
import com.vmware.admiral.host.HostInitServiceHelper;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.admiral.request.compute.enhancer.Enhancer.EnhanceContext;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.photon.controller.model.security.util.KeyUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class ComputeDescriptionEnhancersTest extends BaseComputeDescriptionEnhancerTest {

    @Before
    public void setup() throws Throwable {
        host.registerForServiceAvailability(CaSigningCertService.startTask(host), true,
                CaSigningCertService.FACTORY_LINK);
        HostInitServiceHelper.startServices(host, TestInitialBootService.class);
        HostInitServiceHelper.startServiceFactories(host,
                CaSigningCertService.class, ProfileService.class,
                ComputeProfileService.class, StorageProfileService.class, ImageProfileService.class,
                InstanceTypeService.class, NetworkProfileService.class,
                NetworkInterfaceDescriptionService.class,
                DiskService.class, StorageDescriptionService.class, ResourceGroupService.class);
        host.startFactory(TagService.class, TagFactoryService::new);
        waitForServiceAvailability(ProfileService.FACTORY_LINK);
        waitForServiceAvailability(CaSigningCertService.FACTORY_LINK);
        waitForServiceAvailability(ManagementUriParts.AUTH_CREDENTIALS_CA_LINK);

        host.sendRequest(Operation.createPost(
                UriUtils.buildUri(host, TestInitialBootService.class))
                .setReferer(host.getUri())
                .setBody(new ServiceDocument()));
        waitForInitialBootServiceToBeSelfStopped(TestInitialBootService.SELF_LINK);

        cd = new ComputeDescription();
        cd.customProperties = new HashMap<>();
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME, "ubuntu-1604");

        String awsEndpointType = EndpointType.aws.name();
        context = new EnhanceContext();
        context.endpointType = awsEndpointType;
        context.profileLink = UriUtils.buildUriPath(ProfileService.FACTORY_LINK,
                awsEndpointType);
        context.regionId = CommonTestStateFactory.ENDPOINT_REGION_ID;
    }

    @Test
    public void testEnhanceWithSshEnabledAndPreconfiguredPublicSshKeyAuthLink() throws Throwable {
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_ENABLE_SSH_ACCESS_NAME,
                "true");

        cd.authCredentialsLink = getClientPublicSshKeyAuth().documentSelfLink;

        TestContext ctx = testCreate(1);
        DeferredResult<ComputeDescription> result = ComputeDescriptionEnhancers
                .build(host, UriUtils.buildUri(host, "test")).enhance(context,
                        cd);
        result.whenComplete((desc, t) -> {
            if (t != null) {
                ctx.failIteration(t);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        assertNotNull(cd.authCredentialsLink);
    }

    @Test
    public void testEnhanceWithRemoteAPIAndCustomPort() {
        cd.customProperties.put(ComputeAllocationTaskState.ENABLE_COMPUTE_CONTAINER_HOST_PROP_NAME,
                "true");
        cd.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());
        cd.customProperties.put(ContainerHostService.DOCKER_HOST_PORT_PROP_NAME, "2376");

        enhance(ComputeDescriptionEnhancers.build(host, UriUtils.buildUri(host, "test")));

        assertEquals(ManagementUriParts.AUTH_CREDENTIALS_CLIENT_LINK,
                cd.customProperties.get(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME));
    }

    @Test
    public void testEnhanceImageInstanceTypeCaseInsensitive() throws JsonProcessingException {
        cd.instanceType = "xLarge";
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME, "CoreOs");

        enhance(ComputeDescriptionEnhancers.build(host, UriUtils.buildUri(host, "test")));

        assertNotNull(cd.instanceType);
        assertNotNull(cd.customProperties.get(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME));
    }

    @Test
    public void testEnhanceFull() {
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_ENABLE_SSH_ACCESS_NAME,
                "true");
        cd.customProperties.put(ComputeAllocationTaskState.ENABLE_COMPUTE_CONTAINER_HOST_PROP_NAME,
                "true");
        cd.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());

        enhance(ComputeDescriptionEnhancers.build(host, UriUtils.buildUri(host, "test")));

        assertNull("Expected to have content", context.content);
    }

    /**
     * Validates native 'imageId' is populated from Profile through EnhanceContext to DiskState.
     */
    @Test
    public void testEnhanceImage_imageId() throws Throwable {

        ProfileWithComputeBuilder profileBuilder = new ProfileWithComputeBuilder(
                ProfileWithComputeBuilder.IMAGE_ID);

        // OVERRIDE original Profile configured by 'setup' method
        context.profileLink = profileBuilder.build().documentSelfLink;

        // Method under testing...
        enhance(ComputeDescriptionEnhancers.build(host,
                UriUtils.buildUri(host, "testEnhanceImage_imageId")));

        assertEquals("EnhanceContext.resolvedImage", profileBuilder.imageIdValue,
                context.resolvedImage);
        assertNull("EnhanceContext.resolvedImageLink", context.resolvedImageLink);

        assertDiskStates(diskState -> {
            assertEquals("DiskState.sourceImageReference", profileBuilder.imageIdValue,
                    diskState.sourceImageReference.toString());
            assertNull("DiskState.diskState", diskState.imageLink);
        });
    }

    /**
     * Validates 'imageLink' is populated from Profile through EnhanceContext to DiskState.
     */
    @Test
    public void testEnhanceImage_imageLink() throws Throwable {

        ProfileWithComputeBuilder profileBuilder = new ProfileWithComputeBuilder(
                ProfileWithComputeBuilder.IMAGE_LINK);

        // Override original Profile configured by 'setup' method
        context.profileLink = profileBuilder.build().documentSelfLink;

        // Method under testing...
        enhance(ComputeDescriptionEnhancers.build(host, UriUtils.buildUri(host, "test")));

        assertEquals("EnhanceContext.resolvedImageLink", profileBuilder.imageLinkValue,
                context.resolvedImageLink);
        assertNull("EnhanceContext.resolvedImage", context.resolvedImage);

        assertDiskStates(diskState -> {
            assertEquals("DiskState.diskState", profileBuilder.imageLinkValue, diskState.imageLink);
            assertNull("DiskState.sourceImageReference", diskState.sourceImageReference);
        });
    }

    /**
     * Validates 'imageLink' has precedence over 'imageId'.
     */
    @Test
    public void testEnhanceImage_imageLinkHasPrecedence() throws Throwable {

        ProfileWithComputeBuilder profileBuilder = new ProfileWithComputeBuilder(
                ProfileWithComputeBuilder.BOTH);

        // Override original Profile configured by 'setup' method
        context.profileLink = profileBuilder.build().documentSelfLink;

        // Method under testing...
        enhance(ComputeDescriptionEnhancers.build(host, UriUtils.buildUri(host, "test")));

        assertEquals("EnhanceContext.resolvedImageLink", profileBuilder.imageLinkValue,
                context.resolvedImageLink);
        assertNull("EnhanceContext.resolvedImage", context.resolvedImage);

        assertDiskStates(diskState -> {
            assertEquals("DiskState.diskState", profileBuilder.imageLinkValue, diskState.imageLink);
            assertNull("DiskState.sourceImageReference", diskState.sourceImageReference);
        });
    }

    /**
     * Validates 'imageLink' is populated from Profile through EnhanceContext to DiskState.
     */
    @Test
    public void testEnhanceImage_none() throws Throwable {

        ProfileWithComputeBuilder profileBuilder = new ProfileWithComputeBuilder("NONE");

        // Override original Profile configured by 'setup' method
        context.profileLink = profileBuilder.build().documentSelfLink;

        try {
            // Method under testing...
            enhance(ComputeDescriptionEnhancers.build(host, UriUtils.buildUri(host, "test")));

            Assert.fail("IllegalStateException expected");
        } catch (CompletionException exc) {
            assertTrue("IllegalStateException expected", exc.getCause() instanceof IllegalStateException);
        }
    }

    private AuthCredentialsServiceState getClientPublicSshKeyAuth() throws Throwable {
        AuthCredentialsServiceState state = new AuthCredentialsServiceState();
        state.type = AuthCredentialsType.Public.name();
        state.userEmail = UUID.randomUUID().toString();
        generateKeyPair((key, ssh) -> {
            state.publicKey = ssh;
        });
        return doPost(state, AuthCredentialsService.FACTORY_LINK);
    }

    private void generateKeyPair(BiConsumer<KeyPair, String> consumer) {
        KeyPair keyPair = KeyUtil.generateRSAKeyPair();
        String sshAuthorizedKey = KeyUtil
                .toPublicOpenSSHFormat((RSAPublicKey) keyPair.getPublic());

        consumer.accept(keyPair, sshAuthorizedKey);
    }

    private class ProfileWithComputeBuilder {

        static final String BOTH = "BOTH";
        static final String IMAGE_ID = "IMAGE_ID";
        static final String IMAGE_LINK = "IMAGE_LINK";

        // Instruct which ComputeImageDescription field to populate
        final String imageSpec;

        String instanceTypeKey = cd.instanceType;
        String instanceTypeValue = "endpoint.specific.instanceType.xLarge";

        String imageKey = cd.customProperties.get(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME);

        String imageIdValue = "http://vmware.com/endpointSpecificImageId";
        String imageLinkValue = "http://localhost" + ImageService.FACTORY_LINK
                + "/endpointSpecificImageStateSelfLink";

        ProfileWithComputeBuilder(String imageSpec) {
            this.imageSpec = imageSpec;
        }

        ProfileStateExpanded build() throws Throwable {

            ComputeProfile computeProfile = new ComputeProfile();
            {
                InstanceTypeDescription instanceTypeDesc = new InstanceTypeDescription();
                instanceTypeDesc.instanceType = instanceTypeValue;
                computeProfile.instanceTypeMapping = Collections.singletonMap(instanceTypeKey,
                        instanceTypeDesc);

                ComputeImageDescription imageProfile = new ComputeImageDescription();
                if (imageSpec == IMAGE_ID) {

                    imageProfile.image = imageIdValue;

                } else if (imageSpec == IMAGE_LINK) {

                    imageProfile.imageLink = imageLinkValue;

                } else if (imageSpec == BOTH) {

                    imageProfile.image = imageIdValue;
                    imageProfile.imageLink = imageLinkValue;
                } else {
                    // Everything else is considered as NONE
                }
                computeProfile.imageMapping = Collections.singletonMap(imageKey, imageProfile);

                computeProfile = doPost(computeProfile, ComputeProfileService.FACTORY_LINK);
            }

            StorageProfile storageProfile = doPost(new StorageProfile(),
                    StorageProfileService.FACTORY_LINK);

            NetworkProfile networkProfile = doPost(new NetworkProfile(),
                    NetworkProfileService.FACTORY_LINK);

            // Create the Profile with Compute profile set and empty Net/Storage profiles
            ProfileState profile = new ProfileState();
            profile.name = "ProfileWithCompute";
            profile.endpointType = EndpointType.vsphere.name();
            profile.computeProfileLink = computeProfile.documentSelfLink;
            profile.storageProfileLink = storageProfile.documentSelfLink;
            profile.networkProfileLink = networkProfile.documentSelfLink;
            profile = doPost(profile, ProfileService.FACTORY_LINK);

            return getDocument(ProfileStateExpanded.class,
                    profile.documentSelfLink,
                    UriUtils.URI_PARAM_ODATA_EXPAND,
                    ServiceDocumentQueryResult.FIELD_NAME_DOCUMENT_LINKS);
        }
    }
}
