/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.vic.test.ui.hosts;

import org.junit.Test;

import com.vmware.admiral.test.ui.pages.hosts.AddHostModalDialogue.HostType;
import com.vmware.admiral.test.ui.pages.hosts.ContainerHostsPage;
import com.vmware.admiral.vic.test.ui.BaseTest;

/**
 * This test validates that a container host is added successfully with valid input parameters
 *
 */
public class AddDockerhostPositive extends BaseTest {

    private final String DOCKERHOST_NAME = "Dockerhost";

    @Test
    public void testAddHostSucceeds() {
        loginAsAdmin();
        ContainerHostsPage hostsPage = getClient().navigateToHomeTab()
                .navigateToContainerHostsPage();
        hostsPage.validate().validateHostDoesNotExistWithName(DOCKERHOST_NAME);
        hostsPage.addContainerHost()
                .setName(DOCKERHOST_NAME)
                .setDescription("docker host")
                .setHostType(HostType.DOCKER)
                .setUrl(getDockerhostUrl())
                .submit()
                // .acceptCertificateAndExpectSuccess();
                .expectSuccess();
        hostsPage.validate().validateHostExistsWithName(DOCKERHOST_NAME);
        hostsPage.deleteContainerHost(DOCKERHOST_NAME)
                .validate().validateHostDoesNotExistWithName(DOCKERHOST_NAME);
        logOut();
    }
}