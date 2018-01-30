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

package com.vmware.admiral.test.ui.pages.containers;

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ResourcePageLibrary;
import com.vmware.admiral.test.ui.pages.containers.create.BasicTab;
import com.vmware.admiral.test.ui.pages.containers.create.BasicTabLocators;
import com.vmware.admiral.test.ui.pages.containers.create.BasicTabValidator;
import com.vmware.admiral.test.ui.pages.containers.create.CreateContainerPage;
import com.vmware.admiral.test.ui.pages.containers.create.CreateContainerPageLocators;
import com.vmware.admiral.test.ui.pages.containers.create.CreateContainerPageValidator;
import com.vmware.admiral.test.ui.pages.containers.create.NetworkTab;
import com.vmware.admiral.test.ui.pages.containers.create.NetworkTabLocators;
import com.vmware.admiral.test.ui.pages.containers.create.NetworkTabValidator;
import com.vmware.admiral.test.ui.pages.containers.create.StorageTab;
import com.vmware.admiral.test.ui.pages.containers.create.StorageTabLocators;
import com.vmware.admiral.test.ui.pages.containers.create.StorageTabValidator;

public class ContainersPageLibrary extends ResourcePageLibrary {

    private final By[] iframeLocators = new By[] { By.cssSelector("#admiral-content-frame") };

    private ContainersPage containersPage;
    private ContainerStatsPage containerStatsPage;

    private CreateContainerPage createContainerPage;
    private BasicTab basicTab;
    private NetworkTab networkTab;
    private StorageTab storageTab;

    public ContainersPage containersPage() {
        if (Objects.isNull(containersPage)) {
            ContainersPageLocators locators = new ContainersPageLocators();
            ContainersPageValidator validator = new ContainersPageValidator(getFrameLocators(),
                    locators);
            containersPage = new ContainersPage(getFrameLocators(), validator, locators);
        }
        return containersPage;
    }

    public ContainerStatsPage containerStatsPage() {
        if (Objects.isNull(containerStatsPage)) {
            ContainerStatsPageLocators locators = new ContainerStatsPageLocators();
            ContainerStatsPageValidator validator = new ContainerStatsPageValidator(
                    getFrameLocators(), locators);
            containerStatsPage = new ContainerStatsPage(getFrameLocators(), validator, locators);
        }
        return containerStatsPage;
    }

    public CreateContainerPage createContainerPage() {
        if (Objects.isNull(createContainerPage)) {
            CreateContainerPageLocators locators = new CreateContainerPageLocators();
            CreateContainerPageValidator validator = new CreateContainerPageValidator(
                    getFrameLocators(), locators);
            createContainerPage = new CreateContainerPage(getFrameLocators(), validator, locators);
        }
        return createContainerPage;
    }

    public BasicTab basicTab() {
        if (Objects.isNull(basicTab)) {
            BasicTabLocators locators = new BasicTabLocators();
            BasicTabValidator validator = new BasicTabValidator(getFrameLocators(), locators);
            basicTab = new BasicTab(getFrameLocators(), validator, locators);
        }
        return basicTab;
    }

    public NetworkTab networkTab() {
        if (Objects.isNull(networkTab)) {
            NetworkTabLocators locators = new NetworkTabLocators();
            NetworkTabValidator validator = new NetworkTabValidator(getFrameLocators(), locators);
            networkTab = new NetworkTab(getFrameLocators(), validator, locators);
        }
        return networkTab;
    }

    public StorageTab storageTab() {
        if (Objects.isNull(storageTab)) {
            StorageTabLocators locators = new StorageTabLocators();
            StorageTabValidator validator = new StorageTabValidator(getFrameLocators(), locators);
            storageTab = new StorageTab(getFrameLocators(), validator, locators);
        }
        return storageTab;
    }

    @Override
    protected By[] getFrameLocators() {
        return iframeLocators;
    }

}
