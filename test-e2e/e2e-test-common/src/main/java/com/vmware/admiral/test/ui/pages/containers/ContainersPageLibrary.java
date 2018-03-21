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

import com.vmware.admiral.test.ui.pages.common.ResourcePageLibrary;
import com.vmware.admiral.test.ui.pages.containers.create.BasicTab;
import com.vmware.admiral.test.ui.pages.containers.create.BasicTabLocators;
import com.vmware.admiral.test.ui.pages.containers.create.BasicTabValidator;
import com.vmware.admiral.test.ui.pages.containers.create.CommandHealthConfigSubTab;
import com.vmware.admiral.test.ui.pages.containers.create.CommandHealthConfigSubTabLocators;
import com.vmware.admiral.test.ui.pages.containers.create.CreateContainerPage;
import com.vmware.admiral.test.ui.pages.containers.create.CreateContainerPageLocators;
import com.vmware.admiral.test.ui.pages.containers.create.CreateContainerPageValidator;
import com.vmware.admiral.test.ui.pages.containers.create.EnvironmentTab;
import com.vmware.admiral.test.ui.pages.containers.create.EnvironmentTabLocators;
import com.vmware.admiral.test.ui.pages.containers.create.EnvironmentTabValidator;
import com.vmware.admiral.test.ui.pages.containers.create.HealthConfigTab;
import com.vmware.admiral.test.ui.pages.containers.create.HealthConfigTabLocators;
import com.vmware.admiral.test.ui.pages.containers.create.HealthConfigTabValidator;
import com.vmware.admiral.test.ui.pages.containers.create.HttpHealthConfigSubTab;
import com.vmware.admiral.test.ui.pages.containers.create.HttpHealthConfigSubTabLocators;
import com.vmware.admiral.test.ui.pages.containers.create.LogConfigTab;
import com.vmware.admiral.test.ui.pages.containers.create.LogConfigTabLocators;
import com.vmware.admiral.test.ui.pages.containers.create.LogConfigTabValidator;
import com.vmware.admiral.test.ui.pages.containers.create.NetworkTab;
import com.vmware.admiral.test.ui.pages.containers.create.NetworkTabLocators;
import com.vmware.admiral.test.ui.pages.containers.create.NetworkTabValidator;
import com.vmware.admiral.test.ui.pages.containers.create.PolicyTab;
import com.vmware.admiral.test.ui.pages.containers.create.PolicyTabLocators;
import com.vmware.admiral.test.ui.pages.containers.create.PolicyTabValidator;
import com.vmware.admiral.test.ui.pages.containers.create.StorageTab;
import com.vmware.admiral.test.ui.pages.containers.create.StorageTabLocators;
import com.vmware.admiral.test.ui.pages.containers.create.StorageTabValidator;
import com.vmware.admiral.test.ui.pages.containers.create.TcpHealthConfigSubTab;
import com.vmware.admiral.test.ui.pages.containers.create.TcpHealthConfigSubTabLocators;

public class ContainersPageLibrary extends ResourcePageLibrary {

    private ContainersPage containersPage;
    private ContainerStatsPage containerStatsPage;

    private CreateContainerPage createContainerPage;
    private BasicTab basicTab;
    private NetworkTab networkTab;
    private StorageTab storageTab;
    private PolicyTab policyTab;
    private EnvironmentTab environmentTab;
    private HealthConfigTab healthConfigTab;
    private LogConfigTab logConfigTab;

    private TcpHealthConfigSubTab tcpHealthConfigSubTab;
    private HttpHealthConfigSubTab httpHealthConfigSubTab;
    private CommandHealthConfigSubTab commandHealthConfigSubTab;

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

    public PolicyTab policyTab() {
        if (Objects.isNull(policyTab)) {
            PolicyTabLocators locators = new PolicyTabLocators();
            PolicyTabValidator validator = new PolicyTabValidator(getFrameLocators(), locators);
            policyTab = new PolicyTab(getFrameLocators(), validator, locators);
        }
        return policyTab;
    }

    public EnvironmentTab environmentTab() {
        if (Objects.isNull(environmentTab)) {
            EnvironmentTabLocators locators = new EnvironmentTabLocators();
            EnvironmentTabValidator validator = new EnvironmentTabValidator(getFrameLocators(),
                    locators);
            environmentTab = new EnvironmentTab(getFrameLocators(), validator, locators);
        }
        return environmentTab;
    }

    public HealthConfigTab healthConfigTab() {
        if (Objects.isNull(healthConfigTab)) {
            HealthConfigTabLocators locators = new HealthConfigTabLocators();
            HealthConfigTabValidator validator = new HealthConfigTabValidator(getFrameLocators(),
                    locators);
            healthConfigTab = new HealthConfigTab(getFrameLocators(), validator, locators);
        }
        return healthConfigTab;
    }

    public TcpHealthConfigSubTab tcpHealthConfigSubTab() {
        if (Objects.isNull(tcpHealthConfigSubTab)) {
            TcpHealthConfigSubTabLocators locators = new TcpHealthConfigSubTabLocators();
            tcpHealthConfigSubTab = new TcpHealthConfigSubTab(getFrameLocators(), locators);
        }
        return tcpHealthConfigSubTab;
    }

    public HttpHealthConfigSubTab httpHealthConfigSubTab() {
        if (Objects.isNull(httpHealthConfigSubTab)) {
            HttpHealthConfigSubTabLocators locators = new HttpHealthConfigSubTabLocators();
            httpHealthConfigSubTab = new HttpHealthConfigSubTab(getFrameLocators(), locators);
        }
        return httpHealthConfigSubTab;
    }

    public CommandHealthConfigSubTab commandHealthConfigSubTab() {
        if (Objects.isNull(commandHealthConfigSubTab)) {
            CommandHealthConfigSubTabLocators locators = new CommandHealthConfigSubTabLocators();
            commandHealthConfigSubTab = new CommandHealthConfigSubTab(getFrameLocators(), locators);
        }
        return commandHealthConfigSubTab;
    }

    public LogConfigTab logConfigTab() {
        if (Objects.isNull(logConfigTab)) {
            LogConfigTabLocators locators = new LogConfigTabLocators();
            LogConfigTabValidator validator = new LogConfigTabValidator(getFrameLocators(),
                    locators);
            logConfigTab = new LogConfigTab(getFrameLocators(), validator, locators);
        }
        return logConfigTab;
    }

}
