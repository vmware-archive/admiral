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
import static org.junit.Assert.assertTrue;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.profile.ComputeProfileService;
import com.vmware.admiral.compute.profile.NetworkProfileService;
import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.compute.profile.StorageProfileService;
import com.vmware.admiral.host.CaSigningCertService;
import com.vmware.admiral.host.HostInitServiceHelper;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.admiral.request.compute.enhancer.Enhancer.EnhanceContext;
import com.vmware.admiral.service.common.AbstractInitialBootService;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.photon.controller.model.security.util.KeyUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class ComputeStateEnhancersTest extends BaseTestCase {

    private ComputeState cs;
    private EnhanceContext context;

    @Before
    public void setup() throws Throwable {
        host.registerForServiceAvailability(CaSigningCertService.startTask(host), true,
                CaSigningCertService.FACTORY_LINK);
        HostInitServiceHelper.startServices(host,
                TestInitialBootService.class);
        HostInitServiceHelper.startServiceFactories(host,
                CaSigningCertService.class, ProfileService.class,
                ComputeProfileService.class, StorageProfileService.class,
                NetworkProfileService.class, ComputeDescriptionService.class);
        waitForServiceAvailability(ProfileService.FACTORY_LINK);
        waitForServiceAvailability(CaSigningCertService.FACTORY_LINK);
        waitForServiceAvailability(ManagementUriParts.AUTH_CREDENTIALS_CA_LINK);

        host.sendRequest(Operation.createPost(
                UriUtils.buildUri(host, TestInitialBootService.class))
                .setReferer(host.getUri())
                .setBody(new ServiceDocument()));

        waitForInitialBootServiceToBeSelfStopped(TestInitialBootService.SELF_LINK);

        cs = new ComputeState();
        cs.customProperties = new HashMap<>();

        String awsEndpointType = EndpointType.aws.name();
        context = new EnhanceContext();
        context.imageType = "ubuntu-1604";
        context.endpointType = awsEndpointType;
        context.profileLink = UriUtils.buildUriPath(ProfileService.FACTORY_LINK,
                awsEndpointType);
    }

    @Override
    protected boolean getPeerSynchronizationEnabled() {
        return true;
    }

    @Test
    public void testBaseEnhance() throws Throwable {
        cs.descriptionLink = createComputeDescription(null).documentSelfLink;
        TestContext ctx = testCreate(1);
        DeferredResult<ComputeState> result = ComputeStateEnhancers
                .build(host, UriUtils.buildUri(host, "test")).enhance(context,
                        cs);
        result.whenComplete((desc, t) -> {
            if (t != null) {
                ctx.failIteration(t);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();
        assertNotNull("Expected to have content", context.content);
        assertTrue("Expected to empty content", context.content.isEmpty());
    }

    @Test
    public void testEnhanceWithSshEnabledAndGeneratedKey() throws Throwable {
        ComputeDescription cd = createComputeDescription(getClientPublicKeyAuth());
        cs.customProperties = cd.customProperties;
        cs.descriptionLink = cd.documentSelfLink;
        TestContext ctx = testCreate(1);
        DeferredResult<ComputeState> result = ComputeStateEnhancers
                .build(host, UriUtils.buildUri(host, "test")).enhance(context,
                        cs);
        result.whenComplete((desc, t) -> {
            if (t != null) {
                ctx.failIteration(t);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        assertNotNull("Expected to have content", context.content);
        Object object = context.content.get("ssh_authorized_keys");
        assertNotNull("Expected to have authorized keys", object);
        @SuppressWarnings("rawtypes")
        List list = (List) object;
        assertEquals(1, list.size());
        assertNotNull(list.get(0));

    }

    @Test
    public void testEnhanceWithSshEnabledAndPreconfiguredKey() throws Throwable {
        cs.customProperties.put(ComputeConstants.CUSTOM_PROP_ENABLE_SSH_ACCESS_NAME,
                "true");
        cs.customProperties.put(ComputeConstants.CUSTOM_PROP_SSH_AUTHORIZED_KEY_NAME,
                "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQC1cbdZp...");
        cs.descriptionLink = createComputeDescription(null).documentSelfLink;

        TestContext ctx = testCreate(1);
        DeferredResult<ComputeState> result = ComputeStateEnhancers
                .build(host, UriUtils.buildUri(host, "test")).enhance(context,
                        cs);
        result.whenComplete((desc, t) -> {
            if (t != null) {
                ctx.failIteration(t);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        assertNotNull("Expected to have content", context.content);
        Object object = context.content.get("ssh_authorized_keys");
        assertNotNull("Expected to have authorized keys", object);
        @SuppressWarnings("rawtypes")
        List list = (List) object;
        assertEquals(1, list.size());
        assertNotNull(list.get(0));

    }

    @Test
    public void testEnhanceWithSshEnabledAndPreconfiguredPublicKeyAuthLink() throws Throwable {
        cs.customProperties.put(ComputeConstants.CUSTOM_PROP_ENABLE_SSH_ACCESS_NAME,
                "true");

        cs.descriptionLink = createComputeDescription(
                getClientPublicKeyAuthAndSshKey()).documentSelfLink;
        TestContext ctx = testCreate(1);
        DeferredResult<ComputeState> result = ComputeStateEnhancers
                .build(host, UriUtils.buildUri(host, "test")).enhance(context,
                        cs);
        result.whenComplete((desc, t) -> {
            if (t != null) {
                ctx.failIteration(t);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        assertNotNull("Expected to have content", context.content);
        Object object = context.content.get("ssh_authorized_keys");
        assertNotNull("Expected to have authorized keys", object);
        @SuppressWarnings("rawtypes")
        List list = (List) object;
        assertEquals(1, list.size());
        assertNotNull(list.get(0));
    }

    @Test
    public void testEnhanceWithSshEnabledAndPreconfiguredPublicSshKeyAuthLink() throws Throwable {
        cs.customProperties.put(ComputeConstants.CUSTOM_PROP_ENABLE_SSH_ACCESS_NAME,
                "true");

        cs.descriptionLink = createComputeDescription(getClientPublicSshKeyAuth()).documentSelfLink;
        TestContext ctx = testCreate(1);
        DeferredResult<ComputeState> result = ComputeStateEnhancers
                .build(host, UriUtils.buildUri(host, "test")).enhance(context,
                        cs);
        result.whenComplete((desc, t) -> {
            if (t != null) {
                ctx.failIteration(t);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        assertNotNull("Expected to have content", context.content);
        Object object = context.content.get("ssh_authorized_keys");
        assertNotNull("Expected to have authorized keys", object);
        @SuppressWarnings("rawtypes")
        List list = (List) object;
        assertEquals(1, list.size());
        assertNotNull(list.get(0));

    }

    @Test
    public void testEnhanceWithSshEnabledAndPreconfiguredPublicKeyOnlyAuthLink() throws Throwable {
        cs.customProperties.put(ComputeConstants.CUSTOM_PROP_ENABLE_SSH_ACCESS_NAME,
                "true");

        cs.descriptionLink = createComputeDescription(getClientPublicKeyAuth()).documentSelfLink;
        TestContext ctx = testCreate(1);
        DeferredResult<ComputeState> result = ComputeStateEnhancers
                .build(host, UriUtils.buildUri(host, "test")).enhance(context,
                        cs);
        result.whenComplete((desc, t) -> {
            if (t != null) {
                ctx.failIteration(t);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        assertNotNull("Expected to have content", context.content);
        Object object = context.content.get("ssh_authorized_keys");
        assertNotNull("Expected to have authorized keys", object);
        @SuppressWarnings("rawtypes")
        List list = (List) object;
        assertEquals(1, list.size());
        assertNotNull(list.get(0));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEnhanceWithRemoteAPIAndDefaultPort() throws Throwable {
        cs.customProperties.put(ComputeAllocationTaskState.ENABLE_COMPUTE_CONTAINER_HOST_PROP_NAME,
                "true");
        cs.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());
        cs.descriptionLink = createComputeDescription(null).documentSelfLink;

        TestContext ctx = testCreate(1);
        DeferredResult<ComputeState> result = ComputeStateEnhancers
                .build(host, UriUtils.buildUri(host, "test")).enhance(context,
                        cs);
        result.whenComplete((desc, t) -> {
            if (t != null) {
                ctx.failIteration(t);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        assertNotNull("Expected to have content", context.content);
        Object writeFiles = context.content.get("write_files");
        assertNotNull("Expected to have write-files section", writeFiles);
        @SuppressWarnings({ "rawtypes" })
        List<Object> list = (List) writeFiles;
        assertEquals(4, list.size());

        list.stream()
                .filter(e -> (e instanceof Map))
                .map(e -> (Map<String, Object>) e)
                .filter(m -> "/etc/systemd/system/docker.service.d/docker.conf"
                        .equals(m.get("path")))
                .map(m -> (String) m.get("content")).forEach(v -> assertTrue(v.contains("443")));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEnhanceWithRemoteAPIAndCustomPort() throws Throwable {
        cs.customProperties.put(ComputeAllocationTaskState.ENABLE_COMPUTE_CONTAINER_HOST_PROP_NAME,
                "true");
        cs.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());
        cs.customProperties.put(ContainerHostService.DOCKER_HOST_PORT_PROP_NAME, "2376");

        cs.descriptionLink = createComputeDescription(null).documentSelfLink;

        TestContext ctx = testCreate(1);
        DeferredResult<ComputeState> result = ComputeStateEnhancers
                .build(host, UriUtils.buildUri(host, "test")).enhance(context,
                        cs);
        result.whenComplete((desc, t) -> {
            if (t != null) {
                ctx.failIteration(t);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        assertNotNull("Expected to have content", context.content);
        Object writeFiles = context.content.get("write_files");
        assertNotNull("Expected to have write-files section", writeFiles);
        @SuppressWarnings({ "rawtypes" })
        List<Object> list = (List) writeFiles;
        assertEquals(4, list.size());

        list.stream()
                .filter(e -> (e instanceof Map))
                .map(e -> (Map<String, Object>) e)
                .filter(m -> "/etc/systemd/system/docker.service.d/docker.conf"
                        .equals(m.get("path")))
                .map(m -> (String) m.get("content")).forEach(v -> assertTrue(v.contains("2376")));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEnhanceWithRemoteAPIAndCustomPortOnCoreOs() throws Throwable {
        context.imageType = "coreos";

        cs.customProperties.put(ComputeAllocationTaskState.ENABLE_COMPUTE_CONTAINER_HOST_PROP_NAME,
                "true");
        cs.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());
        cs.customProperties.put(ContainerHostService.DOCKER_HOST_PORT_PROP_NAME, "2376");
        cs.descriptionLink = createComputeDescription(null).documentSelfLink;

        TestContext ctx = testCreate(1);
        DeferredResult<ComputeState> result = ComputeStateEnhancers
                .build(host, UriUtils.buildUri(host, "test")).enhance(context,
                        cs);
        result.whenComplete((desc, t) -> {
            if (t != null) {
                ctx.failIteration(t);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        assertNotNull("Expected to have content", context.content);
        Object writeFiles = context.content.get("write_files");
        assertNotNull("Expected to have write-files section", writeFiles);
        @SuppressWarnings({ "rawtypes" })
        List<Object> list = (List) writeFiles;
        assertEquals(3, list.size());

        String value = EnhancerUtils.objectMapper()
                .writeValueAsString(context.content.get("coreos"));
        assertTrue(value.contains("ListenStream=2376"));
    }

    @Test
    public void testEnhanceFull() throws Throwable {
        ComputeDescription cd = createComputeDescription(getClientPublicKeyAuth());
        cs.customProperties = cd.customProperties;
        cs.customProperties.put(ComputeAllocationTaskState.ENABLE_COMPUTE_CONTAINER_HOST_PROP_NAME,
                "true");
        cs.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());

        cs.descriptionLink = cd.documentSelfLink;

        TestContext ctx = testCreate(1);
        DeferredResult<ComputeState> result = ComputeStateEnhancers
                .build(host, UriUtils.buildUri(host, "test")).enhance(context,
                        cs);
        result.whenComplete((desc, t) -> {
            if (t != null) {
                ctx.failIteration(t);
                return;
            }
            ctx.completeIteration();
        });
        ctx.await();

        assertNotNull("Expected to have content", context.content);
        assertNotNull("Expected to have authorized keys",
                context.content.get("ssh_authorized_keys"));

        Object writeFiles = context.content.get("write_files");
        assertNotNull("Expected to have write-files section", writeFiles);
        @SuppressWarnings("rawtypes")
        List list = (List) writeFiles;
        assertEquals(4, list.size());
        System.out.println(
                cs.customProperties.get(ComputeConstants.COMPUTE_CONFIG_CONTENT_PROP_NAME));
    }

    public static class TestInitialBootService extends AbstractInitialBootService {
        public static final String SELF_LINK = ManagementUriParts.CONFIG + "/test-initial-boot";

        @Override
        public void handlePost(Operation post) {
            ArrayList<ServiceDocument> states = new ArrayList<>();
            states.addAll(ProfileService.getAllDefaultDocuments());
            initInstances(post, false, true, states.toArray(new ServiceDocument[states.size()]));
        }
    }

    private AuthCredentialsServiceState getClientPublicKeyAuth() throws Throwable {
        AuthCredentialsServiceState state = new AuthCredentialsServiceState();
        state.type = AuthCredentialsType.PublicKey.name();
        state.userEmail = UUID.randomUUID().toString();
        generateKeyPair((key, ssh) -> {
            state.publicKey = KeyUtil.toPEMFormat(key.getPublic());
            state.privateKey = KeyUtil.toPEMFormat(key.getPrivate());
        });
        return doPost(state, AuthCredentialsService.FACTORY_LINK);
    }

    private AuthCredentialsServiceState getClientPublicKeyAuthAndSshKey() throws Throwable {
        AuthCredentialsServiceState state = new AuthCredentialsServiceState();
        state.type = AuthCredentialsType.PublicKey.name();
        state.userEmail = UUID.randomUUID().toString();
        generateKeyPair((key, ssh) -> {
            state.publicKey = KeyUtil.toPEMFormat(key.getPublic());
            state.privateKey = KeyUtil.toPEMFormat(key.getPrivate());
            state.customProperties = new HashMap<>();
            state.customProperties.put(ComputeConstants.CUSTOM_PROP_SSH_AUTHORIZED_KEY_NAME, ssh);
        });
        return doPost(state, AuthCredentialsService.FACTORY_LINK);
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

    private ComputeDescription createComputeDescription(AuthCredentialsServiceState auth)
            throws Throwable {
        ComputeDescription cd = new ComputeDescription();
        cd.authCredentialsLink = auth != null ? auth.documentSelfLink : null;
        cd.customProperties = new HashMap<>();
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_ENABLE_SSH_ACCESS_NAME,
                "true");

        return doPost(cd, ComputeDescriptionService.FACTORY_LINK);
    }
}
