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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import com.vmware.admiral.test.ui.commons.HostCommons;
import com.vmware.admiral.test.ui.commons.ProjectCommons;
import com.vmware.admiral.test.ui.pages.containers.ContainersPage.ContainerState;
import com.vmware.admiral.test.util.AdmiralEventLogRule;
import com.vmware.admiral.test.util.HostType;
import com.vmware.admiral.test.util.ScreenshotRule;
import com.vmware.admiral.test.util.SshCommandExecutor.CommandResult;
import com.vmware.admiral.test.util.TestStatusLoggerRule;
import com.vmware.admiral.test.util.host.ContainerHost;
import com.vmware.admiral.test.util.host.ContainerHostProviderRule;
import com.vmware.admiral.test.util.host.PhotonDindProviderRule;
import com.vmware.admiral.vic.test.VicTestProperties;
import com.vmware.admiral.vic.test.ui.BaseTestVic;
import com.vmware.admiral.vic.test.util.VICAuthTokenGetter;

public class GlobalAndProjectRegistries extends BaseTestVic {

    private static Logger LOGGER = Logger.getLogger(GlobalAndProjectRegistries.class.getName());

    public ContainerHostProviderRule provider = new ContainerHostProviderRule(true, false);

    public PhotonDindProviderRule registryHostProvider = new PhotonDindProviderRule(false, false);

    @Rule
    public TestRule rules = RuleChain
            .outerRule(new TestStatusLoggerRule())
            .around(provider)
            .around(registryHostProvider)
            .around(new AdmiralEventLogRule(getVicTarget(), getProjectNames(),
                    () -> VICAuthTokenGetter.getVICAuthToken(getVicTarget(),
                            VicTestProperties.defaultAdminUsername(),
                            VicTestProperties.defaultAdminPassword())))
            .around(new ScreenshotRule());

    private static final String REGISTRY_NAME = "registry";
    private static final String REGISTRY_SCHEME = "https://";
    private String REGISTRY_PORT = "443";
    private static final String IMAGE_NAME = "alpine";
    private static final String IMAGE_PATH_BASE = "test";
    private static final String FIRST_IMAGE_PATH = IMAGE_PATH_BASE + "/" + IMAGE_NAME;
    private static final String SECOND_IMAGE_PATH = IMAGE_PATH_BASE + "2/" + IMAGE_NAME;
    private static final String THIRD_IMAGE_PATH = IMAGE_PATH_BASE + "-three/" + IMAGE_NAME;

    private final String CONTAINER_NAME = "container";
    private static final String FIRST_PROJECT_HOST_NAME = "registries-host";
    private final String FIRST_PROJECT_NAME = "registries-project-1";
    private final String SECOND_PROJECT_NAME = "registries-project-2";

    private String registryIpAndPort;
    private String registryAddress;

    private final String REGISTRY_WHITELIST_ERROR_MESSAGE_BASE = "Registry whitelist check failed for image ";

    @Test
    public void globalAndProjectRegistriesTest() {
        prepareRegistryContainer();
        loginAsAdmin();
        main().clickAdministrationTabButton();
        administration().clickProjectsButton();

        ProjectCommons.addProject(getClient(), FIRST_PROJECT_NAME, null, false);

        ProjectCommons.addProject(getClient(), SECOND_PROJECT_NAME, null, false);

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
        registries().addRegistryForm().setAddress(registryAddress);
        registries().addRegistryForm().setName(REGISTRY_NAME);
        registries().addRegistryForm().clickVerifyButton();
        registries().registryCertificateModalDialog().waitToLoad();
        registries().registryCertificateModalDialog().submit();
        registries().addRegistryForm().clickSaveButton();
        registries().sourceRegistriesTab().validate()
                .validateRegistryExistsWithAddress(registryAddress);

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
        applications().applicationsPage().waitToLoad();
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
        // Sometimes the request is not visible initially, instead the last visible request is
        // the previously failed one
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
        ContainerHost registryHost = registryHostProvider.getHost();
        String registryIp = registryHost.getIp();
        registryIpAndPort = registryIp + ":" + REGISTRY_PORT;
        registryAddress = REGISTRY_SCHEME + registryIpAndPort;
        LOGGER.info("Generating certificates for the registry");
        String certificateGenCommand = "export IP=" + registryIp
                + " && export CERTS_DIR=/etc/docker/registry/certs"
                + " && mkdir -p $CERTS_DIR"
                + " && cp /etc/ssl/openssl.cnf $CERTS_DIR/"
                + " && echo [SAN] >> $CERTS_DIR/openssl.cnf"
                + " && echo subjectAltName=IP:$IP >> $CERTS_DIR/openssl.cnf"
                + " && openssl req -newkey rsa:4096 -nodes -sha256 -keyout $CERTS_DIR/ca.key -x509 -days 365 -out $CERTS_DIR/ca.crt -subj /CN=$IP -extensions SAN -config $CERTS_DIR/openssl.cnf &&"
                + " mkdir -p /etc/docker/certs.d/$IP && cp $CERTS_DIR/ca.crt /etc/docker/certs.d/$IP/";
        CommandResult result = registryHostProvider.executeCommandInContainer(certificateGenCommand,
                30);
        if (result.getExitStatus() != 0) {
            String error = String.format(
                    "Failed to generate registry certificates, error output:\n%s",
                    result.getErrorOutput());
            LOGGER.severe(error);
            throw new RuntimeException(error);
        }
        LOGGER.info("Running registry container");
        String startRegistryCommand = "docker run -d"
                + " --restart=always"
                + " -v /etc/docker/registry/certs:/certs"
                + " -e REGISTRY_HTTP_ADDR=0.0.0.0:443"
                + " -e REGISTRY_HTTP_TLS_CERTIFICATE=/certs/ca.crt"
                + " -e REGISTRY_HTTP_TLS_KEY=/certs/ca.key"
                + " -p " + REGISTRY_PORT + ":443"
                + " registry:2";
        result = registryHostProvider.executeCommandInContainer(startRegistryCommand,
                30);
        if (result.getExitStatus() != 0) {
            String error = String.format(
                    "Failed to start registry container, error output:\n%s",
                    result.getErrorOutput());
            LOGGER.severe(error);
            throw new RuntimeException(error);
        }
        LOGGER.info("Pushing iamges to the registry");
        String tagAndPushCommand = "export IP=" + registryIp
                + " && docker pull alpine && docker tag alpine $IP/" + FIRST_IMAGE_PATH
                + " && docker tag alpine $IP/" + SECOND_IMAGE_PATH + " && docker tag alpine $IP/"
                + THIRD_IMAGE_PATH + " && docker push $IP/" + FIRST_IMAGE_PATH
                + " && docker push $IP/" + SECOND_IMAGE_PATH + " && docker push $IP/"
                + THIRD_IMAGE_PATH;
        result = registryHostProvider.executeCommandInContainer(tagAndPushCommand,
                30);
        if (result.getExitStatus() != 0) {
            String error = String.format(
                    "Failed to push images to the registry, error output:\n%s",
                    result.getErrorOutput());
            LOGGER.severe(error);
            throw new RuntimeException(error);
        }
    }

    protected List<String> getProjectNames() {
        return Arrays.asList(new String[] { FIRST_PROJECT_NAME, SECOND_PROJECT_NAME });
    }

}
