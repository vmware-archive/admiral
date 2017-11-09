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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.test.ui.pages.projects.ProjectsPage;
import com.vmware.admiral.vic.test.ui.BaseTest;

/**
 * This test verifies that a project is successfully created when the name is valid
 *
 */
public class CreateProjectPositive extends BaseTest {

    private final String PROJECT_NAME = "test_project";

    @Before
    public void setUp() {
        loginAsAdmin();
    }

    @Test
    public void testCreateProjectSucceeds() {
        ProjectsPage projectsPage = getClient().navigateToAdministrationTab()
                .navigateToProjectsPage();
        projectsPage.addProject()
                .setName(PROJECT_NAME)
                .setDescription(PROJECT_NAME)
                .submit()
                .expectSuccess();
        projectsPage.validate().validateProjectIsVisible(PROJECT_NAME);
        getClient().navigateToHomeTab().validate()
                .validateProjectIsAvailable(PROJECT_NAME);
        getClient().navigateToAdministrationTab().navigateToProjectsPage();
        projectsPage.validate().validateProjectIsVisible(PROJECT_NAME);
        projectsPage.deleteProject(PROJECT_NAME)
                .expectSuccess();
    }

    @After
    public void tearDown() {
        logOut();
    }
}
