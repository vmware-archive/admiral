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

import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import com.vmware.admiral.test.ui.commons.HostCommons;
import com.vmware.admiral.test.ui.commons.ProjectCommons;
import com.vmware.admiral.test.ui.commons.VotingAppCommons;
import com.vmware.admiral.test.ui.pages.applications.ApplicationsPage;
import com.vmware.admiral.test.ui.pages.containers.ContainersPage;
import com.vmware.admiral.test.ui.pages.containers.ContainersPage.ContainerState;
import com.vmware.admiral.test.ui.pages.networks.NetworksPage;
import com.vmware.admiral.test.ui.pages.networks.NetworksPage.NetworkState;
import com.vmware.admiral.test.ui.pages.volumes.VolumesPage;
import com.vmware.admiral.test.ui.pages.volumes.VolumesPage.VolumeState;
import com.vmware.admiral.test.util.AdmiralEventLogRule;
import com.vmware.admiral.test.util.HostType;
import com.vmware.admiral.test.util.ScreenshotRule;
import com.vmware.admiral.test.util.TestStatusLoggerRule;
import com.vmware.admiral.test.util.host.ContainerHostProviderRule;
import com.vmware.admiral.vic.test.VicTestProperties;
import com.vmware.admiral.vic.test.ui.BaseTestVic;
import com.vmware.admiral.vic.test.util.VICAuthTokenGetter;

public class CreateAndProvisionVotingApp extends BaseTestVic {

    private static final String PROJECT_NAME = "voting-app-project";

    private final String HOST_NAME = "host";

    private final String TEMPLATE_NAME = "votingApp";

    public ContainerHostProviderRule provider = new ContainerHostProviderRule(true, false);

    @Rule
    public TestRule rules = RuleChain
            .outerRule(new TestStatusLoggerRule())
            .around(provider)
            .around(new AdmiralEventLogRule(getVicTarget(), getProjectNames(),
                    () -> VICAuthTokenGetter.getVICAuthToken(getVicTarget(),
                            VicTestProperties.defaultAdminUsername(),
                            VicTestProperties.defaultAdminPassword())))
            .around(new ScreenshotRule());

    String lookUup;

    @Test
    public void createAndProvisionVotingApp() {
        loginAsAdmin();

        main().clickAdministrationTabButton();
        ProjectCommons.addProject(getClient(), PROJECT_NAME, "Voting app project", false);

        main().clickHomeTabButton();
        home().switchToProject(PROJECT_NAME);
        applications().applicationsPage().waitToLoad();

        home().clickTemplatesButton();
        VotingAppCommons.createVotingAppTemplate(getClient(), TEMPLATE_NAME);

        home().clickContainerHostsButton();
        HostCommons.addHost(getClient(), HOST_NAME, null, provider.getHost().getHostType(),
                getHostAddress(provider.getHost()), null, true);

        home().clickTemplatesButton();
        templates().templatesPage().provisionTemplate(TEMPLATE_NAME);

        verifySuccessfulProvisioning();

        home().clickApplicationsButton();
        ApplicationsPage applicationsPage = applications().applicationsPage();
        applicationsPage.waitToLoad();
        applicationsPage.validate()
                .validateApplicationExistsWithName(TEMPLATE_NAME);

        home().clickNetworksButton();
        NetworksPage networksPage = networks().networksPage();
        networksPage.waitToLoad();
        networksPage.validate()
                .validateNetworkState(VotingAppCommons.NETWORK_NAME_BACK_TIER,
                        NetworkState.CONNECTED);
        networksPage.validate()
                .validateNetworkState(VotingAppCommons.NETWORK_NAME_FRONT_TIER,
                        NetworkState.CONNECTED);

        home().clickVolumesButton();
        VolumesPage volumesPage = volumes().volumesPage();
        volumesPage.waitToLoad();
        volumesPage.validate()
                .validateVolumeState(VotingAppCommons.VOLUME_NAME_DB_DATA, VolumeState.CONNECTED);

        home().clickContainersButton();
        ContainersPage containersPage = containers().containersPage();
        containersPage.waitToLoad();
        containersPage.validate()
                .validateContainerState(VotingAppCommons.WORKER_CONTAINER_NAME,
                        ContainerState.RUNNING);
        containersPage.validate()
                .validateContainerState(VotingAppCommons.REDIS_CONTAINER_NAME,
                        ContainerState.RUNNING);
        containersPage.validate()
                .validateContainerState(VotingAppCommons.DB_CONTAINER_NAME, ContainerState.RUNNING);
        containersPage.validate()
                .validateContainerState(VotingAppCommons.VOTE_CONTAINER_NAME,
                        ContainerState.RUNNING);
        containersPage.validate()
                .validateContainerState(VotingAppCommons.RESULT_CONTAINER_NAME,
                        ContainerState.RUNNING);

        containersPage.inspectContainer(VotingAppCommons.VOTE_CONTAINER_NAME);
        containers().containerStatsPage().waitToLoad();
        List<String> votePortSettings = containers().containerStatsPage()
                .getPortsSettings();
        String votingAddress = extractAddressFromPortSetting(votePortSettings);
        LOG.info("Voting app voting page address resolved at: " + votingAddress);

        home().clickNetworksButton();
        home().clickContainersButton();
        containersPage.waitToLoad();
        containersPage.inspectContainer(VotingAppCommons.RESULT_CONTAINER_NAME);
        containers().containerStatsPage().waitToLoad();
        List<String> resultPortSettings = containers().containerStatsPage()
                .getPortsSettings();
        String resultAddress = extractAddressFromPortSetting(resultPortSettings);
        LOG.info("Voting app results page address resolved at: " + resultAddress);
        logOut();

        // TODO investigate why the voting app is not healthy when provisioned on a VCH
        try {
            VotingAppCommons.voteAndVerify(votingAddress, resultAddress);
        } catch (Throwable e) {
            if (provider.getHost().getHostType() == HostType.VCH) {
                LOG.warning("Voting app results page did not contain the correct votes count");
            } else {
                throw e;
            }
        }

        loginAsAdmin();
        home().switchToProject(PROJECT_NAME);

        applicationsPage.waitToLoad();
        applicationsPage.deleteApplication(TEMPLATE_NAME);
        applications().requests().waitForLastRequestToSucceed(360);

        home().clickTemplatesButton();
        templates().templatesPage().deleteTemplate(TEMPLATE_NAME);
        templates().templatesPage().validate()
                .validateTemplateDoesNotExistWithName(TEMPLATE_NAME);

        home().clickApplicationsButton();
        applicationsPage.waitToLoad();
        applicationsPage.validate()
                .validateApplicationDoesNotExistWithName(TEMPLATE_NAME);

        home().clickContainersButton();
        containersPage.waitToLoad();
        containersPage.validate()
                .validateContainerDoesNotExistWithName(VotingAppCommons.VOTE_CONTAINER_NAME);
        containersPage.validate()
                .validateContainerDoesNotExistWithName(VotingAppCommons.RESULT_CONTAINER_NAME);
        containersPage.validate()
                .validateContainerDoesNotExistWithName(VotingAppCommons.WORKER_CONTAINER_NAME);
        containersPage.validate()
                .validateContainerDoesNotExistWithName(VotingAppCommons.REDIS_CONTAINER_IMAGE);
        containersPage.validate()
                .validateContainerDoesNotExistWithName(VotingAppCommons.DB_CONTAINER_NAME);

        home().clickNetworksButton();
        networksPage.waitToLoad();
        networksPage.validate()
                .validateNetworkDoesNotExist(VotingAppCommons.NETWORK_NAME_BACK_TIER);
        networksPage.validate()
                .validateNetworkDoesNotExist(VotingAppCommons.NETWORK_NAME_FRONT_TIER);

        home().clickVolumesButton();
        volumesPage.waitToLoad();
        volumesPage.validate()
                .validateVolumeDoesNotExistWithName(VotingAppCommons.VOLUME_NAME_DB_DATA);

        home().clickContainerHostsButton();
        HostCommons.deleteHost(getClient(), HOST_NAME);
        main().clickAdministrationTabButton();
        projects().projectsPage().waitToLoad();
        ProjectCommons.deleteProject(getClient(), PROJECT_NAME);
        logOut();
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

    // TODO remove this after VBV-1928 has been fixed
    // replace this method call with:
    // templates().requests().waitForLastRequestToSucceed(1200);
    private void verifySuccessfulProvisioning() {
        try {
            templates().requests().waitForLastRequestToSucceed(1200);
        } catch (Throwable e) {

        }
        try {
            Thread.sleep(12000);
        } catch (InterruptedException e) {
        }
        try {
            templates().requests().waitForLastRequestToSucceed(1500);
        } catch (Throwable e) {

        }
        try {
            Thread.sleep(12000);
        } catch (InterruptedException e) {
        }
        templates().requests().waitForLastRequestToSucceed(1200);
    }

    protected List<String> getProjectNames() {
        return Arrays.asList(new String[] { PROJECT_NAME });
    }
}
