/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertEquals;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getSystemOrTestProp;

import java.net.URI;
import java.util.stream.Collectors;

import com.google.common.io.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Utils;

public class AdmiralUpgradeIT extends AdmiralUpgradeBaseIT {
    private static final String TEMPLATE_FILE = "Admiral_master_and_0.9.1_release.yaml";
    private static final String ADMIRAL_BRANCH_NAME = "admiral-branch";
    private static final String ADMIRAL_MASTER_NAME = "admiral-master";

    private static final String UPGRADE_SKIP_INITIALIZE = "upgrade.skip.initialize";
    private static final String UPGRADE_SKIP_VALIDATE = "upgrade.skip.validate";
    private static final String UPGRADE_CONTAINERS_LOGS_DIR = "upgrade.containers.logs.dir";

    private ContainerState admiralBranchContainer;
    private ContainerState admiralMasterContainer;

    private String compositeDescriptionLink;
    private String containersLogsDir;

    @Before
    public void setUp() throws Exception {
        containersLogsDir = getSystemOrTestProp(UPGRADE_CONTAINERS_LOGS_DIR,
                Files.createTempDir().getAbsolutePath());
        compositeDescriptionLink = importTemplate(serviceClient, TEMPLATE_FILE);
    }

    @After
    public void cleanUp() {
        setBaseURI(null);
    }

    @Test
    public void testProvision() throws Exception {
        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API);
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return compositeDescriptionLink;
    }

    @Override
    protected void validateAfterStart(String resourceDescLink, RequestBrokerState request)
            throws Exception {
        assertEquals("Unexpected number of resource links", 1,
                request.resourceLinks.size());

        CompositeComponent cc = getDocument(request.resourceLinks.iterator().next(),
                CompositeComponent.class);

        String admiralBranchContainerLink = cc.componentLinks.stream()
                .filter((l) -> l.contains(ADMIRAL_BRANCH_NAME))
                .collect(Collectors.toList()).get(0);
        waitForStatusCode(URI.create(getBaseUrl() + admiralBranchContainerLink),
                Operation.STATUS_CODE_OK);
        admiralBranchContainer = getDocument(admiralBranchContainerLink, ContainerState.class);

        String admiralMasterContainerLink = cc.componentLinks.stream()
                .filter((l) -> l.contains(ADMIRAL_MASTER_NAME))
                .collect(Collectors.toList()).get(0);
        waitForStatusCode(URI.create(getBaseUrl() + admiralMasterContainerLink),
                Operation.STATUS_CODE_OK);
        admiralMasterContainer = getDocument(admiralMasterContainerLink, ContainerState.class);

        try {
            String skipInit = getSystemOrTestProp(UPGRADE_SKIP_INITIALIZE, "false");
            if (skipInit.equals(Boolean.FALSE.toString())) {
                logger.info("---------- Initialize content before upgrade. --------");
                addContentToTheProvisionedAdmiral(admiralBranchContainer);
            } else {
                logger.info("---------- Skipping content initialization. --------");
            }

            String skipValidate = getSystemOrTestProp(UPGRADE_SKIP_VALIDATE, "false");
            if (skipValidate.equals(Boolean.FALSE.toString())) {
                logger.info("---------- Migrate data and validate content. --------");
                migrateData(admiralBranchContainer, admiralMasterContainer);
                validateContent(admiralMasterContainer);
                removeData(admiralMasterContainer);
            } else {
                logger.info("---------- Skipping content validation. --------");
            }
        } finally {
            storeContainersLogs();
        }
    }

    private void storeContainersLogs() {
        try {
            storeContainerLogs(admiralBranchContainer, containersLogsDir);
        } catch (Throwable e) {
            logger.error("Failed to store logs for branch container: %s", Utils.toString(e));
        }
        try {
            storeContainerLogs(admiralMasterContainer, containersLogsDir);
        } catch (Throwable e) {
            logger.error("Failed to store logs for master container: %s", Utils.toString(e));
        }
    }

}
