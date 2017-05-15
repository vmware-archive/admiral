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

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.BaseTestCase;
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
import com.vmware.admiral.service.common.AbstractInitialBootService;
import com.vmware.photon.controller.model.Constraint;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.photon.controller.model.security.util.KeyUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;

public class ComputeDescriptionEnhancersTest extends BaseTestCase {

    private static final String DEFAULT_REGION_ID = "eu-west-1";

    private ComputeDescription cd;
    private EnhanceContext context;

    public static class TestInitialBootService extends AbstractInitialBootService {
        public static final String SELF_LINK = ManagementUriParts.CONFIG + "/test-initial-boot";

        @Override
        public void handlePost(Operation post) {
            ArrayList<ServiceDocument> states = new ArrayList<>();
            states.addAll(ProfileService.getAllDefaultDocuments());
            initInstances(post, false, true, states.toArray(new ServiceDocument[states.size()]));
        }
    }

    @Before
    public void setup() throws Throwable {
        host.registerForServiceAvailability(CaSigningCertService.startTask(host), true,
                CaSigningCertService.FACTORY_LINK);
        HostInitServiceHelper.startServices(host,
                TestInitialBootService.class);
        HostInitServiceHelper.startServiceFactories(host,
                CaSigningCertService.class, ProfileService.class,
                ComputeProfileService.class, StorageProfileService.class, ImageProfileService.class,
                InstanceTypeService.class, NetworkProfileService.class,
                NetworkInterfaceDescriptionService.class,
                DiskService.class);
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
        context.regionId = DEFAULT_REGION_ID;
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
    public void testEnhanceDisk() throws Throwable {
        cd.instanceType = "xLarge";
        // Build disk description
        cd.diskDescLinks = buildDiskStates();
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME, "vc://datastore/test.iso");

        // OVERRIDE original Context configured by 'setup' method
        context = new EnhanceContext();
        context.profile = buildProfileServiceWithStorage();
        context.profileLink = context.profile.documentSelfLink;
        context.resolvedImage = "vc://datastore/test.iso";

        // Use case 1: CD (Disk1) with all hard constraints
        // Use case 2: CD (Disk2) with all soft constraints & all matching
        // Use case 3: CD (Disk3) with all hard & soft constraints
        // Use case 4: CD (Disk4) with all soft constraints & few matching
        // Use case 5: CD (Disk5) with all soft constraints & nothing match
        // Use case 6: CD (Disk6) with constraint null.

        enhance(new ComputeDescriptionDiskEnhancer(host, host.getReferer()));

        assertDiskStates(diskState -> {
            if (diskState.name.equals("Disk1") || diskState.name.equals("Disk3")) {
                assertNotNull(diskState.customProperties);
                assertEquals(1, diskState.customProperties.size());
            } else if (diskState.name.equals("Disk2")) {
                assertNotNull(diskState.customProperties);
                assertEquals(3, diskState.customProperties.size());
            } else if (diskState.name.equals("Disk4")) {
                assertNotNull(diskState.customProperties);
                assertEquals(4, diskState.customProperties.size());
            } else if (diskState.name.equals("Disk5") || diskState.name.equals("Disk6")) {
                assertNotNull(diskState.customProperties);
                assertEquals(2, diskState.customProperties.size());
            }
        });
    }

    @Test
    public void testEnhanceDiskWithNoStorageItems() throws Throwable {
        cd.instanceType = "xLarge";
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME,
                "vc://datastore/test.iso");

        // Build disk description
        ArrayList<String> diskLinks = buildDiskStatesForNoStorageItems();
        cd.diskDescLinks = diskLinks;

        // Use case 1: Disk1 with all hard constraints. It should fail.
        // Use case 2: Disk2 with no constraints.
        ComputeDescriptionDiskEnhancer enhancer = new ComputeDescriptionDiskEnhancer(host,
                host.getReferer());

        DeferredResult<ComputeDescription> result = enhancer.enhance(context, cd);

        TestContext ctx = testCreate(1);
        result.whenComplete((desc, t) -> {
            if (t != null) {
                assertNotNull(t.getMessage());
                ctx.completeIteration();
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        // Now get all the disk states to find the properties size.
        assertDiskStates(diskState -> assertNull(diskState.customProperties));
    }

    @Test
    public void testEnhanceDiskWithNoStorageItemsForSoftConstraint() throws Throwable {
        cd.instanceType = "xLarge";
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME,
                "vc://datastore/test.iso");

        // Build disk description
        ArrayList<String> diskLinks = buildSoftConstraintDisk();
        cd.diskDescLinks = diskLinks;

        // Use case 1: Disk1 with all soft constraints. It shouldn't fail
        enhance(new ComputeDescriptionDiskEnhancer(this.host, this.host.getReferer()));

        // Now get all the disk states to find the properties size.
        assertDiskStates(diskState -> assertNull(diskState.customProperties));
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
            Assert.assertTrue("IllegalStateException expected", exc.getCause() instanceof IllegalStateException);
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

    private ProfileStateExpanded buildProfileServiceWithStorage() throws Throwable {
        ComputeProfileService.ComputeProfile compute = new ComputeProfileService.ComputeProfile();
        compute = doPost(compute, ComputeProfileService.FACTORY_LINK);
        StorageProfileService.StorageProfile storage = buildStorageProfileWithConstraints();
        NetworkProfileService.NetworkProfile networkProfile = new NetworkProfileService.NetworkProfile();
        networkProfile = doPost(networkProfile, NetworkProfileService.FACTORY_LINK);

        ProfileState profile = new ProfileState();
        profile.name = "test profile";
        profile.endpointType = EndpointType.vsphere.name();
        profile.computeProfileLink = compute.documentSelfLink;
        profile.storageProfileLink = storage.documentSelfLink;
        profile.networkProfileLink = networkProfile.documentSelfLink;
        profile = doPost(profile, ProfileService.FACTORY_LINK);

        ProfileState retrievedProfile = getDocument(ProfileState.class, profile.documentSelfLink);
        assertEquals(storage.documentSelfLink, retrievedProfile.storageProfileLink);

        ProfileStateExpanded retrievedExpandedProfile = getDocument(ProfileStateExpanded.class,
                profile.documentSelfLink,
                UriUtils.URI_PARAM_ODATA_EXPAND,
                ServiceDocumentQueryResult.FIELD_NAME_DOCUMENT_LINKS);
        assertEquals(storage.documentSelfLink, retrievedExpandedProfile.storageProfileLink);
        assertEquals(storage.documentSelfLink,
                retrievedExpandedProfile.storageProfile.documentSelfLink);

        return retrievedExpandedProfile;
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

    private StorageProfileService.StorageProfile buildStorageProfileWithConstraints()
            throws Throwable {
        ArrayList<String> tags = buildTagLinks();

        StorageProfileService.StorageItem storageItem1 = new StorageProfileService.StorageItem();
        storageItem1.defaultItem = false;
        storageItem1.name = "fast";
        storageItem1.tagLinks = new HashSet<>(Arrays.asList(tags.get(0), tags.get(1)));
        storageItem1.diskProperties = new HashMap<>();
        storageItem1.diskProperties.put("key1", "value1");

        StorageProfileService.StorageItem storageItem2 = new StorageProfileService.StorageItem();
        storageItem2.defaultItem = true;
        storageItem2.name = "slow";
        storageItem2.tagLinks = new HashSet<>(Arrays.asList(tags.get(2)));
        storageItem2.diskProperties = new HashMap<>();
        storageItem2.diskProperties.put("key1", "value1");
        storageItem2.diskProperties.put("key2", "value2");

        StorageProfileService.StorageItem storageItem3 = new StorageProfileService.StorageItem();
        storageItem3.defaultItem = false;
        storageItem3.name = "temporary";
        storageItem3.tagLinks = new HashSet<>(Arrays.asList(tags.get(0), tags.get(2), tags.get(4)));
        storageItem3.diskProperties = new HashMap<>();
        storageItem3.diskProperties.put("key1", "value1");
        storageItem3.diskProperties.put("key2", "value2");
        storageItem3.diskProperties.put("key3", "value3");

        StorageProfileService.StorageItem storageItem4 = new StorageProfileService.StorageItem();
        storageItem4.defaultItem = false;
        storageItem4.name = "random";
        storageItem4.tagLinks = new HashSet<>(Arrays.asList(tags.get(3), tags.get(4)));
        storageItem4.diskProperties = new HashMap<>();
        storageItem4.diskProperties.put("key1", "value1");
        storageItem4.diskProperties.put("key2", "value2");
        storageItem4.diskProperties.put("key3", "value3");
        storageItem4.diskProperties.put("key4", "value4");

        StorageProfileService.StorageProfile storageProfile = new StorageProfileService.StorageProfile();
        storageProfile.storageItems = new ArrayList<>();
        storageProfile.storageItems.add(storageItem1);
        storageProfile.storageItems.add(storageItem2);
        storageProfile.storageItems.add(storageItem3);
        storageProfile.storageItems.add(storageItem4);

        storageProfile = doPost(storageProfile, StorageProfileService.FACTORY_LINK);
        return storageProfile;
    }

    private ArrayList<String> buildTagLinks() throws Throwable {
        TagService.TagState fastTag = new TagService.TagState();
        fastTag.key = "FAST";
        fastTag.value = "";
        fastTag = doPost(fastTag, TagService.FACTORY_LINK);

        TagService.TagState haTag = new TagService.TagState();
        haTag.key = "HA";
        haTag.value = "";
        haTag = doPost(haTag, TagService.FACTORY_LINK);

        TagService.TagState logsTag = new TagService.TagState();
        logsTag.key = "LOGS_OPTIMIZED";
        logsTag.value = "";
        logsTag = doPost(logsTag, TagService.FACTORY_LINK);

        TagService.TagState criticalTag = new TagService.TagState();
        criticalTag.key = "CRITICAL";
        criticalTag.value = "";
        criticalTag = doPost(criticalTag, TagService.FACTORY_LINK);

        TagService.TagState nonCriticalTag = new TagService.TagState();
        nonCriticalTag.key = "REPLICATED";
        nonCriticalTag.value = "";
        nonCriticalTag = doPost(nonCriticalTag, TagService.FACTORY_LINK);

        ArrayList<String> tags = new ArrayList<>();
        tags.add(fastTag.documentSelfLink);
        tags.add(haTag.documentSelfLink);
        tags.add(logsTag.documentSelfLink);
        tags.add(criticalTag.documentSelfLink);
        tags.add(nonCriticalTag.documentSelfLink);

        return tags;
    }

    private ArrayList<String> buildDiskStates() throws Throwable {
        ArrayList<String> diskLinks = new ArrayList<>();

        DiskState diskState1 = new DiskState();
        diskState1.capacityMBytes = 1024;
        diskState1.type = DiskService.DiskType.HDD;
        diskState1.bootOrder = 1;
        diskState1.name = "Disk1";
        diskState1.constraint = new Constraint();

        List<Constraint.Condition> conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("FAST", null,
                Constraint.Condition.Enforcement.HARD, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("HA", null,
                Constraint.Condition.Enforcement.HARD, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState1.constraint.conditions = conditions;
        diskState1 = doPost(diskState1, DiskService.FACTORY_LINK);
        diskLinks.add(diskState1.documentSelfLink);

        DiskState diskState2 = new DiskState();
        diskState2.capacityMBytes = 2048;
        diskState2.type = DiskService.DiskType.SSD;
        diskState2.bootOrder = 2;
        diskState2.name = "Disk2";
        diskState2.constraint = new Constraint();

        conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("LOGS_OPTIMIZED", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("REPLICATED", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState2.constraint.conditions = conditions;
        diskState2 = doPost(diskState2, DiskService.FACTORY_LINK);
        diskLinks.add(diskState2.documentSelfLink);

        DiskState diskState3 = new DiskState();
        diskState3.capacityMBytes = 1024;
        diskState3.type = DiskService.DiskType.CDROM;
        diskState3.bootOrder = 3;
        diskState3.name = "Disk3";
        diskState3.constraint = new Constraint();

        conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("FAST", null,
                Constraint.Condition.Enforcement.HARD, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("HA", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState3.constraint.conditions = conditions;
        diskState3 = doPost(diskState3, DiskService.FACTORY_LINK);
        diskLinks.add(diskState3.documentSelfLink);

        DiskState diskState4 = new DiskState();
        diskState4.capacityMBytes = 1024;
        diskState4.type = DiskService.DiskType.HDD;
        diskState4.bootOrder = 4;
        diskState4.name = "Disk4";
        diskState4.constraint = new Constraint();

        conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("CRITICAL", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("NON_REPLICATED", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("NORMAL", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState4.constraint.conditions = conditions;
        diskState4 = doPost(diskState4, DiskService.FACTORY_LINK);
        diskLinks.add(diskState4.documentSelfLink);

        DiskState diskState5 = new DiskState();
        diskState5.capacityMBytes = 512;
        diskState5.type = DiskService.DiskType.FLOPPY;
        diskState5.bootOrder = 5;
        diskState5.name = "Disk5";
        diskState5.constraint = new Constraint();

        conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("NON_REPLICATED", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("NORMAL", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState5.constraint.conditions = conditions;
        diskState5 = doPost(diskState5, DiskService.FACTORY_LINK);
        diskLinks.add(diskState5.documentSelfLink);

        DiskState diskState6 = new DiskState();
        diskState6.capacityMBytes = 512;
        diskState6.type = DiskService.DiskType.FLOPPY;
        diskState6.bootOrder = 6;
        diskState6.name = "Disk6";
        diskState6.constraint = null;

        diskState6 = doPost(diskState6, DiskService.FACTORY_LINK);
        diskLinks.add(diskState6.documentSelfLink);

        return diskLinks;
    }

    private ArrayList<String> buildDiskStatesForNoStorageItems() throws Throwable {
        ArrayList<String> diskLinks = new ArrayList<>();

        DiskState diskState1 = new DiskState();
        diskState1.capacityMBytes = 1024;
        diskState1.type = DiskService.DiskType.HDD;
        diskState1.bootOrder = 1;
        diskState1.name = "Disk1";
        diskState1.constraint = new Constraint();

        List<Constraint.Condition> conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("FAST", null,
                Constraint.Condition.Enforcement.HARD, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("HA", null,
                Constraint.Condition.Enforcement.HARD, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState1.constraint.conditions = conditions;
        diskState1 = doPost(diskState1, DiskService.FACTORY_LINK);
        diskLinks.add(diskState1.documentSelfLink);

        DiskState diskState2 = new DiskState();
        diskState2.capacityMBytes = 1024;
        diskState2.type = DiskService.DiskType.CDROM;
        diskState2.bootOrder = 3;
        diskState2.name = "Disk2";
        diskState2.constraint = new Constraint();
        diskState2 = doPost(diskState2, DiskService.FACTORY_LINK);
        diskLinks.add(diskState2.documentSelfLink);

        return diskLinks;
    }

    private ArrayList<String> buildSoftConstraintDisk() throws Throwable {
        ArrayList<String> diskLinks = new ArrayList<>();

        DiskState diskState2 = new DiskState();
        diskState2.capacityMBytes = 2048;
        diskState2.type = DiskService.DiskType.SSD;
        diskState2.bootOrder = 2;
        diskState2.name = "Disk1";
        diskState2.constraint = new Constraint();

        List<Constraint.Condition> conditions = new ArrayList<>();
        conditions.add(Constraint.Condition.forTag("LOGS_OPTIMIZED", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        conditions.add(Constraint.Condition.forTag("REPLICATED", null,
                Constraint.Condition.Enforcement.SOFT, QueryTask.Query.Occurance.MUST_OCCUR));
        diskState2.constraint.conditions = conditions;
        diskState2 = doPost(diskState2, DiskService.FACTORY_LINK);
        diskLinks.add(diskState2.documentSelfLink);

        return diskLinks;
    }

    /**
     * Blocking version of
     * {@link ComputeDescriptionEnhancer#enhance(EnhanceContext, ResourceState)}.
     */
    private void enhance(ComputeDescriptionEnhancer enhancer) {

        DeferredResult<ComputeDescription> result = enhancer.enhance(context, cd);

        TestContext ctx = testCreate(1);

        result.whenComplete((desc, t) -> {
            if (t != null) {
                ctx.failIteration(t);
                return;
            }
            ctx.completeIteration();
        });

        ctx.await();
    }

    /**
     * Helper method traversing 'cd.diskDescLinks' and asserting DiskStates.
     */
    private void assertDiskStates(Consumer<DiskState> assertion) {

        assertNotNull("ComputeDescriptor.diskDescLinks", cd.diskDescLinks);

        List<Operation> getOps = cd.diskDescLinks.stream()
                .map(diskLink -> Operation.createGet(host, diskLink).setReferer(host.getReferer()))
                .collect(Collectors.toList());

        TestContext ctx = testCreate(1);

        OperationJoin.create(getOps).setCompletion((ops, exs) -> {
            if (exs != null && !exs.isEmpty()) {
                ctx.failIteration(new Throwable(exs.toString()));
                return;
            }

            ops.values().forEach(op -> {
                try {
                    assertion.accept(op.getBody(DiskState.class));
                } catch (Throwable t) {
                    ctx.failIteration(t);
                }
            });

            ctx.completeIteration();

        }).sendWith(host);

        ctx.await();
    }
}
