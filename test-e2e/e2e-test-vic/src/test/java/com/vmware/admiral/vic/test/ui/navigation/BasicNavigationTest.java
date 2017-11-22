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
import com.vmware.admiral.test.ui.pages.templates.TemplatesPage;
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
        VICHomeTab homeTab = navigateToHomeTab();

        homeTab.navigateToApplicationsPage()
                .createTemplate()
                .navigateBack();
        homeTab.navigateToContainersPage()
                .provisionAContainer()
                .cancel();
        homeTab.navigateToNetworksPage()
                .createNetwork()
                .cancel();
        homeTab.navigateToVolumesPage()
                .createVolume()
                .cancel();
        TemplatesPage templatesPage = homeTab.navigateToTemplatesPage();
        templatesPage.importTemplate()
                .cancel();
        templatesPage.createTemplate()
                .navigateBack();
        homeTab.navigateToProjectRepositoriesPage();
        homeTab.navigateToPublicRepositoriesPage();
        homeTab.navigateToContainerHostsPage();

        VICAdministrationTab adminTab = navigateToAdministrationTab();

        adminTab.navigateToIdentityManagementPage();
        adminTab.navigateToProjectsPage();
        adminTab.navigateToRegistriesPage();
        adminTab.navigateToConfigurationPage();
        adminTab.navigateToLogsPage();
    }

}
