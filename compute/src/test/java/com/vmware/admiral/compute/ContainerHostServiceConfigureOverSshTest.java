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

package com.vmware.admiral.compute;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ConfigureHostOverSshTaskService.ConfigureHostOverSshTaskServiceState;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.admiral.host.CaSigningCertService;
import com.vmware.admiral.host.ComputeInitialBootService;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.admiral.service.test.MockConfigureHostOverSshTaskService;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public class ContainerHostServiceConfigureOverSshTest extends ComputeBaseTest {

    private MockDockerHostAdapterService dockerAdapterService;
    private MockConfigureHostOverSshTaskService configureHostOverSshTaskService;

    private AuthCredentialsService authCredentialsService;

    @Override
    @Before
    public void beforeForComputeBase() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);
        host.registerForServiceAvailability(CaSigningCertService.startTask(host), true,
                CaSigningCertService.FACTORY_LINK);
        HostInitPhotonModelServiceConfig.startServices(host);
        HostInitTestDcpServicesConfig.startServices(host);
        HostInitCommonServiceConfig.startServices(host);
        HostInitComputeServicesConfig.startServices(host, true);

        waitForServiceAvailability(ComputeInitialBootService.SELF_LINK);
        waitForInitialBootServiceToBeSelfStopped(ComputeInitialBootService.SELF_LINK);
        waitForServiceAvailability(CaSigningCertService.FACTORY_LINK);

        dockerAdapterService = new MockDockerHostAdapterService();
        host.startService(Operation.createPost(UriUtils.buildUri(host,
                MockDockerHostAdapterService.class)), dockerAdapterService);
        waitForServiceAvailability(MockDockerHostAdapterService.SELF_LINK);
    }

    @Test
    public void testPutWithConfigureOverSsh() throws Throwable {
        AuthCredentialsServiceState authCreds = createCredentials();

        ContainerHostSpec hostSpec = ContainerHostServiceTest.createContainerHostSpec(
                new ArrayList<>(),
                ContainerHostServiceTest.SECOND_COMPUTE_DESC_ID);
        hostSpec.hostState.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                authCreds.documentSelfLink);
        hostSpec.isConfigureOverSsh = true;

        ConfigureHostOverSshTaskServiceState finalState = createContainerHostSpecOverSsh(hostSpec,
                false);
        Assert.assertEquals(ConfigureHostOverSshTaskServiceState.SubStage.COMPLETED,
                finalState.taskSubStage);

        List<ComputeState> hosts = getHosts();
        Assert.assertEquals("Only 1 host expected", 1, hosts.size());
        ComputeState h = hosts.get(0);
        Assert.assertEquals("Incorrect adress",
                "https://" + ContainerHostServiceTest.COMPUTE_ADDRESS + ":443",
                h.address);
    }

    @Test
    public void testValidateConfigureOverSsh() throws Throwable {
        AuthCredentialsServiceState authCreds = createCredentials();

        ContainerHostSpec hostSpec = ContainerHostServiceTest.createContainerHostSpec(
                new ArrayList<>(),
                ContainerHostServiceTest.SECOND_COMPUTE_DESC_ID);
        hostSpec.hostState.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                authCreds.documentSelfLink);
        hostSpec.isConfigureOverSsh = true;

        createContainerHostSpecOverSsh(hostSpec,
                true);

        List<ComputeState> hosts = getHosts();
        Assert.assertEquals("No host expected", 0, hosts.size());
    }

    @After
    public void tearDown() throws Throwable {
        stopService(dockerAdapterService);
        stopService(configureHostOverSshTaskService);
        stopService(authCredentialsService);
    }

    private ConfigureHostOverSshTaskServiceState createContainerHostSpecOverSsh(
            ContainerHostSpec hostSpec, boolean validate) throws Throwable {
        AtomicReference<ConfigureHostOverSshTaskServiceState> state = new AtomicReference<>(
                null);

        URI uri;
        if (validate) {
            uri = UriUtils.buildUri(host, ContainerHostService.SELF_LINK,
                    ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);
        } else {
            uri = UriUtils.buildUri(host, ContainerHostService.SELF_LINK);
        }

        Operation getCompositeDesc = Operation.createPut(
                uri)
                .setBody(hostSpec)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.log("Exception while processing the container host: {}.",
                                        Utils.toString(e));
                                host.failIteration(e);
                                return;
                            } else {
                                if (!validate) {
                                    state.set(
                                            o.getBody(ConfigureHostOverSshTaskServiceState.class));
                                } else {
                                    Assert.assertNull(o.getBodyRaw());
                                    Assert.assertEquals(HttpURLConnection.HTTP_NO_CONTENT,
                                            o.getStatusCode());
                                }
                                host.completeIteration();
                            }
                        });
        host.testStart(1);
        host.send(getCompositeDesc);
        host.testWait();

        if (validate) {
            return null;
        }

        return waitForTaskCompletion(
                state.get().documentSelfLink, ConfigureHostOverSshTaskServiceState.class);
    }

    private AuthCredentialsServiceState createCredentials() throws Throwable {
        AuthCredentialsServiceState creds = new AuthCredentialsServiceState();
        creds.userEmail = "test";
        creds.privateKey = "test";
        creds.type = "Password";
        return doPost(creds, AuthCredentialsService.FACTORY_LINK);
    }

    public List<ComputeState> getHosts() throws Throwable {
        List<ComputeState> result = new ArrayList<>();
        AtomicReference<Throwable> t = new AtomicReference<>(null);
        TestContext ctx = testCreate(1);

        QuerySpecification qs = new QuerySpecification();
        qs.query = Query.Builder.create().addKindFieldClause(ComputeState.class).build();
        QueryTask qt = QueryTask.create(qs);
        QuerySpecification.addExpandOption(qt);

        new ServiceDocumentQuery<ComputeState>(
                host, ComputeState.class).query(qt,
                        (r) -> {
                            if (r.hasException()) {
                                ctx.fail(r.getException());
                                return;
                            }

                            if (r.hasResult()) {
                                result.add(r.getResult());
                                return;
                            }

                            ctx.completeIteration();
                        });
        ctx.await();

        Assert.assertNull("Failed to fetch hosts", t.get());
        return result;
    }
}
