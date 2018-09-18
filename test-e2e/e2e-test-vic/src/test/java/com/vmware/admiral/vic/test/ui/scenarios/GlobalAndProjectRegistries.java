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
import java.util.logging.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import com.vmware.admiral.test.ui.commons.HostCommons;
import com.vmware.admiral.test.ui.commons.ProjectCommons;
import com.vmware.admiral.test.ui.pages.containers.ContainersPage.ContainerState;
import com.vmware.admiral.test.ui.pages.projects.AddProjectModalDialog;
import com.vmware.admiral.test.util.AdmiralEventLogRule;
import com.vmware.admiral.test.util.HostType;
import com.vmware.admiral.test.util.ScreenshotRule;
import com.vmware.admiral.test.util.SshCommandExecutor;
import com.vmware.admiral.test.util.SshCommandExecutor.CommandResult;
import com.vmware.admiral.test.util.TestStatusLoggerRule;
import com.vmware.admiral.test.util.host.ContainerHostProviderRule;
import com.vmware.admiral.vic.test.VicTestProperties;
import com.vmware.admiral.vic.test.ui.BaseTestVic;
import com.vmware.admiral.vic.test.ui.UtilityVmInfo;
import com.vmware.admiral.vic.test.util.VICAuthTokenGetter;

public class GlobalAndProjectRegistries extends BaseTestVic {

    private static Logger LOGGER = Logger.getLogger(GlobalAndProjectRegistries.class.getName());

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

    private static final String REGISTRY_NAME = "registry";
    private static final String REGISTRY_CHEME = "https://";
    private static final String REMOTE_REGISTRY_CERTIFICATE_PATH = "/tmp/temporary_test_files/registry_certificates/";
    private static final String REGISTRY_CERTIFICATE_FILE_NAME = "ca.crt";
    private static final String IMAGE_NAME = "alpine";
    private static final String IMAGE_PATH_BASE = "test";
    private static final String FIRST_IMAGE_PATH = IMAGE_PATH_BASE + "/" + IMAGE_NAME;
    private static final String SECOND_IMAGE_PATH = IMAGE_PATH_BASE + "2/" + IMAGE_NAME;
    private static final String THIRD_IMAGE_PATH = IMAGE_PATH_BASE + "-three/" + IMAGE_NAME;

    private final String CONTAINER_NAME = "container";
    private static final String FIRST_PROJECT_HOST_NAME = "registries-host";
    private final String FIRST_PROJECT_NAME = "registries-project-1";
    private final String SECOND_PROJECT_NAME = "registries-project-2";

    private final String REGISTRY_IP = UtilityVmInfo.getIp();
    private String registryIpAndPort;
    private String registryAddress;
    private String registryContainerId;

    private final String REGISTRY_WHITELIST_ERROR_MESSAGE_BASE = "Registry whitelist check failed for image ";

    @Test
    public void globalAndProjectRegistriesTest() {
        prepareRegistryContainer();
        loginAsAdmin();
        main().clickAdministrationTabButton();
        administration().clickProjectsButton();
        projects().projectsPage().clickAddProjectButton();
        AddProjectModalDialog addProjectDialog = projects().addProjectDialog();
        addProjectDialog.waitToLoad();
        addProjectDialog.setName(FIRST_PROJECT_NAME);
        addProjectDialog.submit();

        projects().projectsPage().clickAddProjectButton();
        addProjectDialog = projects().addProjectDialog();
        addProjectDialog.waitToLoad();
        addProjectDialog.setName(SECOND_PROJECT_NAME);
        addProjectDialog.submit();

        main().clickHomeTabButton();
        home().switchToProject(FIRST_PROJECT_NAME);

        home().clickContainersButton();
        containers().containersPage().clickCreateContainer();
        containers().basicTab().setImage(registryIpAndPort + "/" + FIRST_IMAGE_PATH);
        containers().basicTab().setName(CONTAINER_NAME);
        containers().createContainerPage().submit();
        containers().requests().waitForLastRequestToFail(120);
        containers().requests().clickLastRequest();
        String errorMessage = containers().logs().getLastEventMessage();
        String expectedErrorMessage = REGISTRY_WHITELIST_ERROR_MESSAGE_BASE
                + registryIpAndPort + "/" + FIRST_IMAGE_PATH;
        validateErrorMessageMatch(expectedErrorMessage, errorMessage);

        main().clickAdministrationTabButton();
        administration().clickGlobalRegistriesButton();
        registries().sourceRegistriesTab().clickAddRegistryButton();
        registries().addRegistryForm().setAddress(REGISTRY_CHEME + registryIpAndPort);
        registries().addRegistryForm().setName(REGISTRY_NAME);
        registries().addRegistryForm().clickVerifyButton();
        registries().registryCertificateModalDialog().waitToLoad();
        registries().registryCertificateModalDialog().submit();
        registries().addRegistryForm().clickSaveButton();
        registries().sourceRegistriesTab().validate()
                .validateRegistryExistsWithAddress(REGISTRY_CHEME + registryIpAndPort);

        main().clickHomeTabButton();
        applications().applicationsPage().waitToLoad();

        home().clickContainerHostsButton();
        HostCommons.addHost(getClient(), FIRST_PROJECT_HOST_NAME, null,
                provider.getHost().getHostType(),
                getHostAddress(provider.getHost()), null, true);

        home().clickContainersButton();
        if (provider.getHost().getHostType() == HostType.DOCKER) {
            containers().containersPage().waitForContainerStateByExactName("admiral_agent",
                    ContainerState.RUNNING, 120);
        }
        containers().containersPage().clickCreateContainer();
        containers().basicTab().setImage(registryIpAndPort + "/" + FIRST_IMAGE_PATH);
        containers().basicTab().setName(CONTAINER_NAME);
        containers().createContainerPage().submit();
        containers().requests().waitForLastRequestToSucceed(120);
        containers().containersPage().refresh();
        containers().containersPage().deleteContainer(CONTAINER_NAME);
        containers().requests().waitForLastRequestToSucceed(120);

        home().clickRepositoriesButton();
        repositories().repositoriesPage().selectRegistry(REGISTRY_NAME);
        repositories().repositoriesPage().enterSearchCriteria(IMAGE_PATH_BASE);
        repositories().repositoriesPage().validate()
                .validateRepositoryExistsWithName(FIRST_IMAGE_PATH);
        repositories().repositoriesPage().validate()
                .validateRepositoryExistsWithName(SECOND_IMAGE_PATH);
        repositories().repositoriesPage().validate()
                .validateRepositoryExistsWithName(THIRD_IMAGE_PATH);
        repositories().repositoriesPage().enterSearchCriteria(FIRST_IMAGE_PATH);
        repositories().repositoriesPage().validate()
                .validateRepositoryExistsWithName(FIRST_IMAGE_PATH);
        repositories().repositoriesPage().validate()
                .validateRepositoryDoesNotExistWithName(SECOND_IMAGE_PATH);
        repositories().repositoriesPage().validate()
                .validateRepositoryDoesNotExistWithName(THIRD_IMAGE_PATH);

        main().clickAdministrationTabButton();
        administration().clickGlobalRegistriesButton();
        registries().sourceRegistriesTab().deleteRegistryByAddress(registryAddress);
        registries().sourceRegistriesTab().validate()
                .validateRegistryDoesNotExistWithAddress(registryAddress);

        administration().clickProjectsButton();
        projects().projectsPage().waitToLoad();
        projects().projectsPage().clickProjectDetailsButton(FIRST_PROJECT_NAME);
        projects().configureProjectPage().waitToLoad();
        projects().configureProjectPage().clickProjectRegistriesTabButton();
        projects().projectRegistriesTab().clickAddRegistryButton();
        projects().addRegistryForm().setAddress(registryAddress);
        projects().addRegistryForm().setName(REGISTRY_NAME);
        projects().addRegistryForm().submit();
        projects().projectRegistriesTab().validate().validateRegistryExistsWithName(REGISTRY_NAME);

        main().clickHomeTabButton();
        home().clickContainersButton();
        containers().containersPage().clickCreateContainer();
        containers().basicTab().setImage(registryIpAndPort + "/" + FIRST_IMAGE_PATH);
        containers().basicTab().setName(CONTAINER_NAME);
        containers().createContainerPage().submit();
        containers().requests().waitForLastRequestToSucceed(120);
        containers().containersPage().refresh();
        containers().containersPage().deleteContainer(CONTAINER_NAME);

        home().clickRepositoriesButton();
        repositories().repositoriesPage().selectRegistry(REGISTRY_NAME);
        repositories().repositoriesPage().enterSearchCriteria(IMAGE_PATH_BASE);
        repositories().repositoriesPage().validate()
                .validateRepositoryExistsWithName(FIRST_IMAGE_PATH);
        repositories().repositoriesPage().validate()
                .validateRepositoryExistsWithName(SECOND_IMAGE_PATH);
        repositories().repositoriesPage().validate()
                .validateRepositoryExistsWithName(THIRD_IMAGE_PATH);
        repositories().repositoriesPage().enterSearchCriteria(FIRST_IMAGE_PATH);
        repositories().repositoriesPage().validate()
                .validateRepositoryExistsWithName(FIRST_IMAGE_PATH);
        repositories().repositoriesPage().validate()
                .validateRepositoryDoesNotExistWithName(SECOND_IMAGE_PATH);
        repositories().repositoriesPage().validate()
                .validateRepositoryDoesNotExistWithName(THIRD_IMAGE_PATH);

        home().switchToProject(SECOND_PROJECT_NAME);
        home().clickContainersButton();
        containers().containersPage().clickCreateContainer();
        containers().createContainerPage().waitToLoad();
        containers().basicTab().setImage(registryIpAndPort + "/" + FIRST_IMAGE_PATH);
        containers().basicTab().setName(CONTAINER_NAME);
        containers().createContainerPage().submit();
        containers().requests().waitForLastRequestToFail(120);
        containers().requests().clickLastRequest();
        errorMessage = containers().logs().getLastEventMessage();
        expectedErrorMessage = REGISTRY_WHITELIST_ERROR_MESSAGE_BASE
                + registryIpAndPort + "/" + FIRST_IMAGE_PATH;
        validateErrorMessageMatch(expectedErrorMessage, errorMessage);

        home().clickRepositoriesButton();
        repositories().repositoriesPage().validate()
                .validateRegistryDoesNotExistWithName(REGISTRY_NAME);

        home().switchToProject(FIRST_PROJECT_NAME);
        main().clickAdministrationTabButton();
        projects().projectsPage().clickProjectDetailsButton(FIRST_PROJECT_NAME);
        projects().configureProjectPage().waitToLoad();
        projects().configureProjectPage().clickProjectRegistriesTabButton();
        projects().projectRegistriesTab().editRegistry(REGISTRY_NAME);
        projects().editRegistryForm().waitToLoad();
        projects().editRegistryForm().setAddress(registryAddress + "/" + IMAGE_PATH_BASE);
        projects().editRegistryForm().submit();
        projects().projectRegistriesTab().validate().validateRegistryExistsWithName(REGISTRY_NAME);
        main().clickHomeTabButton();
        home().clickRepositoriesButton();
        repositories().repositoriesPage().selectRegistry(REGISTRY_NAME);
        repositories().repositoriesPage().enterSearchCriteria(IMAGE_NAME);
        repositories().repositoriesPage().validate()
                .validateRepositoryExistsWithName(FIRST_IMAGE_PATH);
        repositories().repositoriesPage().validate()
                .validateRepositoryDoesNotExistWithName(SECOND_IMAGE_PATH);
        repositories().repositoriesPage().validate()
                .validateRepositoryDoesNotExistWithName(THIRD_IMAGE_PATH);
        // we switch between tabs because sometimes after searching for an image navigating to
        // another tab fails
        main().clickAdministrationTabButton();
        main().clickHomeTabButton();

        home().clickContainersButton();
        containers().containersPage().waitToLoad();
        containers().containersPage().clickCreateContainer();
        containers().basicTab().setImage(registryIpAndPort + "/" + SECOND_IMAGE_PATH);
        containers().basicTab().setName(CONTAINER_NAME);
        containers().createContainerPage().submit();
        containers().requests().waitForLastRequestToFail(120);
        containers().requests().clickLastRequest();
        errorMessage = containers().logs().getLastEventMessage();
        expectedErrorMessage = REGISTRY_WHITELIST_ERROR_MESSAGE_BASE
                + registryIpAndPort + "/" + SECOND_IMAGE_PATH;
        validateErrorMessageMatch(expectedErrorMessage, errorMessage);

        containers().containersPage().clickCreateContainer();
        containers().basicTab().setImage(registryIpAndPort + "/" + FIRST_IMAGE_PATH);
        containers().basicTab().setName(CONTAINER_NAME);
        containers().createContainerPage().submit();
        // Sometimes the request is not visible initially, instead the last visible request is the
        // previously failed one
        try {
            containers().requests().waitForLastRequestToSucceed(120);
        } catch (AssertionError e) {
            sleep(13000);
            containers().requests().waitForLastRequestToSucceed(120);
        }
        containers().containersPage().refresh();
        containers().containersPage().deleteContainer(CONTAINER_NAME);
        containers().requests().waitForLastRequestToSucceed(120);

        home().clickContainerHostsButton();
        HostCommons.deleteHost(getClient(), FIRST_PROJECT_HOST_NAME);

        main().clickAdministrationTabButton();
        projects().projectsPage().clickProjectDetailsButton(FIRST_PROJECT_NAME);
        projects().configureProjectPage().waitToLoad();
        projects().configureProjectPage().clickProjectRegistriesTabButton();
        projects().projectRegistriesTab().selectRegistryByName(REGISTRY_NAME);
        projects().projectRegistriesTab().clickDeleteButton();
        projects().deleteRegistryDialog().waitToLoad();
        projects().deleteRegistryDialog().submit();
        projects().projectRegistriesTab().waitToLoad();
        projects().projectRegistriesTab().validate()
                .validateRegistryDoesNotExistWithName(REGISTRY_NAME);
        administration().clickProjectsButton();
        projects().projectsPage().waitToLoad();
        ProjectCommons.deleteProject(getClient(), FIRST_PROJECT_NAME);
        ProjectCommons.deleteProject(getClient(), SECOND_PROJECT_NAME);
        logOut();
        killRegistryContainer();
    }

    private void validateErrorMessageMatch(String expectedErrorMessage, String actualErrorMessage) {
        if (!actualErrorMessage.equals(expectedErrorMessage)) {
            throw new AssertionError(String.format(
                    "Expected '%s' error message, but received:%n%s", expectedErrorMessage,
                    actualErrorMessage));
        }
    }

    public void prepareRegistryContainer() {
        LOGGER.info("Preparing docker registry");
        SshCommandExecutor executor = getUtilityVmExecutor();
        LOGGER.info("Generating certificates for the registry");
        String command = "mkdir -p " + REMOTE_REGISTRY_CERTIFICATE_PATH;
        executeAndValidateResult(executor, command, 10);
        command = "openssl req -newkey rsa:4096 -nodes -sha256"
                + " -keyout " + REMOTE_REGISTRY_CERTIFICATE_PATH + "ca.key"
                + " -x509 -days 365"
                + " -out " + REMOTE_REGISTRY_CERTIFICATE_PATH + REGISTRY_CERTIFICATE_FILE_NAME
                + " -subj \"/CN=" + UtilityVmInfo.getIp() + "\""
                + " -extensions SAN"
                + " -config <(cat /etc/ssl/openssl.cnf; printf \"[SAN]\nsubjectAltName=IP:"
                + UtilityVmInfo.getIp()
                + "\")";
        executeAndValidateResult(executor, command, 20);

        LOGGER.info("Creating registry container");
        command = "docker run -d"
                + " --restart=always"
                + " -v " + REMOTE_REGISTRY_CERTIFICATE_PATH + ":/certs"
                + " -e REGISTRY_HTTP_ADDR=0.0.0.0:443"
                + " -e REGISTRY_HTTP_TLS_CERTIFICATE=/certs/ca.crt"
                + " -e REGISTRY_HTTP_TLS_KEY=/certs/ca.key"
                + " -p :443"
                + " registry:2";
        registryContainerId = executeAndValidateResult(executor, command, 120);
        command = "docker port " + registryContainerId + " | sed -rn 's/(.*0.0.0.0:)//p'";
        String port = executeAndValidateResult(executor, command, 120);
        registryIpAndPort = REGISTRY_IP + ":" + port;
        registryAddress = REGISTRY_CHEME + registryIpAndPort;
        LOGGER.info("Pushing images to the registry");
        command = String.format("mkdir -p /etc/docker/certs.d/%s && cp %s /etc/docker/certs.d/%s",
                registryIpAndPort,
                REMOTE_REGISTRY_CERTIFICATE_PATH + REGISTRY_CERTIFICATE_FILE_NAME,
                registryIpAndPort);
        executeAndValidateResult(executor, command, 10);
        command = "docker pull alpine";
        executeAndValidateResult(executor, command, 60);
        tagImageAndPush(executor, FIRST_IMAGE_PATH);
        tagImageAndPush(executor, SECOND_IMAGE_PATH);
        tagImageAndPush(executor, THIRD_IMAGE_PATH);
        command = String.format("docker rmi %s %s %s %s",
                registryIpAndPort + "/" + FIRST_IMAGE_PATH,
                registryIpAndPort + "/" + SECOND_IMAGE_PATH,
                registryIpAndPort + "/" + THIRD_IMAGE_PATH, IMAGE_NAME);
        executeAndValidateResult(executor, command, 60);
        LOGGER.info("Configuring iptables for the registry port");
        command = "iptables -w -A INPUT -j ACCEPT -p tcp --dport " + port;
        executeAndValidateResult(executor, command, 10);
    }

    public void killRegistryContainer() {
        LOG.info(String.format("Killing registry container with id '%s'", registryContainerId));
        String command = "docker rm -f " + registryContainerId;
        try {
            executeAndValidateResult(getUtilityVmExecutor(), command, 10);
        } catch (Throwable e) {
            LOG.warning(String.format("Could not kill registry container with id '%s', error:%n%s",
                    registryContainerId, ExceptionUtils.getStackTrace(e)));
        }
    }

    private void tagImageAndPush(SshCommandExecutor executor, String imagePath) {
        String taggedImage = registryIpAndPort + "/" + imagePath;
        String command = String.format("docker tag %s %s && docker push %s", IMAGE_NAME,
                taggedImage, taggedImage);
        executeAndValidateResult(executor, command, 30);
    }

    private static String executeAndValidateResult(SshCommandExecutor executor, String command,
            int timeout) {
        CommandResult result = executor.execute(command, timeout);
        if (result.getExitStatus() != 0) {
            String error = String.format(
                    "Command [%s] failed with exit status [%d], error output:\n%s",
                    command, result.getExitStatus(), result.getErrorOutput());
            LOGGER.severe(error);
            throw new RuntimeException(error);
        }
        return result.getOutput();
    }

    protected List<String> getProjectNames() {
        return Arrays.asList(new String[] { FIRST_PROJECT_NAME, SECOND_PROJECT_NAME });
    }

}
