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

package com.vmware.admiral.vic.test.ui.navigation;

import org.junit.Test;

import com.vmware.admiral.test.ui.SelenideClassRunner.Browser;
import com.vmware.admiral.test.ui.SelenideClassRunner.RunWithBrowsers;
import com.vmware.admiral.test.ui.pages.applications.ApplicationsPage;
import com.vmware.admiral.test.ui.pages.containers.ContainersPage;
import com.vmware.admiral.test.ui.pages.networks.NetworksPage;
import com.vmware.admiral.test.ui.pages.templates.TemplatesPage;
import com.vmware.admiral.test.ui.pages.volumes.VolumesPage;
import com.vmware.admiral.vic.test.ui.BaseTest;
import com.vmware.admiral.vic.test.ui.pages.main.VICAdministrationTab;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTab;

/**
 * This test navigates to the home and administration tabs and their child pages and validates the
 * navigation works as expected
 */
public class BasicNavigationTest extends BaseTest {

    @Test
    @RunWithBrowsers({ Browser.FIREFOX, Browser.CHROME })
    public void basicNavigationTest() {
        loginAsAdmin();
        VICHomeTab homeTab = getClient().navigateToHomeTab();

        ApplicationsPage applicationsPage = homeTab.navigateToApplicationsPage();
        applicationsPage.validate(v -> v.validateIsCurrentPage())
                .createTemplate()
                .validate(v -> v.validateIsCurrentPage())
                .navigateBack();
        applicationsPage.validate().validateIsCurrentPage();

        ContainersPage containersPage = homeTab.navigateToContainersPage();
        containersPage.validate(v -> v.validateIsCurrentPage())
                .provisionAContainer()
                .validate(v -> v.validateIsCurrentPage())
                .cancel();
        containersPage.validate().validateIsCurrentPage();

        NetworksPage networksPage = homeTab.navigateToNetworksPage();
        networksPage.validate(v -> v.validateIsCurrentPage())
                .createNetwork()
                .validate(v -> v.validateIsCurrentPage())
                .cancel();
        networksPage.validate().validateIsCurrentPage();

        VolumesPage volumesPage = homeTab.navigateToVolumesPage();
        volumesPage.validate(v -> v.validateIsCurrentPage())
                .createVolume()
                .validate(v -> v.validateIsCurrentPage())
                .cancel();
        volumesPage.validate().validateIsCurrentPage();

        TemplatesPage templatesPage = homeTab.navigateToTemplatesPage();
        templatesPage.validate().validateIsCurrentPage();
        templatesPage.importTemplate()
                .validate(v -> v.validateIsCurrentPage())
                .cancel();
        templatesPage.validate().validateIsCurrentPage();
        templatesPage.createTemplate()
                .validate(v -> v.validateIsCurrentPage())
                .navigateBack();
        templatesPage.validate().validateIsCurrentPage();

        homeTab.navigateToProjectRepositoriesPage()
                .validate()
                .validateIsCurrentPage();
        homeTab.navigateToPublicRepositoriesPage()
                .validate()
                .validateIsCurrentPage();
        homeTab.navigateToContainerHostsPage()
                .validate()
                .validateIsCurrentPage();

        VICAdministrationTab adminTab = getClient().navigateToAdministrationTab();

        adminTab.navigateToIdentityManagementPage()
                .validate()
                .validateIsCurrentPage();
        adminTab.navigateToProjectsPage()
                .validate()
                .validateIsCurrentPage();
        adminTab.navigateToRegistriesPage()
                .validate()
                .validateIsCurrentPage();
        // VBV-1724 after navigating to the Configuration tab logout is not possible the normal way
        // adminTab.navigateToConfigurationPage()
        // .validate()
        // .isCurrentPage();
        adminTab.navigateToLogsPage()
                .validate()
                .validateIsCurrentPage();
        logOut();
    }

}
