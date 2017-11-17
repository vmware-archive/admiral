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

package com.vmware.admiral.vic.test.ui.projects;

import org.junit.Test;

import com.vmware.admiral.vic.test.ui.BaseTest;

/**
 * This host tries to create a project feedin invalid name values and verifies creation fails
 *
 */
public class CreateProjectNegative extends BaseTest {

    @Test
    public void testCreateProjectFailsWithInvalidName() {
        loginAsAdmin();
        navigateToAdministrationTab().navigateToProjectsPage()
                .addProject()
                .setName("invalid project name")
                .submit(expect -> expect.expectInvalidNameErrorMessage())
                .setName("@@@@")
                .submit(expect -> expect.expectInvalidNameErrorMessage())
                .setName("!!!!")
                .submit(expect -> expect.expectInvalidNameErrorMessage())
                .setName("....")
                .submit(expect -> expect.expectInvalidNameErrorMessage())
                .setName("CAPITAL")
                .submit(expect -> expect.expectInvalidNameErrorMessage())
                .setName("Capital")
                .submit(expect -> expect.expectInvalidNameErrorMessage())
                .setName("___")
                .submit(expect -> expect.expectInvalidNameErrorMessage())
                .setName("a._a")
                .submit(expect -> expect.expectInvalidNameErrorMessage())
                .setName("dotend.")
                .submit(expect -> expect.expectInvalidNameErrorMessage())
                .setName(".startdot")
                .submit(expect -> expect.expectInvalidNameErrorMessage())
                .setName("_startus")
                .submit(expect -> expect.expectInvalidNameErrorMessage())
                .setName("endus_")
                .submit(expect -> expect.expectInvalidNameErrorMessage())
                .cancel();
        logOut();
    }

}
