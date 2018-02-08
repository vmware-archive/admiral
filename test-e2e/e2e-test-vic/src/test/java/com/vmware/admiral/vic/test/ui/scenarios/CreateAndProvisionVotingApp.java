/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.vic.test.ui.scenarios;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.WebDriverRunner.clearBrowserCache;

import java.util.List;

import com.codeborne.selenide.Condition;

import org.junit.Rule;
import org.junit.Test;
import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.applications.ApplicationsPage;
import com.vmware.admiral.test.ui.pages.clusters.AddClusterModalDialog.HostType;
import com.vmware.admiral.test.ui.pages.containers.ContainersPage;
import com.vmware.admiral.test.ui.pages.containers.ContainersPage.ContainerState;
import com.vmware.admiral.test.ui.pages.networks.NetworksPage;
import com.vmware.admiral.test.ui.pages.networks.NetworksPage.NetworkState;
import com.vmware.admiral.test.ui.pages.projects.AddProjectModalDialog;
import com.vmware.admiral.test.ui.pages.templates.create.EditTemplatePage;
import com.vmware.admiral.test.ui.pages.volumes.VolumesPage;
import com.vmware.admiral.test.ui.pages.volumes.VolumesPage.VolumeState;
import com.vmware.admiral.test.util.AuthContext;
import com.vmware.admiral.vic.test.ui.BaseTest;
import com.vmware.admiral.vic.test.ui.pages.hosts.AddContainerHostModalDialog;
import com.vmware.admiral.vic.test.ui.util.CreateVchRule;

public class CreateAndProvisionVotingApp extends BaseTest {

    private final String HOST_NAME = "host";

    private final String PROJECT_NAME = "voting-app-project";

    private final String TEMPLATE_NAME = "votingApp";
    private final String NETWORK_NAME_BACK_TIER = "back-tier";
    private final String NETWORK_NAME_FRONT_TIER = "front-tier";
    private final String VOLUME_NAME_DB_DATA = "db-data";
    private final String VOLUME_DRIVER_LOCAL = "local";

    private final String RESULT_CONTAINER_IMAGE = "eesprit/voting-app-result";
    private final String RESULT_CONTAINER_NAME = "result";

    private final String WORKER_CONTAINER_IMAGE = "eesprit/voting-app-worker";
    private final String WORKER_CONTAINER_NAME = "worker";

    private final String VOTE_CONTAINER_IMAGE = "eesprit/voting-app-vote";
    private final String VOTE_CONTAINER_NAME = "vote";

    private final String REDIS_CONTAINER_IMAGE = "redis";
    private final String REDIS_CONTAINER_TAG = "alpine";
    private final String REDIS_CONTAINER_NAME = "redis";

    private final String DB_CONTAINER_IMAGE = "postgres";
    private final String DB_CONTAINER_TAG = "9.4";
    private final String DB_CONTAINER_NAME = "db";

    private final AuthContext vicOvaAuthContext = new AuthContext(getVicIp(), getVicVmUsername(),
            getVicVmPassword());
    private final AuthContext vcenterAuthContext = new AuthContext(getVcenterIp(),
            getDefaultAdminUsername(), getDefaultAdminPassword());

    @Rule
    public CreateVchRule vchIps = new CreateVchRule(vicOvaAuthContext, vcenterAuthContext,
            "voting-app-test", 1);

    @Test
    public void createAndProvisionVotingApp() {
        loginAsAdmin();

        main().clickAdministrationTabButton();
        administration().clickProjectsButton();
        projects().projectsPage().clickAddProjectButton();
        AddProjectModalDialog addProjectDialog = projects().addProjectDialog();
        addProjectDialog.waitToLoad();
        addProjectDialog.setName(PROJECT_NAME);
        addProjectDialog.submit();

        main().clickHomeTabButton();
        home().switchToProject(PROJECT_NAME);

        home().clickContainerHostsButton();
        clusters().clustersPage().clickAddClusterButton();

        AddContainerHostModalDialog addHostDialog = clusters().addHostDialog();
        addHostDialog.waitToLoad();
        addHostDialog.setName(HOST_NAME);
        addHostDialog.setHostType(HostType.VCH);
        String vchUrl = getVCHUrl(vchIps.getHostsIps()[0]);
        addHostDialog.setUrl(vchUrl);
        addHostDialog.submit();
        clusters().certificateModalDialog().waitToLoad();
        clusters().certificateModalDialog().submit();

        createVotingAppTemplate();

        templates().templatesPage().provisionTemplate(TEMPLATE_NAME);
        templates().requests().waitForLastRequestToSucceed(720);

        home().clickApplicationsButton();
        ApplicationsPage applicationsPage = applications().applicationsPage();
        applicationsPage.waitToLoad();
        applicationsPage.validate()
                .validateApplicationExistsWithName(TEMPLATE_NAME);

        home().clickNetworksButton();
        NetworksPage networksPage = networks().networksPage();
        networksPage.waitToLoad();
        networksPage.validate()
                .validateNetworkState(NETWORK_NAME_BACK_TIER, NetworkState.CONNECTED);
        networksPage.validate()
                .validateNetworkState(NETWORK_NAME_FRONT_TIER, NetworkState.CONNECTED);

        home().clickVolumesButton();
        VolumesPage volumesPage = volumes().volumesPage();
        volumesPage.waitToLoad();
        volumesPage.validate()
                .validateVolumeState(VOLUME_NAME_DB_DATA, VolumeState.CONNECTED);

        home().clickContainersButton();
        ContainersPage containersPage = containers().containersPage();
        containersPage.waitToLoad();
        containersPage.validate()
                .validateContainerState(WORKER_CONTAINER_NAME, ContainerState.RUNNING);
        containersPage.validate()
                .validateContainerState(REDIS_CONTAINER_NAME, ContainerState.RUNNING);
        containersPage.validate()
                .validateContainerState(DB_CONTAINER_NAME, ContainerState.RUNNING);
        containersPage.validate()
                .validateContainerState(VOTE_CONTAINER_NAME, ContainerState.RUNNING);
        containersPage.validate()
                .validateContainerState(RESULT_CONTAINER_NAME, ContainerState.RUNNING);

        containersPage.inspectContainer(VOTE_CONTAINER_NAME);
        containers().containerStatsPage().waitToLoad();
        List<String> votePortSettings = containers().containerStatsPage()
                .getPortsSettings();
        home().clickNetworksButton();
        home().clickContainersButton();
        containersPage.waitToLoad();
        containersPage.inspectContainer(RESULT_CONTAINER_NAME);
        containers().containerStatsPage().waitToLoad();
        List<String> resultPortSettings = containers().containerStatsPage()
                .getPortsSettings();

        String votingAddress = extractAddressFromPortSetting(votePortSettings);
        String resultAddress = extractAddressFromPortSetting(resultPortSettings);
        logOut();

        voteAndVerify(votingAddress, resultAddress);

        loginAsAdmin();
        home().switchToProject(PROJECT_NAME);

        home().clickTemplatesButton();
        templates().templatesPage().deleteTemplate(TEMPLATE_NAME);
        templates().templatesPage().validate()
                .validateTemplateDoesNotExistWithName(TEMPLATE_NAME);

        home().clickApplicationsButton();
        applicationsPage.waitToLoad();
        applicationsPage.deleteApplication(TEMPLATE_NAME);
        applications().requests().waitForLastRequestToSucceed(360);
        applicationsPage.refresh();
        applicationsPage.validate()
                .validateApplicationDoesNotExistWithName(TEMPLATE_NAME);

        home().clickContainersButton();
        containersPage.waitToLoad();
        containersPage.validate().validateContainerDoesNotExistWithName(VOTE_CONTAINER_NAME);
        containersPage.validate().validateContainerDoesNotExistWithName(RESULT_CONTAINER_NAME);
        containersPage.validate().validateContainerDoesNotExistWithName(WORKER_CONTAINER_NAME);
        containersPage.validate().validateContainerDoesNotExistWithName(REDIS_CONTAINER_IMAGE);
        containersPage.validate().validateContainerDoesNotExistWithName(DB_CONTAINER_NAME);

        home().clickNetworksButton();
        networksPage.waitToLoad();
        networksPage.validate().validateNetworkDoesNotExist(NETWORK_NAME_BACK_TIER);
        networksPage.validate().validateNetworkDoesNotExist(NETWORK_NAME_FRONT_TIER);

        home().clickVolumesButton();
        volumesPage.waitToLoad();
        volumesPage.validate().validateVolumeDoesNotExistWithName(VOLUME_NAME_DB_DATA);

        home().clickContainerHostsButton();
        clusters().clustersPage().clickHostDeleteButton(HOST_NAME);
        clusters().deleteHostDialog().waitToLoad();
        clusters().deleteHostDialog().submit();
        main().clickAdministrationTabButton();
        projects().projectsPage().waitToLoad();
        projects().projectsPage().clickProjectDeleteButton(PROJECT_NAME);
        projects().deleteProjectDialog().waitToLoad();
        projects().deleteProjectDialog().submit();
        logOut();
    }

    public void createVotingAppTemplate() {

        home().clickTemplatesButton();
        templates().templatesPage().clickCreateTemplateButton();
        templates().createTemplatePage().setName(TEMPLATE_NAME);
        templates().createTemplatePage().clickProceedButton();
        EditTemplatePage editTemplate = templates().editTemplatePage();

        editTemplate.waitToLoad();
        editTemplate.clickAddNetworkButton();
        templates().addNetworkPage().setName(NETWORK_NAME_BACK_TIER);
        templates().addNetworkPage().submit();

        editTemplate.waitToLoad();
        editTemplate.clickAddNetworkButton();
        templates().addNetworkPage().setName(NETWORK_NAME_FRONT_TIER);
        templates().addNetworkPage().submit();

        editTemplate.waitToLoad();
        editTemplate.clickAddVolumeButton();
        templates().addVolumePage().setName(VOLUME_NAME_DB_DATA);
        templates().addVolumePage().setDriver(VOLUME_DRIVER_LOCAL);
        templates().addVolumePage().submit();

        editTemplate.waitToLoad();
        editTemplate.clickAddContainerButton();
        templates().selectImagePage().waitToLoad();
        templates().selectImagePage().selectImageByName("library/alpine");
        templates().basicTab().setImage(RESULT_CONTAINER_IMAGE);
        templates().basicTab().setName(RESULT_CONTAINER_NAME);
        templates().basicTab().addCommand("nodemon --debug server.js");

        templates().addContainerPage().clickNetworkTab();
        templates().networkTab().addPortBinding(null, "80");
        templates().networkTab().addPortBinding(null, "5858");
        templates().networkTab().linkNetwork(NETWORK_NAME_FRONT_TIER, null, null, null);
        templates().networkTab().linkNetwork(NETWORK_NAME_BACK_TIER, null, null, null);
        templates().addContainerPage().submit();

        editTemplate.waitToLoad();
        editTemplate.clickAddContainerButton();
        templates().selectImagePage().waitToLoad();
        templates().selectImagePage().selectImageByName("library/alpine");
        templates().basicTab().setImage(WORKER_CONTAINER_IMAGE);
        templates().basicTab().setName(WORKER_CONTAINER_NAME);
        templates().addContainerPage().clickNetworkTab();
        templates().networkTab().linkNetwork(NETWORK_NAME_BACK_TIER, null, null, null);
        templates().addContainerPage().submit();

        editTemplate.waitToLoad();
        editTemplate.clickAddContainerButton();
        templates().selectImagePage().waitToLoad();
        templates().selectImagePage().selectImageByName("library/alpine");
        templates().basicTab().setImage(VOTE_CONTAINER_IMAGE);
        templates().basicTab().setName(VOTE_CONTAINER_NAME);
        templates().basicTab().addCommand("python app.py");

        templates().addContainerPage().clickNetworkTab();
        templates().networkTab().addPortBinding(null, "80");
        templates().networkTab().linkNetwork(NETWORK_NAME_FRONT_TIER, null, null, null);
        templates().networkTab().linkNetwork(NETWORK_NAME_BACK_TIER, null, null, null);
        templates().addContainerPage().submit();

        editTemplate.waitToLoad();
        editTemplate.clickAddContainerButton();
        templates().selectImagePage().waitToLoad();
        templates().selectImagePage().selectImageByName("library/alpine");
        templates().basicTab().setImage(REDIS_CONTAINER_IMAGE);
        templates().basicTab().setTag(REDIS_CONTAINER_TAG);
        templates().basicTab().setName(REDIS_CONTAINER_NAME);

        templates().addContainerPage().clickNetworkTab();
        templates().networkTab().addPortBinding(null, "6379");
        templates().networkTab().linkNetwork(NETWORK_NAME_BACK_TIER, null, null, null);
        templates().addContainerPage().submit();

        editTemplate.waitToLoad();
        editTemplate.clickAddContainerButton();
        templates().selectImagePage().waitToLoad();
        templates().selectImagePage().selectImageByName("library/alpine");
        templates().basicTab().setImage(DB_CONTAINER_IMAGE);
        templates().basicTab().setTag(DB_CONTAINER_TAG);
        templates().basicTab().setName(DB_CONTAINER_NAME);
        templates().addContainerPage().clickNetworkTab();
        templates().networkTab().linkNetwork(NETWORK_NAME_BACK_TIER, null, null, null);
        templates().addContainerPage().clickStorageTab();
        templates().storageTab().addVolume("db-data", "/var/lib/postgresql/data", false);
        templates().addContainerPage().submit();

        templates().editTemplatePage().navigateBack();
    }

    private void voteAndVerify(String votingTarget, String resultTarget) {
        LOG.info("Opening voting app voting page");
        open(votingTarget);
        LOG.info("Voting for cats");
        $(By.cssSelector("#a")).click();
        clearBrowserCache();
        sleep(1000);
        LOG.info("Opening the votig app results page");
        open(resultTarget);
        LOG.info("Validating voting results");
        $(By.cssSelector("#result>span")).shouldHave(Condition.exactText("1 vote"));
        $(By.cssSelector(".choice.cats .stat.ng-binding"))
                .shouldHave(Condition.exactText("100.0%"));
    }

    protected String extractAddressFromPortSetting(List<String> portSettings) {
        if (portSettings.isEmpty()) {
            throw new RuntimeException(
                    "Voting app container did not contain port settings, unable to extract the voting app address.");
        }
        String portSetting = portSettings.stream().filter(p -> p.endsWith(":80/tcp"))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Could not extract voting app address from port settings: "
                                + portSettings));
        return portSetting.substring(0, portSetting.lastIndexOf(":"));
    }
}
