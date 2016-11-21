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

package com.vmware.admiral.test.integration;

import static com.vmware.admiral.test.integration.SshUtilIT.SSH_HOST;
import static com.vmware.admiral.test.integration.SshUtilIT.getPasswordCredentials;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.docker.mock.MockConfigureHostOverSshTaskService;
import com.vmware.admiral.adapter.docker.service.ConfigureHostOverSshTaskService;
import com.vmware.admiral.adapter.docker.service.ConfigureHostOverSshTaskService.SetupOverSshServiceState;
import com.vmware.admiral.adapter.docker.service.DockerHostAdapterService;
import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.ElasticPlacementZoneService;
import com.vmware.admiral.host.CaSigningCertService;
import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustImportService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class ConfigureHostOverSshTaskServiceIT extends BaseTestCase {

    @Before
    public void setup() throws Throwable {
        // Pool Services
        host.startFactory(new ResourcePoolService());
        host.startService(new ElasticPlacementZoneConfigurationService());
        host.startFactory(new ElasticPlacementZoneService());
        waitForServiceAvailability(ResourcePoolService.FACTORY_LINK);
        waitForServiceAvailability(ElasticPlacementZoneService.FACTORY_LINK);
        waitForServiceAvailability(ElasticPlacementZoneConfigurationService.SELF_LINK);

        // Compute Service
        host.startFactory(new ComputeService());
        host.startFactory(new ComputeDescriptionService());
        host.startFactory(new SslTrustCertificateService());
        host.startService(new SslTrustImportService());
        host.startService(new ContainerHostService());
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ComputeDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(SslTrustCertificateService.FACTORY_LINK);
        waitForServiceAvailability(SslTrustImportService.SELF_LINK);
        waitForServiceAvailability(ContainerHostService.SELF_LINK);

        // Others
        host.startService(new DockerHostAdapterService());
        host.startFactory(new EventLogService());
        host.startService(new ConfigurationFactoryService());
        host.startService(new CaSigningCertService());
        waitForServiceAvailability(DockerHostAdapterService.SELF_LINK);
        waitForServiceAvailability(EventLogService.FACTORY_LINK);
        waitForServiceAvailability(ConfigurationFactoryService.SELF_LINK);
        waitForServiceAvailability(CaSigningCertService.SELF_LINK);

        host.startFactory(new MockConfigureHostOverSshTaskService());
        waitForServiceAvailability(MockConfigureHostOverSshTaskService.FACTORY_LINK);
    }

    @Test
    public void test() throws Throwable {
        AuthCredentialsServiceState sshCreds = getPasswordCredentials();
        sshCreds = doPost(sshCreds, AuthCredentialsService.FACTORY_LINK);

        ResourcePoolState resourcePool = new ResourcePoolState();
        resourcePool.name = "test";

        ElasticPlacementZoneConfigurationState placementZone = new ElasticPlacementZoneConfigurationState();
        placementZone.resourcePoolState = resourcePool;
        placementZone = doPost(placementZone, ElasticPlacementZoneConfigurationService.SELF_LINK);

        SetupOverSshServiceState state = new ConfigureHostOverSshTaskService.SetupOverSshServiceState();
        state.address = SSH_HOST;
        state.port = 2376;
        state.authCredentialsLink = sshCreds.documentSelfLink;
        state.placementZoneLink = placementZone.documentSelfLink;

        state = doPost(state, ConfigureHostOverSshTaskService.FACTORY_LINK);
        state = waitForFinalState(state, 10, TimeUnit.MINUTES);

        Assert.assertEquals("Task failed", TaskStage.FINISHED, state.taskInfo.stage);
    }

    @Test
    public void testNoAddress() throws Throwable {
        AuthCredentialsServiceState sshCreds = getPasswordCredentials();
        sshCreds = doPost(sshCreds, AuthCredentialsService.FACTORY_LINK);

        ResourcePoolState resourcePool = new ResourcePoolState();
        resourcePool.name = "test";

        ElasticPlacementZoneConfigurationState placementZone = new ElasticPlacementZoneConfigurationState();
        placementZone.resourcePoolState = resourcePool;
        placementZone = doPost(placementZone, ElasticPlacementZoneConfigurationService.SELF_LINK);

        SetupOverSshServiceState state = new ConfigureHostOverSshTaskService.SetupOverSshServiceState();
        state.port = 2376;
        state.authCredentialsLink = sshCreds.documentSelfLink;
        state.placementZoneLink = placementZone.documentSelfLink;

        state = doPost(state, ConfigureHostOverSshTaskService.FACTORY_LINK);
        state = waitForFinalState(state, 1, TimeUnit.MINUTES);

        Assert.assertEquals(TaskStage.FAILED, state.taskInfo.stage);
        Assert.assertEquals(ConfigureHostOverSshTaskService.ADDRESS_NOT_SET_ERROR_MESSAGE,
                state.taskInfo.failure.message);
    }

    @Test
    public void testNoPort() throws Throwable {
        AuthCredentialsServiceState sshCreds = getPasswordCredentials();
        sshCreds = doPost(sshCreds, AuthCredentialsService.FACTORY_LINK);

        ResourcePoolState resourcePool = new ResourcePoolState();
        resourcePool.name = "test";

        ElasticPlacementZoneConfigurationState placementZone = new ElasticPlacementZoneConfigurationState();
        placementZone.resourcePoolState = resourcePool;
        placementZone = doPost(placementZone, ElasticPlacementZoneConfigurationService.SELF_LINK);

        SetupOverSshServiceState state = new ConfigureHostOverSshTaskService.SetupOverSshServiceState();
        state.address = SSH_HOST;
        state.authCredentialsLink = sshCreds.documentSelfLink;
        state.placementZoneLink = placementZone.documentSelfLink;

        state = doPost(state, ConfigureHostOverSshTaskService.FACTORY_LINK);
        state = waitForFinalState(state, 1, TimeUnit.MINUTES);

        Assert.assertEquals(TaskStage.FAILED, state.taskInfo.stage);
        Assert.assertEquals(ConfigureHostOverSshTaskService.PORT_NOT_SET_ERROR_MESSAGE,
                state.taskInfo.failure.message);
    }

    private SetupOverSshServiceState waitForFinalState(
            SetupOverSshServiceState state, long timeout, TimeUnit unit)
            throws Throwable {
        long timeoutTime = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < timeoutTime &&
                !state.taskInfo.stage.equals(TaskStage.FINISHED) &&
                !state.taskInfo.stage.equals(TaskStage.FAILED)) {
            Thread.sleep(5000);
            state = getDocument(SetupOverSshServiceState.class, state.documentSelfLink);
        }

        if (System.currentTimeMillis() > timeoutTime) {
            throw new TimeoutException("Task did not finish on time!");
        }

        return state;
    }
}
