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

package com.vmware.admiral.vic.test.ui.scenarios;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.vmware.admiral.test.ui.SelenideClassRunner.Browser;
import com.vmware.admiral.test.ui.SelenideClassRunner.SupportedBrowsers;
import com.vmware.admiral.test.ui.pages.containers.ContainersPage;
import com.vmware.admiral.test.ui.pages.hosts.AddHostModalDialogue.HostType;
import com.vmware.admiral.test.ui.pages.hosts.ContainerHostsPage;
import com.vmware.admiral.test.ui.pages.networks.NetworksPage;
import com.vmware.admiral.test.ui.pages.projects.ProjectsPage;
import com.vmware.admiral.test.ui.pages.projects.configure.AddMemberModalDialogue.ProjectMemberRole;
import com.vmware.admiral.test.ui.pages.projects.configure.ConfigureProjectPage;
import com.vmware.admiral.test.ui.pages.projects.configure.MembersTab;
import com.vmware.admiral.test.ui.pages.volumes.VolumesPage;
import com.vmware.admiral.vic.test.ui.BaseTest;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTab;

/**
 * This test creates six projects, adds users with different roles to the projects, adds non-default
 * cloud admin roles, adds content to each project and validates the displayed content is project
 * aware, validates users with different roles see the corresponding views, and are able to perform
 * the corresponding actions.
 *
 * Test steps overview:
 *
 * 1. Login with default system administrator, create projects, add users and groups with different
 * roles to the projects, assign cloud administrator role to users
 *
 * 2. Login with configured cloud administrator, validate user sees all the tabs, and create content
 * in each project(add host, provision a container, create a nertwork and a volume). Validate that
 * the created content is project-aware and is visible only in the project it belongs to
 *
 * 3. Login with project administrator and validate user sees all Home tabs and only the Projects
 * tab under Administration. Validate user can create and remove resources
 *
 * 4. Login with project member, validate Administration tab is not accessible, validate user can
 * create and remove resources
 *
 * 5. Login with project viewer and validate role restrictions
 *
 * 6. Login with cloud administrator again and delete all the content from all of the projects
 *
 * 7. Login with default system administrator, remove all the projects and unassign the cloud admin
 * roles
 */
public class RBACAndItemsProjectAwareness extends BaseTest {

    private final String PROJECT_NAME_ADMIRAL = "admiral";
    private final String PROJECT_NAME_HARBOR = "harbor";
    private final String PROJECT_NAME_VIC = "vic";
    private final String PROJECT_NAME_DEVELOPMENT = "development";
    private final String PROJECT_NAME_FINANCE = "finance";
    private final String PROJECT_NAME_QE = "quality-engineering";
    private final String PROJECT_NAME_DEFAULT = "default-project";

    private final String HOST_SUFFIX = "_host";
    private final String NETWORK_SUFFIX = "_network";
    private final String VOLUME_SUFFIX = "_volume";
    private final String CONTAINER_SUFFIX = "_container";

    private final String IMAGE_NAME = "alpine";

    private final String USER_SHAUNA = "shauna@coke.sqa-horizon.local";
    private final String USER_ISTOYANOV = "istoyanov@vcac.sqa-horizon.local";
    private final String USER_SCOTT = "scott@coke.sqa-horizon.local";
    private final String USER_SERGEY = "sergey@coke.sqa-horizon.local";
    private final String USER_TGEORGIEV = "tgeorgiev@vcac.sqa-horizon.local";
    private final String USER_SYLVIA = "sylvia@coke.sqa-horizon.local";
    private final String USER_SANCHEZS = "sanchezs@vcac.sqa-horizon.local";
    private final String USER_EIVANOV = "eivanova@vcac.sqa-horizon.local";
    private final String USER_TAYLOR = "taylorc@vcac.sqa-horizon.local";
    private final String USER_AKRACHEVA = "akracheva@vcac.sqa-horizon.local";
    private final String USER_CONNIE = "connie@coke.sqa-horizon.local";

    private final String USER_GROUP_COKE = "coke@coke.sqa-horizon.local";
    private final String USER_GROUP_SOFIA_DEV_TEAM = "sofiadevteam@vcac.sqa-horizon.local";
    private final String USER_GROUP_SOFIA_QE_TEAM = "sofiaqeteam@vcac.sqa-horizon.local";
    private final String USER_GROUP_SOFIA_TEAMS = "sofiateams@vcac.sqa-horizon.local";
    private final String USER_GROUP_PALOALTO = "paloalto@vcac.sqa-horizon.local";

    private final String CLOUD_ADMIN_JASON = "jason@coke.sqa-horizon.local";
    private final String CLOUD_ADMIN_FRITZ = "fritz@coke.sqa-horizon.local";
    private final String CLOUD_ADMIN_KSTEFANOV = "kstefanov@vcac.sqa-horizon.local";

    private final String PASSWORD = "VMware1!";

    private final List<String> ALL_PROJECTS = Arrays.asList(new String[] {
            PROJECT_NAME_ADMIRAL,
            PROJECT_NAME_HARBOR,
            PROJECT_NAME_VIC,
            PROJECT_NAME_DEVELOPMENT,
            PROJECT_NAME_FINANCE,
            PROJECT_NAME_QE,
    });

    @Test
    @SupportedBrowsers({ Browser.CHROME })
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
        navigateToHomeTab().validate()
                .validateAllHomeTabsAreAvailable()
                .validateProjectsAreAvailable(ALL_PROJECTS)
                .validateProjectIsAvailable(PROJECT_NAME_DEFAULT);
        navigateToAdministrationTab()
                .validate(v -> v.validateAllAdministrationTabsAreAvailable())
                .navigateToProjectsPage()
                .validate()
                .validateProjectsAreVisible(
                        PROJECT_NAME_ADMIRAL,
                        PROJECT_NAME_HARBOR,
                        PROJECT_NAME_VIC,
                        PROJECT_NAME_DEVELOPMENT,
                        PROJECT_NAME_FINANCE,
                        PROJECT_NAME_QE,
                        PROJECT_NAME_DEFAULT);
    }

    private void validateWithProjectAdminRole() {
        navigateToHomeTab()
                .validate(v -> v
                        .validateAllHomeTabsAreAvailable()
                        .validateProjectsAreAvailable(ALL_PROJECTS)
                        .validateProjectIsNotAvailable(PROJECT_NAME_DEFAULT))
                .switchToProject(PROJECT_NAME_ADMIRAL);
        String resourcePrefix = USER_SHAUNA.split("@")[0];
        createAndDeleteResourcesInAdmiralProject(resourcePrefix);
        navigateToAdministrationTab()
                .validate(v -> v
                        .validateProjectsAvailable()
                        .validateIdentityManagementNotAvailable()
                        .validateRegistriesNotAvailable()
                        .validateLogsNotAvailable()
                        .validateConfigurationNotAvailable())
                .navigateToProjectsPage()
                .validate(v -> v
                        .validateProjectsAreVisible(
                                PROJECT_NAME_ADMIRAL,
                                PROJECT_NAME_HARBOR,
                                PROJECT_NAME_VIC,
                                PROJECT_NAME_DEVELOPMENT,
                                PROJECT_NAME_FINANCE,
                                PROJECT_NAME_QE)
                        .validateProjectIsNotVisible(PROJECT_NAME_DEFAULT));
    }

    private void createAndDeleteResourcesInAdmiralProject(String resourcePrefix) {
        ContainersPage containers = navigateToHomeTab().navigateToContainersPage();
        containers.provisionAContainer()
                .navigateToBasicTab()
                .setName(resourcePrefix + CONTAINER_SUFFIX)
                .setImage(IMAGE_NAME)
                .submit()
                .expectSuccess();
        containers.requests().waitForLastRequestToSucceed(60);
        containers.refresh()
                .validate(v -> v.validateContainerExistsWithName(resourcePrefix + CONTAINER_SUFFIX))
                .deleteContainer(resourcePrefix + CONTAINER_SUFFIX)
                .requests()
                .waitForLastRequestToSucceed(60);
        containers.refresh().validate()
                .validateContainerDoesNotExistWithName(resourcePrefix + CONTAINER_SUFFIX);
        NetworksPage networks = navigateToHomeTab().navigateToNetworksPage();
        networks.createNetwork()
                .setName(resourcePrefix + NETWORK_SUFFIX)
                .addHostByName(PROJECT_NAME_ADMIRAL + HOST_SUFFIX)
                .submit()
                .expectSuccess();
        networks.requests().waitForLastRequestToSucceed(20);
        networks.refresh()
                .validate(v -> v.validateNetworkExistsWithName(resourcePrefix + NETWORK_SUFFIX))
                .deleteNetwork(resourcePrefix + NETWORK_SUFFIX)
                .requests()
                .waitForLastRequestToSucceed(20);
        networks.refresh()
                .validate()
                .validateNetworkDoesNotExist(resourcePrefix + NETWORK_SUFFIX);
        // Volumes cannot be created on current VCH deployment
        /*
         * VolumesPage volumes = navigateToHomeTab().navigateToVolumesPage(); volumes.createVolume()
         * .setName(resourcePrefix + VOLUME_SUFFIX) .selectHostByName(PROJECT_NAME_ADMIRAL +
         * HOST_SUFFIX) .submit() .expectSuccess();
         * volumes.requests().waitForLastRequestToSucceed(20); volumes.refresh() .validate(v ->
         * v.validateVolumeExistsWithName(resourcePrefix + VOLUME_SUFFIX))
         * .deleteVolume(resourcePrefix + VOLUME_SUFFIX)
         * .requests().waitForLastRequestToSucceed(20);
         */
    }

    private void validateWithProjectMember() {
        navigateToHomeTab()
                .validate()
                .validateAllHomeTabsAreAvailable()
                .validateProjectsAreAvailable(ALL_PROJECTS)
                .validateProjectIsNotAvailable(PROJECT_NAME_DEFAULT);
        getClient().validate().validateAdministrationTabIsNotVisible();
        String resourcePrefix = USER_SCOTT.split("@")[0];
        createAndDeleteResourcesInAdmiralProject(resourcePrefix);
    }

    private void validateWithProjectViewer() {
        getClient().validate(v -> v.validateAdministrationTabIsNotVisible())
                .navigateToHomeTab()
                .validate()
                .validateApplicationsNotAvailable()
                .validateContainersNotAvailable()
                .validateNetworksNotAvailable()
                .validateVolumesNotAvailable()
                .validateTemplatesNotAvailable()
                .validateProjectRepositoriesAvailable()
                .validatePublicRepositoriesNotAvailable()
                .validateContainerHostsNotAvailable()
                .validateProjectsAreAvailable(
                        PROJECT_NAME_ADMIRAL,
                        PROJECT_NAME_HARBOR,
                        PROJECT_NAME_VIC)
                .validateProjectsAreNotAvailable(
                        PROJECT_NAME_DEFAULT,
                        PROJECT_NAME_DEVELOPMENT,
                        PROJECT_NAME_FINANCE,
                        PROJECT_NAME_QE);
    }

    private void configureCloudAdminRoles() {
        navigateToAdministrationTab()
                .navigateToIdentityManagementPage()
                .navigateToUsersAndGroupsTab()
                .assignCloudAdminRole(CLOUD_ADMIN_JASON)
                .assignCloudAdminRole(CLOUD_ADMIN_FRITZ)
                .assignCloudAdminRole(CLOUD_ADMIN_KSTEFANOV);
    }

    private void unconfigureCloudAdminRoles() {
        navigateToAdministrationTab()
                .navigateToIdentityManagementPage()
                .navigateToUsersAndGroupsTab()
                .unassignCloudAdminRole(CLOUD_ADMIN_JASON)
                .unassignCloudAdminRole(CLOUD_ADMIN_FRITZ)
                .unassignCloudAdminRole(CLOUD_ADMIN_KSTEFANOV);
    }

    private void addContentToProjects() {
        VICHomeTab homeTab = navigateToHomeTab();
        for (String projectName : ALL_PROJECTS) {
            homeTab.switchToProject(projectName);
            addVchHostToProject(projectName);
            provisionContainerInProject(projectName);
            addNetworkToProject(projectName);
            // addVolumeToProject(projectName);
        }
    }

    private void removeContentFromProjects() {
        VICHomeTab homeTab = navigateToHomeTab();
        for (String projectName : ALL_PROJECTS) {
            homeTab.switchToProject(projectName);
            homeTab.navigateToContainersPage()
                    .deleteContainer(projectName + CONTAINER_SUFFIX)
                    .requests()
                    .waitForLastRequestToSucceed(60);
            homeTab.navigateToNetworksPage()
                    .deleteNetwork(projectName + NETWORK_SUFFIX)
                    .requests()
                    .waitForLastRequestToSucceed(20);
            // Volumes cannot be created on current VCH deployment
            /*
             * homeTab.navigateToVolumesPage() .deleteVolume(projectName + VOLUME_SUFFIX)
             * .requests() .waitForLastRequestToSucceed(20);
             */
            homeTab.navigateToContainerHostsPage()
                    .deleteContainerHost(projectName + HOST_SUFFIX);
        }
    }

    private void addVchHostToProject(String hostName) {
        ContainerHostsPage hostsPage = navigateToHomeTab()
                .navigateToContainerHostsPage();
        hostsPage.addContainerHost()
                .setName(hostName + HOST_SUFFIX)
                .setHostType(HostType.VCH)
                .setUrl(getVchUrl())
                .submit()
                .acceptCertificateIfShownAndExpectSuccess();
        hostsPage.refresh()
                .validate()
                .validateHostExistsWithName(hostName + HOST_SUFFIX)
                .validateHostsCount(1);
    }

    private void provisionContainerInProject(String containerName) {
        ContainersPage containersPage = navigateToHomeTab()
                .navigateToContainersPage();
        containersPage.provisionAContainer()
                .navigateToBasicTab()
                .setName(containerName + CONTAINER_SUFFIX)
                .setImage(IMAGE_NAME)
                .submit()
                .expectSuccess();
        containersPage.requests().waitForLastRequestToSucceed(60);
        containersPage.refresh()
                .validate()
                .validateContainerExistsWithName(containerName + CONTAINER_SUFFIX)
                .validateContainersCount(1);
    }

    private void addNetworkToProject(String networkName) {
        NetworksPage networksPage = navigateToHomeTab().navigateToNetworksPage();
        networksPage.createNetwork()
                .setName(networkName + NETWORK_SUFFIX)
                .addHostByName(networkName + HOST_SUFFIX)
                .submit()
                .expectSuccess();
        networksPage.requests().waitForLastRequestToSucceed(10);
        networksPage.refresh()
                .validate()
                .validateNetworkExistsWithName(networkName + NETWORK_SUFFIX)
                .validateNetworksCount(1);
    }

    // Volumes cannot be created on current VCH deployment
    @SuppressWarnings("unused")
    private void addVolumeToProject(String volumeName) {
        VolumesPage volumesPage = navigateToHomeTab().navigateToVolumesPage();
        volumesPage.createVolume()
                .setName(volumeName + VOLUME_SUFFIX)
                .selectHostByName(volumeName + HOST_SUFFIX)
                .submit()
                .expectSuccess();
        volumesPage.requests().waitForLastRequestToSucceed(20);
        volumesPage.refresh()
                .validate()
                .validateVolumeExistsWithName(volumeName + VOLUME_SUFFIX);
        // VBV-1735 If there is a host with volumes added in a project and if thaat host is added to
        // another project, the volumes are visible in the second project.
        // .validateVolumesCount(1);
    }

    private void configureProjects() {
        ProjectsPage projectsPage = navigateToAdministrationTab()
                .navigateToProjectsPage();
        ConfigureProjectPage configureProjectPage = projectsPage
                .configureProject(PROJECT_NAME_ADMIRAL);
        MembersTab membersTab = configureProjectPage.navigateToMembersTab();
        membersTab.addMemebers()
                .addMember(USER_SHAUNA)
                .addMember(USER_ISTOYANOV)
                .setRole(ProjectMemberRole.ADMIN)
                .submit()
                .expectSuccess();
        membersTab.addMemebers()
                .addMember(USER_SCOTT)
                .addMember(USER_SERGEY)
                .setRole(ProjectMemberRole.MEMBER)
                .submit()
                .expectSuccess();
        membersTab.addMemebers()
                .addMember(USER_GROUP_COKE)
                .setRole(ProjectMemberRole.VIEWER)
                .submit()
                .expectSuccess();
        configureProjectPage.navigateBack();
        projectsPage.configureProject(PROJECT_NAME_HARBOR).navigateToMembersTab();
        membersTab.addMemebers()
                .addMember(USER_SHAUNA)
                .addMember(USER_TGEORGIEV)
                .setRole(ProjectMemberRole.ADMIN)
                .submit()
                .expectSuccess();
        membersTab.addMemebers()
                .addMember(USER_SCOTT)
                .addMember(USER_SYLVIA)
                .setRole(ProjectMemberRole.MEMBER)
                .submit()
                .expectSuccess();
        membersTab.addMemebers()
                .addMember(USER_GROUP_COKE)
                .setRole(ProjectMemberRole.VIEWER)
                .submit();
        configureProjectPage.navigateBack();
        projectsPage.configureProject(PROJECT_NAME_VIC).navigateToMembersTab();
        membersTab.addMemebers()
                .addMember(USER_SHAUNA)
                .addMember(USER_SANCHEZS)
                .setRole(ProjectMemberRole.ADMIN)
                .submit()
                .expectSuccess();
        membersTab.addMemebers()
                .addMember(USER_SCOTT)
                .setRole(ProjectMemberRole.MEMBER)
                .submit()
                .expectSuccess();
        membersTab.addMemebers()
                .addMember(USER_GROUP_COKE)
                .setRole(ProjectMemberRole.VIEWER)
                .submit()
                .expectSuccess();
        configureProjectPage.navigateBack();
        projectsPage.configureProject(PROJECT_NAME_DEVELOPMENT).navigateToMembersTab();
        membersTab.addMemebers()
                .addMember(USER_SHAUNA)
                .addMember(USER_EIVANOV)
                .setRole(ProjectMemberRole.ADMIN)
                .submit()
                .expectSuccess();
        membersTab.addMemebers()
                .addMember(USER_SCOTT)
                .addMember(USER_GROUP_SOFIA_DEV_TEAM)
                .setRole(ProjectMemberRole.MEMBER)
                .submit()
                .expectSuccess();
        membersTab.addMemebers()
                .addMember(USER_GROUP_SOFIA_QE_TEAM)
                .setRole(ProjectMemberRole.VIEWER)
                .submit()
                .expectSuccess();
        configureProjectPage.navigateBack();
        projectsPage.configureProject(PROJECT_NAME_FINANCE).navigateToMembersTab();
        membersTab.addMemebers()
                .addMember(USER_SHAUNA)
                .addMember(USER_TAYLOR)
                .setRole(ProjectMemberRole.ADMIN)
                .submit()
                .expectSuccess();
        membersTab.addMemebers()
                .addMember(USER_SCOTT)
                .addMember(USER_GROUP_PALOALTO)
                .setRole(ProjectMemberRole.MEMBER)
                .submit()
                .expectSuccess();
        membersTab.addMemebers()
                .addMember(USER_GROUP_SOFIA_TEAMS)
                .setRole(ProjectMemberRole.VIEWER)
                .submit()
                .expectSuccess();
        configureProjectPage.navigateBack();
        projectsPage.configureProject(PROJECT_NAME_QE).navigateToMembersTab();
        membersTab.addMemebers()
                .addMember(USER_SHAUNA)
                .addMember(USER_AKRACHEVA)
                .setRole(ProjectMemberRole.ADMIN)
                .submit()
                .expectSuccess();
        membersTab.addMemebers()
                .addMember(USER_SCOTT)
                .addMember(USER_GROUP_SOFIA_QE_TEAM)
                .setRole(ProjectMemberRole.MEMBER)
                .submit()
                .expectSuccess();
        membersTab.addMemebers()
                .addMember(USER_GROUP_SOFIA_DEV_TEAM)
                .setRole(ProjectMemberRole.VIEWER)
                .submit()
                .expectSuccess();
        configureProjectPage.navigateBack();
    }

    private void createProjects() {
        ProjectsPage projectsPage = navigateToAdministrationTab().navigateToProjectsPage();
        projectsPage.addProject()
                .setName(PROJECT_NAME_ADMIRAL)
                .setDescription("This is the Admiral project.")
                .setIsPublic(true)
                .submit()
                .expectSuccess();
        projectsPage.addProject()
                .setName(PROJECT_NAME_HARBOR)
                .setDescription("This is the Harbor project.")
                .setIsPublic(true)
                .submit()
                .expectSuccess();
        projectsPage.addProject()
                .setName(PROJECT_NAME_VIC)
                .setDescription("This is the VIC project.")
                .setIsPublic(true)
                .submit()
                .expectSuccess();
        projectsPage.addProject()
                .setName(PROJECT_NAME_DEVELOPMENT)
                .setDescription("This is the Development project.")
                .submit()
                .expectSuccess();
        projectsPage.addProject()
                .setName(PROJECT_NAME_FINANCE)
                .setDescription("This is the Finance project.")
                .submit()
                .expectSuccess();
        projectsPage.addProject()
                .setName(PROJECT_NAME_QE)
                .setDescription("This is the Quality Engineering project.")
                .submit()
                .expectSuccess();
    }

    private void loginAs(String username) {
        getClient().logIn(getVicUrl(), username, PASSWORD);
    }

    private void deleteProjects() {
        ProjectsPage projectsPage = navigateToAdministrationTab().navigateToProjectsPage();
        for (String project : ALL_PROJECTS) {
            projectsPage.deleteProject(project)
                    .expectSuccess();
        }
    }

}
