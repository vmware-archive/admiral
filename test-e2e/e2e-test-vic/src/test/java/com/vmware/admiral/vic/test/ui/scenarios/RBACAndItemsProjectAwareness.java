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
import com.vmware.admiral.test.ui.pages.containers.ContainersPage;
import com.vmware.admiral.test.ui.pages.containers.create.BasicTab;
import com.vmware.admiral.test.ui.pages.networks.CreateNetworkPage;
import com.vmware.admiral.test.ui.pages.networks.NetworksPage;
import com.vmware.admiral.test.ui.pages.projects.configure.members.AddMemberModalDialog;
import com.vmware.admiral.test.ui.pages.projects.configure.members.AddMemberModalDialog.ProjectMemberRole;
import com.vmware.admiral.test.ui.pages.templates.TemplatesPage;
import com.vmware.admiral.test.ui.pages.templates.create.CreateTemplatePage;
import com.vmware.admiral.test.ui.pages.volumes.CreateVolumePage;
import com.vmware.admiral.test.ui.pages.volumes.VolumesPage;
import com.vmware.admiral.test.util.AdmiralEventLogRule;
import com.vmware.admiral.test.util.HostType;
import com.vmware.admiral.test.util.ScreenshotRule;
import com.vmware.admiral.test.util.TestStatusLoggerRule;
import com.vmware.admiral.test.util.host.ContainerHostProviderRule;
import com.vmware.admiral.vic.test.VicTestProperties;
import com.vmware.admiral.vic.test.ui.BaseTestVic;
import com.vmware.admiral.vic.test.util.VICAuthTokenGetter;

/**
 * This test creates two projects, adds users with different roles to the projects, adds non-default
 * cloud admin role to a user, adds content to each project and validates the displayed content is
 * project aware, validates users with different roles see the corresponding views, and are able to
 * perform the corresponding actions.
 *
 * Test steps overview:
 *
 * 1. Login with default system administrator, create projects, add users and groups with different
 * roles to the projects, assign cloud administrator role to a user
 *
 * 2. Login with configured cloud administrator, validate user sees all the tabs. Create content in
 * each project(add host, provision a container, create a nertwork, volume and a tempalte). Validate
 * that the created content is project-aware and is visible only in the project it belongs to
 *
 * 3. Login with project administrator and validate user sees all Home tabs and only the Projects
 * tab under Administration. Validate user can create and remove items
 *
 * 4. Login with project member, validate Administration tab is not accessible, validate user can
 * create and remove items
 *
 * 5. Login with project viewer and validate role restrictions
 *
 * 6. Login with cloud administrator again and delete all the content from all of the projects
 *
 * 7. Login with default system administrator, remove all the projects and unassign the cloud admin
 * role
 */
public class RBACAndItemsProjectAwareness extends BaseTestVic {

    private static final String FIRST_PROJECT_NAME = "rbac-project-one";
    private static final String SECOND_PROJECT_NAME = "rbac-project-two";
    private final String PROJECT_NAME_DEFAULT = "default-project";

    private final String HOST_SUFFIX = "_host";
    private final String NETWORK_SUFFIX = "_network";
    private final String TEMPLATE_SUFFIX = "_template";
    private final String VOLUME_SUFFIX = "_volume";
    private final String CONTAINER_SUFFIX = "_container";

    private final String IMAGE_NAME = "library/alpine";

    private final String USER_SHAUNA = "shauna@coke.sqa-horizon.local";
    private final String USER_SCOTT = "scott@coke.sqa-horizon.local";
    private final String USER_CONNIE = "connie@coke.sqa-horizon.local";

    private final String USER_GROUP_COKE = "coke@coke.sqa-horizon.local";

    private final String CLOUD_ADMIN_JASON = "jason@coke.sqa-horizon.local";

    private final String PASSWORD = "VMware1!";

    private final int REQUEST_TIMEOUT = 120;

    public ContainerHostProviderRule firstProjectProvider = new ContainerHostProviderRule(true, false);

    public ContainerHostProviderRule secondProjectProvider = new ContainerHostProviderRule(true, false);

    @Rule
    public TestRule rules = RuleChain
            .outerRule(new TestStatusLoggerRule())
            .around(firstProjectProvider)
            .around(secondProjectProvider)
            .around(new AdmiralEventLogRule(getVicTarget(), getProjectNames(),
                    () -> VICAuthTokenGetter.getVICAuthToken(getVicTarget(),
                            VicTestProperties.defaultAdminUsername(),
                            VicTestProperties.defaultAdminPassword())))
            .around(new ScreenshotRule());

    @Test
    public void testRbacAndItemProjectAwareness() {

        loginAsAdmin();
        configureCloudAdminRoles();
        logOut();

        loginAs(CLOUD_ADMIN_JASON);
        main().clickAdministrationTabButton();
        ProjectCommons.addProject(getClient(), FIRST_PROJECT_NAME, "This is the first project.",
                false);
        ProjectCommons.addProject(getClient(), SECOND_PROJECT_NAME, "This is the second project.",
                false);
        configureProjects();
        validateWithCloudAdminRoleView();
        addContentToProjects();
        logOut();

        loginAs(USER_SHAUNA);
        validateWithProjectAdminRole();
        logOut();

        loginAs(USER_SCOTT);
        validateWithProjectMember();
        logOut();

        loginAs(USER_CONNIE);
        validateWithProjectViewer();
        logOut();

        loginAs(CLOUD_ADMIN_JASON);
        removeContentFromProjects();
        deleteProjects();
        logOut();

        loginAsAdmin();
        unconfigureCloudAdminRoles();
        logOut();

    }

    private void validateWithCloudAdminRoleView() {
        main().clickHomeTabButton();
        home().validate().validateAllHomeTabsAreAvailable();
        home().validate().validateProjectsAreAvailable(getProjectNames());
        home().validate().validateProjectIsAvailable(PROJECT_NAME_DEFAULT);

        main().clickAdministrationTabButton();
        administration().validate().validateAllAdministrationTabsAreAvailable();
        projects().projectsPage().validate()
                .validateProjectsAreVisible(
                        FIRST_PROJECT_NAME,
                        SECOND_PROJECT_NAME,
                        PROJECT_NAME_DEFAULT);
    }

    private void validateWithProjectAdminRole() {

        home().validate().validateAllHomeTabsAreAvailable();
        home().validate().validateProjectsAreAvailable(getProjectNames());
        home().validate().validateProjectIsNotAvailable(PROJECT_NAME_DEFAULT);
        home().switchToProject(FIRST_PROJECT_NAME);
        home().clickContainerHostsButton();
        clusters().clustersPage().waitToLoad();
        clusters().clustersPage().validate().validateAddHostButtonNotAvailable();
        clusters().clustersPage().validate()
                .validateHostActionsNotAvailable(FIRST_PROJECT_NAME + HOST_SUFFIX);

        String resourcePrefix = USER_SHAUNA.split("@")[0];
        createAndDeleteResourcesInFirstProject(resourcePrefix);

        main().clickAdministrationTabButton();
        projects().projectsPage().waitToLoad();
        administration().validate().validateProjectsAvailable();
        administration().validate().validateIdentityManagementNotAvailable();
        administration().validate().validateRegistriesNotAvailable();
        administration().validate().validateLogsNotAvailable();
        administration().validate().validateConfigurationNotAvailable();

        projects().projectsPage().validate().validateProjectsAreVisible(
                FIRST_PROJECT_NAME,
                SECOND_PROJECT_NAME);
        projects().projectsPage().validate().validateProjectIsNotVisible(PROJECT_NAME_DEFAULT);
        projects().projectsPage().validate().validateAddProjectButtonNotAvailable();
        projects().projectsPage().validate()
                .validateProjectDeleteButtonNotAvailable(FIRST_PROJECT_NAME);
    }

    private void createAndDeleteResourcesInFirstProject(String resourcePrefix) {
        home().switchToProject(FIRST_PROJECT_NAME);
        home().clickContainersButton();
        ContainersPage containersPage = containers().containersPage();
        containersPage.clickCreateContainer();
        containers().createContainerPage().waitToLoad();
        containers().createContainerPage().clickBasicTab();
        containers().basicTab().setName(resourcePrefix + CONTAINER_SUFFIX);
        containers().basicTab().setImage(IMAGE_NAME);
        containers().createContainerPage().submit();

        containers().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);
        containersPage.refresh();
        containersPage.validate()
                .validateContainerExistsWithName(resourcePrefix + CONTAINER_SUFFIX);
        containersPage.deleteContainer(resourcePrefix + CONTAINER_SUFFIX);
        containers().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);
        containersPage.refresh();
        containersPage.validate()
                .validateContainerDoesNotExistWithName(resourcePrefix + CONTAINER_SUFFIX);

        home().clickNetworksButton();
        NetworksPage networksPage = networks().networksPage();
        networksPage.clickCreateNetwork();
        CreateNetworkPage createNetwork = networks().createNetworkPage();
        createNetwork.waitToLoad();
        createNetwork.setName(resourcePrefix + NETWORK_SUFFIX);
        createNetwork.addHostByName(FIRST_PROJECT_NAME + HOST_SUFFIX);
        createNetwork.submit();
        networks().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);
        networksPage.refresh();
        networksPage.validate().validateNetworkExistsWithName(resourcePrefix + NETWORK_SUFFIX);
        networksPage.deleteNetwork(resourcePrefix + NETWORK_SUFFIX);
        networks().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);
        networksPage.refresh();
        networksPage.validate()
                .validateNetworkDoesNotExist(resourcePrefix + NETWORK_SUFFIX);

        home().clickVolumesButton();
        VolumesPage volumesPage = volumes().volumesPage();
        volumesPage.clickCreateVolumeButton();
        CreateVolumePage createVolume = volumes().createVolumePage();
        createVolume.setName(resourcePrefix + VOLUME_SUFFIX);
        createVolume.selectHostByName(FIRST_PROJECT_NAME + HOST_SUFFIX);
        createVolume.submit();
        volumes().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);
        volumesPage.refresh();
        volumesPage.validate().validateVolumeExistsWithName(resourcePrefix + VOLUME_SUFFIX);
        volumesPage.deleteVolume(resourcePrefix + VOLUME_SUFFIX);
        volumes().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);
        volumesPage.refresh();
        volumesPage.validate().validateVolumeDoesNotExistWithName(resourcePrefix + VOLUME_SUFFIX);

        home().clickTemplatesButton();
        TemplatesPage templatesPage = templates().templatesPage();
        templatesPage.clickCreateTemplateButton();
        CreateTemplatePage createTemplate = templates().createTemplatePage();
        createTemplate.waitToLoad();
        createTemplate.setName(resourcePrefix + TEMPLATE_SUFFIX);
        createTemplate.clickProceedButton();

        templates().editTemplatePage().waitToLoad();
        templates().editTemplatePage().clickAddVolumeButton();
        templates().addVolumePage().setName(resourcePrefix + VOLUME_SUFFIX);
        templates().addVolumePage().submit();

        templates().editTemplatePage().waitToLoad();
        templates().editTemplatePage().clickAddNetworkButton();
        templates().addNetworkPage().setName(resourcePrefix + NETWORK_SUFFIX);
        templates().addNetworkPage().submit();

        templates().editTemplatePage().waitToLoad();
        templates().editTemplatePage().clickAddContainerButton();
        templates().selectImagePage().waitToLoad();
        templates().selectImagePage().selectImageByName(IMAGE_NAME);
        templates().basicTab().setName(resourcePrefix + CONTAINER_SUFFIX);
        templates().addContainerPage().clickNetworkTab();
        templates().networkTab().linkNetwork(resourcePrefix + NETWORK_SUFFIX, null, null, null);
        templates().addContainerPage().clickStorageTab();
        templates().storageTab().addVolume(resourcePrefix + VOLUME_SUFFIX, "/container/path",
                false);
        templates().addContainerPage().submit();

        templates().editTemplatePage().waitToLoad();
        templates().editTemplatePage().navigateBack();
        templates().templatesPage().waitToLoad();
        templates().templatesPage().provisionTemplate(resourcePrefix + TEMPLATE_SUFFIX);
        templates().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);
        home().clickApplicationsButton();
        applications().applicationsPage().deleteApplication(resourcePrefix + TEMPLATE_SUFFIX);
        applications().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);
        home().clickContainersButton();
        home().clickApplicationsButton();
        applications().applicationsPage().validate()
                .validateApplicationDoesNotExistWithName(resourcePrefix + TEMPLATE_SUFFIX);
        home().clickTemplatesButton();
        templatesPage.waitToLoad();
        templatesPage.deleteTemplate(resourcePrefix + TEMPLATE_SUFFIX);
        templatesPage.refresh();
        templatesPage.validate()
                .validateTemplateDoesNotExistWithName(resourcePrefix + TEMPLATE_SUFFIX);
    }

    private void validateWithProjectMember() {
        home().validate().validateAllHomeTabsAreAvailable();
        home().validate().validateProjectsAreAvailable(getProjectNames());
        home().validate().validateProjectIsNotAvailable(PROJECT_NAME_DEFAULT);
        main().validate().validateAdministrationTabIsNotVisible();
        home().clickContainerHostsButton();
        clusters().clustersPage().waitToLoad();
        clusters().clustersPage().validate().validateAddHostButtonNotAvailable();
        clusters().clustersPage().validate()
                .validateHostActionsNotAvailable(FIRST_PROJECT_NAME + HOST_SUFFIX);
        String resourcePrefix = USER_SCOTT.split("@")[0];
        createAndDeleteResourcesInFirstProject(resourcePrefix);
    }

    private void validateWithProjectViewer() {
        main().validate().validateAdministrationTabIsNotVisible();
        home().validate().validateApplicationsNotAvailable();
        home().validate().validateContainersNotAvailable();
        home().validate().validateNetworksNotAvailable();
        home().validate().validateVolumesNotAvailable();
        home().validate().validateTemplatesNotAvailable();
        home().validate().validateProjectRepositoriesAvailable();
        home().validate().validatePublicRepositoriesNotAvailable();
        home().validate().validateContainerHostsNotAvailable();
        home().validate().validateProjectIsAvailable(FIRST_PROJECT_NAME);
        home().validate().validateProjectsAreNotAvailable(
                PROJECT_NAME_DEFAULT,
                SECOND_PROJECT_NAME);
    }

    private void configureCloudAdminRoles() {
        main().clickAdministrationTabButton();
        administration().clickIdentityManagementButton();
        identity().identityPage().clickUsersAndGroupsTab();
        identity().usersTab().assignCloudAdminRole(CLOUD_ADMIN_JASON);
    }

    private void unconfigureCloudAdminRoles() {
        main().clickAdministrationTabButton();
        administration().clickIdentityManagementButton();
        identity().identityPage().clickUsersAndGroupsTab();
        identity().usersTab().unassignCloudAdminRole(CLOUD_ADMIN_JASON);
    }

    private void addContentToProjects() {
        main().clickHomeTabButton();
        home().switchToProject(FIRST_PROJECT_NAME);
        home().clickContainerHostsButton();
        HostCommons.addHost(getClient(), FIRST_PROJECT_NAME + HOST_SUFFIX, null,
                firstProjectProvider.getHost().getHostType(),
                getHostAddress(firstProjectProvider.getHost()), true);
        provisionContainerInProject(FIRST_PROJECT_NAME, firstProjectProvider.getHost().getHostType());
        addNetworkToProject(FIRST_PROJECT_NAME);
        addVolumeToProject(FIRST_PROJECT_NAME);
        addTemplateToProject(FIRST_PROJECT_NAME);
        home().switchToProject(SECOND_PROJECT_NAME);
        home().clickContainerHostsButton();
        HostCommons.addHost(getClient(), SECOND_PROJECT_NAME + HOST_SUFFIX, null,
                secondProjectProvider.getHost().getHostType(),
                getHostAddress(secondProjectProvider.getHost()), true);
        provisionContainerInProject(SECOND_PROJECT_NAME, secondProjectProvider.getHost().getHostType());
        addNetworkToProject(SECOND_PROJECT_NAME);
        addVolumeToProject(SECOND_PROJECT_NAME);
        addTemplateToProject(SECOND_PROJECT_NAME);
    }

    private void removeContentFromProjects() {
        for (String projectName : getProjectNames()) {
            home().switchToProject(projectName);
            home().clickApplicationsButton();
            applications().applicationsPage().deleteApplication(projectName + TEMPLATE_SUFFIX);
            applications().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);

            home().clickContainersButton();
            containers().containersPage().waitToLoad();
            containers().containersPage().deleteContainer(projectName + CONTAINER_SUFFIX);
            containers().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);

            home().clickNetworksButton();
            networks().networksPage().waitToLoad();
            networks().networksPage().deleteNetwork(projectName + NETWORK_SUFFIX);
            networks().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);

            home().clickVolumesButton();
            volumes().volumesPage().waitToLoad();
            volumes().volumesPage().deleteVolume(projectName + VOLUME_SUFFIX);
            volumes().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);

            home().clickTemplatesButton();
            templates().templatesPage().waitToLoad();
            templates().templatesPage().deleteTemplate(projectName + TEMPLATE_SUFFIX);

            home().clickContainerHostsButton();
            HostCommons.deleteHost(getClient(), projectName + HOST_SUFFIX);
        }
    }

    private void provisionContainerInProject(String containerName, HostType hostType) {
        home().clickContainersButton();
        containers().containersPage().clickCreateContainer();
        containers().createContainerPage().waitToLoad();
        BasicTab basicTab = containers().basicTab();
        basicTab.setName(containerName + CONTAINER_SUFFIX);
        basicTab.setImage(IMAGE_NAME);

        containers().createContainerPage().submit();
        containers().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);
        containers().containersPage().refresh();
        containers().containersPage().validate()
                .validateContainerExistsWithName(containerName + CONTAINER_SUFFIX);
        if (hostType == HostType.VCH) {
            containers().containersPage().validate().validateContainersCount(1);
        } else {
            containers().containersPage().validate().validateContainersCount(2);
        }
    }

    private void addTemplateToProject(String namePrefix) {
        String templateName = namePrefix + TEMPLATE_SUFFIX;
        String containerName = namePrefix + TEMPLATE_SUFFIX + CONTAINER_SUFFIX;
        String networkName = namePrefix + TEMPLATE_SUFFIX + NETWORK_SUFFIX;
        String volumeName = namePrefix + TEMPLATE_SUFFIX + VOLUME_SUFFIX;
        home().clickTemplatesButton();
        templates().templatesPage().waitToLoad();
        templates().templatesPage().clickCreateTemplateButton();
        templates().createTemplatePage().waitToLoad();
        templates().createTemplatePage().setName(templateName);
        templates().createTemplatePage().clickProceedButton();

        templates().editTemplatePage().waitToLoad();
        templates().editTemplatePage().clickAddNetworkButton();
        templates().addNetworkPage().setName(networkName);
        templates().addNetworkPage().submit();
        templates().editTemplatePage().waitToLoad();
        templates().editTemplatePage().clickAddVolumeButton();
        templates().addVolumePage().setName(volumeName);
        templates().addVolumePage().submit();

        templates().editTemplatePage().waitToLoad();
        templates().editTemplatePage().clickAddContainerButton();
        templates().selectImagePage().waitToLoad();
        templates().selectImagePage().selectImageByName(IMAGE_NAME);
        templates().basicTab().setName(containerName);
        templates().addContainerPage().clickNetworkTab();
        templates().networkTab().linkNetwork(networkName, null, null, null);
        templates().addContainerPage().clickStorageTab();
        templates().storageTab().addVolume(volumeName, "/container/path", false);
        templates().addContainerPage().submit();

        templates().editTemplatePage().waitToLoad();
        templates().editTemplatePage().navigateBack();
        templates().templatesPage().provisionTemplate(templateName);
        templates().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);
        home().clickApplicationsButton();
        applications().applicationsPage().validate()
                .validateApplicationExistsWithName(templateName);
        home().clickContainersButton();
        containers().containersPage().validate().validateContainerExistsWithName(containerName);
        home().clickNetworksButton();
        networks().networksPage().validate().validateNetworkExistsWithName(networkName);
        home().clickVolumesButton();
        volumes().volumesPage().validate().validateVolumeExistsWithName(volumeName);
    }

    private void addNetworkToProject(String networkName) {
        home().clickNetworksButton();
        networks().networksPage().clickCreateNetwork();
        CreateNetworkPage createNetwork = networks().createNetworkPage();
        createNetwork.setName(networkName + NETWORK_SUFFIX);
        createNetwork.addHostByName(networkName + HOST_SUFFIX);
        createNetwork.submit();
        networks().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);
        networks().networksPage().refresh();
        networks().networksPage().validate()
                .validateNetworkExistsWithName(networkName + NETWORK_SUFFIX);
        networks().networksPage().validate().validateNetworksCount(1);
    }

    private void addVolumeToProject(String volumeName) {
        home().clickVolumesButton();
        volumes().volumesPage().clickCreateVolumeButton();
        CreateVolumePage createVolume = volumes().createVolumePage();
        createVolume.waitToLoad();
        createVolume.setName(volumeName + VOLUME_SUFFIX);
        createVolume.selectHostByName(volumeName + HOST_SUFFIX);
        createVolume.submit();
        volumes().requests().waitForLastRequestToSucceed(REQUEST_TIMEOUT);
        volumes().volumesPage().refresh();
        volumes().volumesPage().validate()
                .validateVolumeExistsWithName(volumeName + VOLUME_SUFFIX);
        volumes().volumesPage().validate().validateVolumesCount(1);
    }

    private void configureProjects() {
        projects().projectsPage().clickProjectDetailsButton(FIRST_PROJECT_NAME);
        projects().configureProjectPage().waitToLoad();
        projects().configureProjectPage().clickMembersTabButton();
        projects().membersTab().clickAddMemebersButton();
        AddMemberModalDialog addMemberDialogue = projects().addMemberDialog();
        addMemberDialogue.waitToLoad();
        addMemberDialogue.addMember(USER_SHAUNA);
        addMemberDialogue.setRole(ProjectMemberRole.ADMIN);
        addMemberDialogue.submit();
        projects().membersTab().clickAddMemebersButton();
        addMemberDialogue.waitToLoad();
        addMemberDialogue.addMember(USER_SCOTT);
        addMemberDialogue.setRole(ProjectMemberRole.MEMBER);
        addMemberDialogue.submit();
        projects().membersTab().clickAddMemebersButton();
        addMemberDialogue.waitToLoad();
        addMemberDialogue.addMember(USER_GROUP_COKE);
        addMemberDialogue.setRole(ProjectMemberRole.VIEWER);
        addMemberDialogue.submit();
        administration().clickProjectsButton();
        projects().projectsPage().waitToLoad();

        projects().projectsPage().clickProjectDetailsButton(SECOND_PROJECT_NAME);
        projects().configureProjectPage().waitToLoad();
        projects().configureProjectPage().clickMembersTabButton();
        projects().membersTab().clickAddMemebersButton();
        addMemberDialogue.waitToLoad();
        addMemberDialogue.addMember(USER_SHAUNA);
        addMemberDialogue.setRole(ProjectMemberRole.ADMIN);
        addMemberDialogue.submit();
        projects().membersTab().clickAddMemebersButton();
        addMemberDialogue.waitToLoad();
        addMemberDialogue.addMember(USER_SCOTT);
        addMemberDialogue.setRole(ProjectMemberRole.MEMBER);
        addMemberDialogue.submit();
        administration().clickProjectsButton();
        projects().projectsPage().waitToLoad();
    }

    private void loginAs(String username) {
        loginAs(username, PASSWORD);
    }

    private void deleteProjects() {
        main().clickAdministrationTabButton();
        projects().projectsPage().waitToLoad();
        for (String project : getProjectNames()) {
            ProjectCommons.deleteProject(getClient(), project);
        }
    }

    @Override
    protected List<String> getProjectNames() {
        return Arrays.asList(new String[] {
                FIRST_PROJECT_NAME,
                SECOND_PROJECT_NAME
        });
    }

}
