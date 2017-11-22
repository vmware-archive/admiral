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

package com.vmware.admiral.vic.test.ui.containers;

import org.junit.Test;

import com.vmware.admiral.test.ui.pages.containers.ContainersPage;
import com.vmware.admiral.test.ui.pages.hosts.AddHostModalDialogue.HostType;
import com.vmware.admiral.vic.test.ui.BaseTest;

/**
 * This test adds a docker host to the default project, provisions a container and validates the
 * container is provisioned successfully. Then deletes the container and removes the host from the
 * project
 *
 */
public class ProvisionContainer extends BaseTest {

    private final String CONTAINER_NAME = "alpine_container";
    private final String CONTAINER_IMAGE = "alpine";
    private final String HOST_NAME = "vch_host";

    @Test
    public void testAddHostAndProvisionContainer() {
        loginAsAdmin();
        navigateToHomeTab()
                .navigateToContainerHostsPage()
                .addContainerHost()
                .setName(HOST_NAME)
                .setHostType(HostType.VCH)
                .setUrl(getVchUrl())
                .submit()
                .acceptCertificateIfShownAndExpectSuccess();
        ContainersPage containers = navigateToHomeTab().navigateToContainersPage();
        containers.provisionAContainer()
                .navigateToBasicTab()
                .setName(CONTAINER_NAME)
                .setImage(CONTAINER_IMAGE)
                .submit()
                .expectSuccess();
        containers.requests()
                .waitForLastRequestToSucceed(60);
        containers.refresh()
                .validate(v -> v.validateContainerExistsWithName(CONTAINER_NAME))
                .deleteContainer(CONTAINER_NAME)
                .requests()
                .waitForLastRequestToSucceed(60);
        containers.refresh()
                .validate()
                .validateContainerDoesNotExistWithName(CONTAINER_NAME);
        navigateToHomeTab()
                .navigateToContainerHostsPage()
                .deleteContainerHost(HOST_NAME);
        logOut();
    }

}
