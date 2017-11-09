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
import com.vmware.admiral.vic.test.ui.BaseTest;

/**
 * This test tries to add a container host feeding the dialogue with invalid input values and
 * validates the host addition fails
 */
public class AddHostNegative extends BaseTest {

    @Test
    public void testAddHostFailsWithInvalidUrlValues() {
        loginAsAdmin();
        getClient().navigateToHomeTab().navigateToContainerHostsPage()
                .addContainerHost()
                .setName("InvalidHost")
                .setDescription("bad host url")
                .setHostType(HostType.VCH)
                .setUrl("123123.123123")
                .submit(expect -> expect.errorMessage("URI syntax error"))
                .setUrl("Invalid url")
                .submit(expect -> expect.errorMessage("Invalid host address"))
                .setUrl("1")
                .submit(expect -> expect.errorMessage("Connection refused"))
                .setUrl("http://asd.asd.asd.asd:asd")
                .submit(expect -> expect.expectFailure())
                .setUrl(" ")
                .submit(expect -> expect.expectFailure())
                .submit(expect -> expect.errorMessage("Invalid host address"))
                .setUrl("@@@@@@@@@@@@@")
                .submit(expect -> expect.errorMessage("URI syntax error"))
                .cancel();
        logOut();
    }

}