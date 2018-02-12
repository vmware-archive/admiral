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

import com.vmware.admiral.test.ui.pages.clusters.AddClusterModalDialog;
import com.vmware.admiral.test.ui.pages.clusters.AddClusterModalDialog.HostType;
import com.vmware.admiral.test.ui.pages.containers.ContainersPage;
import com.vmware.admiral.test.ui.pages.containers.create.BasicTab;
import com.vmware.admiral.test.ui.pages.networks.CreateNetworkPage;
import com.vmware.admiral.test.ui.pages.networks.NetworksPage;
import com.vmware.admiral.test.ui.pages.projects.AddProjectModalDialog;
import com.vmware.admiral.test.ui.pages.projects.configure.members.AddMemberModalDialog;
import com.vmware.admiral.test.ui.pages.projects.configure.members.AddMemberModalDialog.ProjectMemberRole;
import com.vmware.admiral.test.ui.pages.templates.TemplatesPage;
import com.vmware.admiral.test.ui.pages.templates.create.CreateTemplatePage;
import com.vmware.admiral.test.ui.pages.volumes.CreateVolumePage;
import com.vmware.admiral.test.ui.pages.volumes.VolumesPage;
import com.vmware.admiral.test.util.AuthContext;
import com.vmware.admiral.vic.test.ui.BaseTest;
import com.vmware.admiral.vic.test.ui.util.CreateVchRule;

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
public class RBACAndItemsProjectAwareness extends BaseTest {

    private final String PROJECT_NAME_ADMIRAL = "admiral";
    private final String PROJECT_NAME_QE = "quality-engineering";
    private final String PROJECT_NAME_DEFAULT = "default-project";

    private final String HOST_SUFFIX = "_host";
    private final String NETWORK_SUFFIX = "_network";
    private final String TEMPLATE_SUFFIX = "_template";
    private final String VOLUME_SUFFIX = "_volume";
    private final String CONTAINER_SUFFIX = "_container";

    private final String IMAGE_NAME = "alpine";

    private final String USER_SHAUNA = "shauna@coke.sqa-horizon.local";
    private final String USER_ISTOYANOV = "istoyanov@vcac.sqa-horizon.local";
    private final String USER_SCOTT = "scott@coke.sqa-horizon.local";
    private final String USER_SERGEY = "sergey@coke.sqa-horizon.local";
    private final String USER_AKRACHEVA = "akracheva@vcac.sqa-horizon.local";
    private final String USER_CONNIE = "connie@coke.sqa-horizon.local";

    private final String USER_GROUP_COKE = "coke@coke.sqa-horizon.local";
    private final String USER_GROUP_SOFIA_DEV_TEAM = "sofiadevteam@vcac.sqa-horizon.local";
    private final String USER_GROUP_SOFIA_QE_TEAM = "sofiaqeteam@vcac.sqa-horizon.local";

    private final String CLOUD_ADMIN_JASON = "jason@coke.sqa-horizon.local";

    private final String PASSWORD = "VMware1!";

    private final int REQUEST_TIMEOUT = 120;

    private final List<String> ALL_PROJECTS = Arrays.asList(new String[] {
            PROJECT_NAME_ADMIRAL,
            PROJECT_NAME_QE
    });

    private final AuthContext vicOvaAuthContext = new AuthContext(getVicIp(), getVicVmUsername(),
            getVicVmPassword());
    private final AuthContext vcenterAuthContext = new AuthContext(getVcenterIp(),
            getDefaultAdminUsername(), getDefaultAdminPassword());

    @Rule
    public CreateVchRule vchIps = new CreateVchRule(vicOvaAuthContext, vcenterAuthContext,
            "rbac-test", 2);

    @Test
    public void testRbacAndItemProjectAwareness() {

        loginAsAdmin();
        createProjects();
        configureProjects();
        configureCloudAdminRoles();
        logOut();

        loginAs(CLOUD_ADMIN_JASON);
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
        logOut();

        loginAsAdmin();
        deleteProjects();
        unconfigureCloudAdminRoles();
        logOut();

    }

    private void validateWithCloudAdminRoleView() {
        home().validate().validateAllHomeTabsAreAvailable();
        home().validate().validateProjectsAreAvailable(ALL_PROJECTS);
        home().validate().validateProjectIsAvailable(PROJECT_NAME_DEFAULT);

        main().clickAdministrationTabButton();
        administration().validate().validateAllAdministrationTabsAreAvailable();
        projects().projectsPage().validate()
                .validateProjectsAreVisible(
                        PROJECT_NAME_ADMIRAL,
                        PROJECT_NAME_QE,
                        PROJECT_NAME_DEFAULT);
    }

    private void validateWithProjectAdminRole() {

        home().validate().validateAllHomeTabsAreAvailable();
        home().validate().validateProjectsAreAvailable(ALL_PROJECTS);
        home().validate().validateProjectIsNotAvailable(PROJECT_NAME_DEFAULT);
        String resourcePrefix = USER_SHAUNA.split("@")[0];
        createAndDeleteResourcesInAdmiralProject(resourcePrefix);

        main().clickAdministrationTabButton();
        projects().projectsPage().waitToLoad();
        administration().validate().validateProjectsAvailable();
        administration().validate().validateIdentityManagementNotAvailable();
        administration().validate().validateRegistriesNotAvailable();
        administration().validate().validateLogsNotAvailable();
        administration().validate().validateConfigurationNotAvailable();

        projects().projectsPage().validate().validateProjectsAreVisible(
                PROJECT_NAME_ADMIRAL,
                PROJECT_NAME_QE);
        projects().projectsPage().validate().validateProjectIsNotVisible(PROJECT_NAME_DEFAULT);
    }

    private void createAndDeleteResourcesInAdmiralProject(String resourcePrefix) {
        home().switchToProject(PROJECT_NAME_ADMIRAL);
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
        createNetwork.addHostByName(PROJECT_NAME_ADMIRAL + HOST_SUFFIX);
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
        createVolume.selectHostByName(PROJECT_NAME_ADMIRAL + HOST_SUFFIX);
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
        templates().editTemplatePage().navigateBack();
        templatesPage.refresh();
        templatesPage.validate().validateTemplateExistsWithName(resourcePrefix + TEMPLATE_SUFFIX);
        templatesPage.deleteTemplate(resourcePrefix + TEMPLATE_SUFFIX);
        templatesPage.refresh();
        templatesPage.validate()
                .validateTemplateDoesNotExistWithName(resourcePrefix + TEMPLATE_SUFFIX);
    }

    private void validateWithProjectMember() {
        home().validate().validateAllHomeTabsAreAvailable();
        home().validate().validateProjectsAreAvailable(ALL_PROJECTS);
        home().validate().validateProjectIsNotAvailable(PROJECT_NAME_DEFAULT);
        main().validate().validateAdministrationTabIsNotVisible();
        String resourcePrefix = USER_SCOTT.split("@")[0];
        createAndDeleteResourcesInAdmiralProject(resourcePrefix);
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
        home().validate().validateProjectIsAvailable(PROJECT_NAME_ADMIRAL);
        home().validate().validateProjectsAreNotAvailable(
                PROJECT_NAME_DEFAULT,
                PROJECT_NAME_QE);
    }

    private void configureCloudAdminRoles() {
        administration().clickIdentityManagementButton();
        identity().identityPage().clickUsersAndGroupsTab();
        identity().usersTab().assignCloudAdminRole(CLOUD_ADMIN_JASON);
    }

    private void unconfigureCloudAdminRoles() {
        administration().clickIdentityManagementButton();
        identity().identityPage().clickUsersAndGroupsTab();
        identity().usersTab().unassignCloudAdminRole(CLOUD_ADMIN_JASON);
    }

    private void addContentToProjects() {
        main().clickHomeTabButton();
        for (int i = 0; i < ALL_PROJECTS.size(); i++) {
            String projectName = ALL_PROJECTS.get(i);
            home().switchToProject(projectName);
            addVchHostToProject(projectName, getVCHUrl(vchIps.getHostsIps()[i]));
            provisionContainerInProject(projectName);
            addNetworkToProject(projectName);
            addVolumeToProject(projectName);
            addTemplateToProject(projectName);
        }
    }

    private void removeContentFromProjects() {
        for (String projectName : ALL_PROJECTS) {
            home().switchToProject(projectName);
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
            clusters().clustersPage().clickHostDeleteButton(projectName + HOST_SUFFIX);
            clusters().deleteHostDialog().waitToLoad();
            clusters().deleteHostDialog().submit();
        }
    }

    private void addVchHostToProject(String hostName, String hostUrl) {
        home().clickContainerHostsButton();
        clusters().clustersPage().clickAddClusterButton();
        AddClusterModalDialog addHostDialog = clusters().addHostDialog();
        addHostDialog.waitToLoad();
        addHostDialog.setName(hostName + HOST_SUFFIX);
        addHostDialog.setHostType(HostType.VCH);
        addHostDialog.setUrl(hostUrl);
        clusters().addHostDialog().submit();
        clusters().certificateModalDialog().waitToLoad();
        clusters().certificateModalDialog().submit();
        clusters().clustersPage().refresh();
        clusters().clustersPage().validate().validateHostExistsWithName(hostName + HOST_SUFFIX);
        clusters().clustersPage().validate().validateHostsCount(1);
    }

    private void provisionContainerInProject(String containerName) {
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
        containers().containersPage().validate().validateContainersCount(1);
    }

    private void addTemplateToProject(String namePrefix) {
        home().clickTemplatesButton();
        templates().templatesPage().waitToLoad();
        templates().templatesPage().clickCreateTemplateButton();
        templates().createTemplatePage().waitToLoad();
        templates().createTemplatePage().setName(namePrefix + TEMPLATE_SUFFIX);
        templates().createTemplatePage().clickProceedButton();
        templates().editTemplatePage().navigateBack();
        templates().templatesPage().waitToLoad();
        templates().templatesPage().refresh();
        templates().templatesPage().validate()
                .validateTemplateExistsWithName(namePrefix + TEMPLATE_SUFFIX);
        templates().templatesPage().validate().validateTemplatesCount(1);
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

        projects().projectsPage().clickProjectCard(PROJECT_NAME_ADMIRAL);
        projects().configureProjectPage().waitToLoad();
        projects().configureProjectPage().clickMembersTabButton();
        projects().membersTab().clickAddMemebersButton();
        AddMemberModalDialog addMemberDialogue = projects().addMemberDialog();
        addMemberDialogue.waitToLoad();
        addMemberDialogue.addMember(USER_SHAUNA);
        addMemberDialogue.addMember(USER_ISTOYANOV);
        addMemberDialogue.setRole(ProjectMemberRole.ADMIN);
        addMemberDialogue.submit();
        projects().membersTab().clickAddMemebersButton();
        addMemberDialogue.waitToLoad();
        addMemberDialogue.addMember(USER_SCOTT);
        addMemberDialogue.addMember(USER_SERGEY);
        addMemberDialogue.setRole(ProjectMemberRole.MEMBER);
        addMemberDialogue.submit();
        projects().membersTab().clickAddMemebersButton();
        addMemberDialogue.waitToLoad();
        addMemberDialogue.addMember(USER_GROUP_COKE);
        addMemberDialogue.setRole(ProjectMemberRole.VIEWER);
        addMemberDialogue.submit();
        projects().configureProjectPage().navigateBack();
        projects().projectsPage().waitToLoad();

        projects().projectsPage().clickProjectCard(PROJECT_NAME_QE);
        projects().configureProjectPage().waitToLoad();
        projects().configureProjectPage().clickMembersTabButton();
        projects().membersTab().clickAddMemebersButton();
        addMemberDialogue.waitToLoad();
        addMemberDialogue.addMember(USER_SHAUNA);
        addMemberDialogue.addMember(USER_AKRACHEVA);
        addMemberDialogue.setRole(ProjectMemberRole.ADMIN);
        addMemberDialogue.submit();
        projects().membersTab().clickAddMemebersButton();
        addMemberDialogue.waitToLoad();
        addMemberDialogue.addMember(USER_SCOTT);
        addMemberDialogue.addMember(USER_GROUP_SOFIA_QE_TEAM);
        addMemberDialogue.setRole(ProjectMemberRole.MEMBER);
        addMemberDialogue.submit();
        projects().membersTab().clickAddMemebersButton();
        addMemberDialogue.waitToLoad();
        addMemberDialogue.addMember(USER_GROUP_SOFIA_DEV_TEAM);
        addMemberDialogue.setRole(ProjectMemberRole.VIEWER);
        addMemberDialogue.submit();
        projects().configureProjectPage().navigateBack();
        projects().projectsPage().waitToLoad();
    }

    private void createProjects() {
        main().clickAdministrationTabButton();
        projects().projectsPage().clickAddProjectButton();
        AddProjectModalDialog addProjectDialog = projects().addProjectDialog();
        addProjectDialog.waitToLoad();
        addProjectDialog.setName(PROJECT_NAME_ADMIRAL);
        addProjectDialog.setDescription("This is the Admiral project.");
        addProjectDialog.setIsPublic(true);
        addProjectDialog.submit();
        projects().projectsPage().clickAddProjectButton();
        addProjectDialog.waitToLoad();
        addProjectDialog.setName(PROJECT_NAME_QE);
        addProjectDialog.setDescription("This is the Quality Engineering project.");
        addProjectDialog.submit();
    }

    private void loginAs(String username) {
        loginAs(username, PASSWORD);
    }

    private void deleteProjects() {
        main().clickAdministrationTabButton();
        projects().projectsPage().waitToLoad();
        for (String project : ALL_PROJECTS) {
            projects().projectsPage().clickProjectDeleteButton(project);
            projects().deleteProjectDialog().waitToLoad();
            projects().deleteProjectDialog().submit();
            projects().projectsPage().waitToLoad();
        }
    }

    @Override
    protected List<String> getCloudAdminsPrincipalIds() {
        return Arrays.asList(new String[] { CLOUD_ADMIN_JASON });
    }

}
