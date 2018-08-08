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
import java.util.Arrays;
import java.util.List;

import com.codeborne.selenide.Condition;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.commons.HostCommons;
import com.vmware.admiral.test.ui.commons.ProjectCommons;
import com.vmware.admiral.test.util.AdmiralEventLogRule;
import com.vmware.admiral.test.util.ScreenshotRule;
import com.vmware.admiral.test.util.SshCommandExecutor;
import com.vmware.admiral.test.util.SshCommandExecutor.CommandResult;
import com.vmware.admiral.test.util.TestStatusLoggerRule;
import com.vmware.admiral.test.util.host.ContainerHostProviderRule;
import com.vmware.admiral.vic.test.VicTestProperties;
import com.vmware.admiral.vic.test.ui.BaseTestVic;
import com.vmware.admiral.vic.test.util.VICAuthTokenGetter;

public class PushImageToHarborAndProvision extends BaseTestVic {

    private static final String PROJECT_NAME = "hbr-provision";

    private final String LOCAL_CERTIFICATE_PATH = "target" + File.separator + "ca.crt";
    private final String REMOTE_CERTIFICATE_FOLDER_PATH = String.format("/etc/docker/certs.d/%s",
            VicTestProperties.vicIp());
    private final String NGINX_IMAGE_NAME = "nginx";
    private final String NGINX_IMAGE_TAG = "alpine";
    private final String NGINX_IMAGE_AND_TAG = NGINX_IMAGE_NAME + ":" + NGINX_IMAGE_TAG;
    private final String TAGGED_IMAGE_PATH = "/wmware/vic/harbor/test/nginx";
    private final String TAGGED_IMAGE = VicTestProperties.vicIp() + "/" + PROJECT_NAME
            + TAGGED_IMAGE_PATH;
    private final String HOST_NAME = PROJECT_NAME + "_host";

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

    @Test
    public void pushImageToHarborAndProvision() {
        loginAsAdmin();
        main().clickAdministrationTabButton();
        ProjectCommons.addProject(getClient(), PROJECT_NAME, "Harbor provisioning project", false);
        main().clickHomeTabButton();
        home().switchToProject(PROJECT_NAME);

        home().clickContainerHostsButton();
        HostCommons.addHost(getClient(), HOST_NAME, null, provider.getHost().getHostType(),
                getHostAddress(provider.getHost()), true);

        main().clickAdministrationTabButton();
        administration().clickConfigurationButton();
        configuration().configurationPage()
                .downloadCertificate(LOCAL_CERTIFICATE_PATH);

        pushImageToProject();

        main().clickHomeTabButton();
        home().clickBuiltInRepositoriesButton();
        builtInRepositories().builtInRepositoriesCardPage().waitToLoad();
        builtInRepositories().builtInRepositoriesCardPage()
                .provisionRepositoryWithAdditionalInfo(PROJECT_NAME + TAGGED_IMAGE_PATH);
        containers().createContainerPage().waitToLoad();
        containers().createContainerPage().clickNetworkTab();
        containers().networkTab().addPortBinding(null, "80");
        containers().createContainerPage().submit();
        repositories().repositoriesPage().expandRequestsToolbar();
        repositories().requests().waitForLastRequestToSucceed(720);
        home().clickContainersButton();
        containers().containersPage().waitToLoad();
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
        home().clickBuiltInRepositoriesButton();
        builtInRepositories().builtInRepositoriesCardPage().switchToListView();
        builtInRepositories().builtInRepositoriesListPage()
                .selectRepositoryByName(PROJECT_NAME + TAGGED_IMAGE_PATH);
        builtInRepositories().builtInRepositoriesListPage().clickDeleteButton();
        // builtInRepositories().builtInRepositoriesCardPage().waitToLoad();
        // builtInRepositories().builtInRepositoriesCardPage()
        // .deleteRepository(PROJECT_NAME + TAGGED_IMAGE_PATH);

        builtInRepositories().deleteRepositoryModalDialog().waitToLoad();
        builtInRepositories().deleteRepositoryModalDialog().submit();
        builtInRepositories().deleteRepositoryModalDialog().waitForDeleteToComplete();
        builtInRepositories().deleteRepositoryModalDialog().close();
        home().clickContainerHostsButton();
        HostCommons.deleteHost(getClient(), HOST_NAME);

        main().clickAdministrationTabButton();
        projects().projectsPage().waitToLoad();
        ProjectCommons.deleteProject(getClient(), PROJECT_NAME);
        logOut();
    }

    private void pushImageToProject() {
        SshCommandExecutor executor = getUtilityVmExecutor();

        LOG.info(String.format("Creating remote folder [%s]", REMOTE_CERTIFICATE_FOLDER_PATH));
        String createDirCommand = String.format("mkdir -p " + REMOTE_CERTIFICATE_FOLDER_PATH);
        CommandResult result = executor.execute(createDirCommand, 10);
        logOutputOrThrow(createDirCommand, result);

        LOG.info(String.format("Sending certificate to remote path [%s]",
                REMOTE_CERTIFICATE_FOLDER_PATH));
        try {
            executor.sendFile(new File(LOCAL_CERTIFICATE_PATH), REMOTE_CERTIFICATE_FOLDER_PATH);
        } catch (IOException e) {
            String errorMessage = String.format(
                    "Could not send the certificate file to remote path [%s]",
                    REMOTE_CERTIFICATE_FOLDER_PATH);
            throw new RuntimeException(errorMessage, e);
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
                VicTestProperties.vicIp(), VicTestProperties.defaultAdminUsername(),
                VicTestProperties.defaultAdminPassword());
        result = executor.execute(loginCommand, 30);
        logOutputOrThrow(loginCommand, result);

        LOG.info(String.format("Pushing image [%s] to the registry", TAGGED_IMAGE));
        String pushCommnad = "docker push " + TAGGED_IMAGE;
        result = executor.execute(pushCommnad, 120);
        logOutputOrThrow(pushCommnad, result);
    }

    protected void logOutputOrThrow(String command, CommandResult result) {
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

    @Override
    protected List<String> getProjectNames() {
        return Arrays.asList(new String[] { PROJECT_NAME });
    }

}
