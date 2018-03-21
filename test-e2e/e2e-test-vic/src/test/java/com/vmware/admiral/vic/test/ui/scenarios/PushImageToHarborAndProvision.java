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

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;

import java.io.File;
import java.io.IOException;

import com.codeborne.selenide.Condition;

import org.junit.Rule;
import org.junit.Test;
import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.clusters.AddClusterModalDialog.HostType;
import com.vmware.admiral.test.util.AuthContext;
import com.vmware.admiral.test.util.SSHCommandExecutor;
import com.vmware.admiral.test.util.SSHCommandExecutor.CommandResult;
import com.vmware.admiral.vic.test.ui.BaseTest;
import com.vmware.admiral.vic.test.ui.util.CreateVchRule;

public class PushImageToHarborAndProvision extends BaseTest {

    private final String LOCAL_CERTIFICATE_PATH = "target" + File.separator + "ca.crt";
    private final String REMOTE_CERTIFICATE_FOLDER_PATH = String.format("/etc/docker/certs.d/%s",
            getVicIp());
    private final String NGINX_IMAGE_NAME = "nginx";
    private final String NGINX_IMAGE_TAG = "alpine";
    private final String NGINX_IMAGE_AND_TAG = NGINX_IMAGE_NAME + ":" + NGINX_IMAGE_TAG;
    private final String PROJECT_NAME = "hbr-provision";
    private final String TAGGED_IMAGE_PATH = "/wmware/vic/harbor/test/nginx";
    private final String TAGGED_IMAGE = getVicIp() + "/" + PROJECT_NAME + TAGGED_IMAGE_PATH;
    private final String HOST_NAME = PROJECT_NAME + "_host";

    private final AuthContext vicOvaAuthContext = new AuthContext(getVicIp(), getVicVmUsername(),
            getVicVmPassword());
    private final AuthContext vcenterAuthContext = new AuthContext(getVcenterIp(),
            getDefaultAdminUsername(), getDefaultAdminPassword());

    @Rule
    public CreateVchRule vchIps = new CreateVchRule(vicOvaAuthContext, vcenterAuthContext,
            "harbor-provisioning-test", 1);

    @Test
    public void pushImageToHarborAndProvision() {
        loginAsAdmin();
        main().clickAdministrationTabButton();
        projects().projectsPage().clickAddProjectButton();
        projects().addProjectDialog().setName(PROJECT_NAME);
        projects().addMemberDialog().submit();

        main().clickHomeTabButton();
        home().switchToProject(PROJECT_NAME);
        home().clickContainerHostsButton();
        clusters().clustersPage().clickAddClusterButton();
        clusters().addHostDialog().setName(HOST_NAME);
        clusters().addHostDialog().setHostType(HostType.VCH);
        String vchUrl = getVchUrl(vchIps.getHostsIps()[0]);
        clusters().addHostDialog().setUrl(vchUrl);
        clusters().addHostDialog().submit();
        clusters().certificateModalDialog().waitToLoad();
        clusters().certificateModalDialog().submit();

        main().clickAdministrationTabButton();
        administration().clickConfigurationButton();
        configuration().configurationPage()
                .downloadCertificate(LOCAL_CERTIFICATE_PATH);

        pushImageToProject();

        main().clickHomeTabButton();
        home().clickContainersButton();
        containers().containersPage().clickCreateContainer();
        containers().basicTab()
                .setImage(getVicIp() + ":443/" + PROJECT_NAME + TAGGED_IMAGE_PATH);
        containers().basicTab().setName(NGINX_IMAGE_NAME);
        containers().createContainerPage().clickNetworkTab();
        containers().networkTab().addPortBinding(null, "80");
        containers().createContainerPage().submit();
        containers().requests().waitForLastRequestToSucceed(720);
        containers().containersPage().refresh();
        containers().containersPage().inspectContainer(NGINX_IMAGE_NAME);
        containers().containerStatsPage().waitToLoad();
        String settings = containers().containerStatsPage().getPortsSettings().get(0);
        String nginxAddress = settings.substring(0, settings.lastIndexOf(":"));
        LOG.info("NGINX landing page resolved at: " + nginxAddress);
        logOut();

        LOG.info("Validating the NGINX landing page");
        open(nginxAddress);
        $(By.cssSelector("h1")).shouldHave(Condition.exactText("Welcome to nginx!"));

        loginAsAdmin();

        home().clickContainersButton();
        containers().containersPage().waitToLoad();
        containers().containersPage().deleteContainer(NGINX_IMAGE_NAME);
        containers().requests().waitForLastRequestToSucceed(360);
        home().clickProjectRepositoriesButton();
        projectRepositories().projectRepositoriesPage()
                .selectRepositoryByName(PROJECT_NAME + TAGGED_IMAGE_PATH);
        projectRepositories().projectRepositoriesPage().clickDeleteButton();
        projectRepositories().deleteRepositoryModalDialog().waitToLoad();
        projectRepositories().deleteRepositoryModalDialog().submit();
        projectRepositories().deleteRepositoryModalDialog().waitForDeleteToComplete();
        projectRepositories().deleteRepositoryModalDialog().close();
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

    private void pushImageToProject() {
        SSHCommandExecutor executor = new SSHCommandExecutor(vicOvaAuthContext, 22);

        LOG.info(String.format("Creating remote folder [%s]", REMOTE_CERTIFICATE_FOLDER_PATH));
        String createDirCommand = String.format("mkdir -p " + REMOTE_CERTIFICATE_FOLDER_PATH);
        CommandResult result = executor.execute(createDirCommand, 10);
        logOutputOrThrow(createDirCommand, result);

        LOG.info(String.format("Sending certificate to remote path [%s]",
                REMOTE_CERTIFICATE_FOLDER_PATH));
        try {
            executor.sendFile(new File(LOCAL_CERTIFICATE_PATH), REMOTE_CERTIFICATE_FOLDER_PATH);
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format("Could not send the certificate file to remote path [%s]",
                            REMOTE_CERTIFICATE_FOLDER_PATH),
                    e);
        }

        LOG.info(String.format("Pulling image [%s]", NGINX_IMAGE_AND_TAG));
        String pullNginxCommand = "docker pull " + NGINX_IMAGE_AND_TAG;
        result = executor.execute(pullNginxCommand, 120);
        logOutputOrThrow(pullNginxCommand, result);

        LOG.info(
                String.format("Tagging image [%s] with [%s]", NGINX_IMAGE_AND_TAG, TAGGED_IMAGE));
        String tagNginxCommand = "docker tag " + NGINX_IMAGE_AND_TAG + " " + TAGGED_IMAGE;
        result = executor.execute(tagNginxCommand, 10);
        logOutputOrThrow(tagNginxCommand, result);

        LOG.info("Logging in to the registry");
        String loginCommand = String.format("docker login %s --username %s --password %s",
                getVicIp(), getDefaultAdminUsername(), getDefaultAdminPassword());
        result = executor.execute(loginCommand, 30);
        logOutputOrThrow(loginCommand, result);

        LOG.info(String.format("Pushing image [%s] to the registry", TAGGED_IMAGE));
        String pushCommnad = "docker push " + TAGGED_IMAGE;
        result = executor.execute(pushCommnad, 120);
        logOutputOrThrow(pushCommnad, result);
    }

    private void logOutputOrThrow(String command, CommandResult result) {
        if (result.getExitStatus() != 0) {
            String error = String.format(
                    "Command [%s] failed with exit status [%d], error output:\n%s",
                    command, result.getExitStatus(), result.getErrorOutput());
            LOG.warning(error);
            throw new RuntimeException(error);
        } else {
            LOG.info(String.format("Command [%s] succeeded, output:\n%s", command,
                    result.getOutput()));
        }
    }

}
