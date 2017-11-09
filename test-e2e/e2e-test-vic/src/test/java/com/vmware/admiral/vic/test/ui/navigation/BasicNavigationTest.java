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

import com.vmware.admiral.test.ui.pages.SelenideClassRunner.Browser;
import com.vmware.admiral.test.ui.pages.SelenideClassRunner.RunWithBrowsers;
import com.vmware.admiral.test.ui.pages.applications.ApplicationsPage;
import com.vmware.admiral.test.ui.pages.containers.ContainersPage;
import com.vmware.admiral.test.ui.pages.containers.provision.ProvisionAContainerPage;
import com.vmware.admiral.test.ui.pages.networks.CreateNetworkPage;
import com.vmware.admiral.test.ui.pages.networks.NetworksPage;
import com.vmware.admiral.test.ui.pages.templates.CreateTemplatePage;
import com.vmware.admiral.test.ui.pages.templates.ImportTemplatePage;
import com.vmware.admiral.test.ui.pages.templates.TemplatesPage;
import com.vmware.admiral.test.ui.pages.volumes.CreateVolumePage;
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
        applicationsPage.validate().validateIsCurrentPage();
        CreateTemplatePage createTemplatePage = applicationsPage.createTemplate();
        createTemplatePage.validate().validateIsCurrentPage();
        createTemplatePage.navigateBack();
        applicationsPage.validate().validateIsCurrentPage();

        ContainersPage containersPage = homeTab.navigateToContainersPage();
        containersPage.validate().validateIsCurrentPage();
        ProvisionAContainerPage provisionPage = containersPage.provisionAContainer();
        provisionPage.validate().validateIsCurrentPage();
        provisionPage.cancel();
        containersPage.validate().validateIsCurrentPage();

        NetworksPage networksPage = homeTab.navigateToNetworksPage();
        networksPage.validate().validateIsCurrentPage();
        CreateNetworkPage createNetworkPage = networksPage.createNetwork();
        createNetworkPage.validate().validateIsCurrentPage();
        createNetworkPage.cancel();
        networksPage.validate().validateIsCurrentPage();

        VolumesPage volumesPage = homeTab.navigateToVolumesPage();
        volumesPage.validate().validateIsCurrentPage();
        CreateVolumePage createVolumePage = volumesPage.createVolume();
        createVolumePage.validate().validateIsCurrentPage();
        createVolumePage.cancel();
        volumesPage.validate().validateIsCurrentPage();

        TemplatesPage templatesPage = homeTab.navigateToTemplatesPage();
        templatesPage.validate().validateIsCurrentPage();
        ImportTemplatePage importTemplatePage = templatesPage.importTemplate();
        importTemplatePage.validate().validateIsCurrentPage();
        importTemplatePage.cancel();
        templatesPage.validate().validateIsCurrentPage();
        createTemplatePage = templatesPage.createTemplate();
        createTemplatePage.validate().validateIsCurrentPage();
        createTemplatePage.navigateBack();
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
