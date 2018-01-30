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

import com.codeborne.selenide.Condition;

import org.junit.Test;
import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.clusters.AddClusterModalDialog.HostType;
import com.vmware.admiral.test.util.SSHCommandExecutor;
import com.vmware.admiral.test.util.SSHCommandExecutor.CommandResult;
import com.vmware.admiral.vic.test.ui.BaseTest;

public class PushImageToHarborAndProvision extends BaseTest {

    private final String HARBOR_CERTIFICATE_PATH = "/storage/data/harbor/ca_download/ca.crt";
    private final String NGINX_IMAGE_NAME = "nginx";
    private final String PROJECT_NAME = "image-path-test";
    private final String TAGGED_IMAGE_PATH = "/wmware/vic/harbor/test/nginx";
    private final String HOST_NAME = PROJECT_NAME + "_host";

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
        clusters().addHostDialog().setUrl(getVchUrl());
        clusters().addHostDialog().submit();
        clusters().certificateModalDialog().waitToLoad();
        clusters().certificateModalDialog().submit();

        pushImageToProject();

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
        projectRepositories().projectRepositoriesPage().deleteRepository(PROJECT_NAME
                + "/wmware/vic/harbor/test/nginx");
        projectRepositories().deleteRepositoryModalDialog().waitToLoad();
        projectRepositories().deleteRepositoryModalDialog().submit();
        home().clickContainerHostsButton();
        clusters().clustersPage().clickHostDeleteButton(HOST_NAME);
        clusters().deleteHostDialog().waitToLoad();
        clusters().deleteHostDialog().submit();

        main().clickAdministrationTabButton();
        projects().projectsPage().waitToLoad();
        projects().projectsPage().clickProjectDeleteButton(PROJECT_NAME);
        projects().deleteProjectDialog().submit();
        logOut();
    }

    private void pushImageToProject() {
        SSHCommandExecutor executor = new SSHCommandExecutor(getVicIp(), 22, getVicVmUsername(),
                getVicVmPassword());
        String createDirCommand = String.format("mkdir -p /etc/docker/certs.d/%s", getVicIp());
        CommandResult result = executor.execute(createDirCommand, 10);
        logOutputOrThrow(createDirCommand, result);

        String copyCertificateCommand = String.format("cp %s /etc/docker/certs.d/%s",
                HARBOR_CERTIFICATE_PATH, getVicIp());
        result = executor.execute(copyCertificateCommand, 10);
        logOutputOrThrow(copyCertificateCommand, result);

        String pullNginxCommand = "docker pull nginx";
        result = executor.execute(pullNginxCommand, 120);
        logOutputOrThrow(pullNginxCommand, result);

        String taggedImage = getVicIp() + "/" + PROJECT_NAME + TAGGED_IMAGE_PATH;
        String tagNginxCommand = "docker tag nginx " + taggedImage;
        result = executor.execute(tagNginxCommand, 10);
        logOutputOrThrow(tagNginxCommand, result);

        String loginCommand = String.format("docker login %s --username %s --password %s",
                getVicIp(), getDefaultAdminUsername(),
                getDefaultAdminPassword());
        result = executor.execute(loginCommand, 30);
        logOutputOrThrow(loginCommand, result);

        String pushCommnad = "docker push " + taggedImage;
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
