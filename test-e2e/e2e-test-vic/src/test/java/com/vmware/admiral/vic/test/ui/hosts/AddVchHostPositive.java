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
public class AddVchHostPositive extends BaseTest {

    private final String VCH_HOST_NAME = "vch_host";

    @Test
    public void testAddHostSucceeds() {
        loginAsAdmin();
        ContainerHostsPage hostsPage = navigateToHomeTab().navigateToContainerHostsPage();
        hostsPage.validate(v -> v.validateHostDoesNotExistWithName(VCH_HOST_NAME))
                .addContainerHost()
                .setName(VCH_HOST_NAME)
                .setDescription(VCH_HOST_NAME)
                .setHostType(HostType.VCH)
                .setUrl(getVchUrl())
                .submit()
                .acceptCertificateIfShownAndExpectSuccess();
        hostsPage.validate(v -> v.validateHostExistsWithName(VCH_HOST_NAME))
                .deleteContainerHost(VCH_HOST_NAME)
                .validate().validateHostDoesNotExistWithName(VCH_HOST_NAME);
        logOut();
    }
}