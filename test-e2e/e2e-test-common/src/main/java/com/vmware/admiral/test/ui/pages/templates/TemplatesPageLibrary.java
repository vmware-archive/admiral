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

package com.vmware.admiral.test.ui.pages.templates;

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ResourcePageLibrary;
import com.vmware.admiral.test.ui.pages.containers.create.BasicTab;
import com.vmware.admiral.test.ui.pages.containers.create.BasicTabLocators;
import com.vmware.admiral.test.ui.pages.containers.create.BasicTabValidator;
import com.vmware.admiral.test.ui.pages.containers.create.CommandHealthConfigSubTab;
import com.vmware.admiral.test.ui.pages.containers.create.CommandHealthConfigSubTabLocators;
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
import com.vmware.admiral.test.ui.pages.templates.create.AddContainerPage;
import com.vmware.admiral.test.ui.pages.templates.create.AddContainerPageLocators;
import com.vmware.admiral.test.ui.pages.templates.create.AddContainerPageValidator;
import com.vmware.admiral.test.ui.pages.templates.create.AddNetworkPage;
import com.vmware.admiral.test.ui.pages.templates.create.AddNetworkPageLocators;
import com.vmware.admiral.test.ui.pages.templates.create.AddNetworkPageValidator;
import com.vmware.admiral.test.ui.pages.templates.create.AddVolumePage;
import com.vmware.admiral.test.ui.pages.templates.create.AddVolumePageLocators;
import com.vmware.admiral.test.ui.pages.templates.create.AddVolumePageValidator;
import com.vmware.admiral.test.ui.pages.templates.create.CreateTemplatePage;
import com.vmware.admiral.test.ui.pages.templates.create.CreateTemplatePageLocators;
import com.vmware.admiral.test.ui.pages.templates.create.CreateTemplatePageValidator;
import com.vmware.admiral.test.ui.pages.templates.create.EditTemplatePage;
import com.vmware.admiral.test.ui.pages.templates.create.EditTemplatePageLocators;
import com.vmware.admiral.test.ui.pages.templates.create.EditTemplatePageValidator;
import com.vmware.admiral.test.ui.pages.templates.create.SelectImagePage;
import com.vmware.admiral.test.ui.pages.templates.create.SelectImagePageLocators;
import com.vmware.admiral.test.ui.pages.templates.create.SelectImagePageValidator;

public class TemplatesPageLibrary extends ResourcePageLibrary {

    public TemplatesPageLibrary(By[] iframeLocators) {
        super(iframeLocators);
    }

    private TemplatesPage templatesPage;
    private EditTemplatePage editTemplatePage;
    private ImportTemplatePage importTemplatePage;
    private CreateTemplatePage createTemplatePage;
    private AddVolumePage addVolumePage;
    private SelectImagePage selectImagePage;
    private AddContainerPage addContainerPage;
    private AddNetworkPage addNetworkPage;

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

    public TemplatesPage templatesPage() {
        if (Objects.isNull(templatesPage)) {
            TemplatesPageLocators locators = new TemplatesPageLocators();
            TemplatesPageValidator validator = new TemplatesPageValidator(getFrameLocators(),
                    locators);
            templatesPage = new TemplatesPage(getFrameLocators(), validator, locators);
        }
        return templatesPage;
    }

    public EditTemplatePage editTemplatePage() {
        if (Objects.isNull(editTemplatePage)) {
            EditTemplatePageLocators locators = new EditTemplatePageLocators();
            EditTemplatePageValidator validator = new EditTemplatePageValidator(getFrameLocators(),
                    locators);
            editTemplatePage = new EditTemplatePage(getFrameLocators(), validator, locators);
        }
        return editTemplatePage;
    }

    public ImportTemplatePage importTemplatePage() {
        if (Objects.isNull(importTemplatePage)) {
            ImportTemplatePageLocators locators = new ImportTemplatePageLocators();
            ImportTemplatePageValidator validator = new ImportTemplatePageValidator(
                    getFrameLocators(), locators);
            importTemplatePage = new ImportTemplatePage(getFrameLocators(), validator, locators);
        }
        return importTemplatePage;
    }

    public CreateTemplatePage createTemplatePage() {
        if (Objects.isNull(createTemplatePage)) {
            CreateTemplatePageLocators locators = new CreateTemplatePageLocators();
            CreateTemplatePageValidator validator = new CreateTemplatePageValidator(
                    getFrameLocators(), locators);
            createTemplatePage = new CreateTemplatePage(getFrameLocators(), validator, locators);
        }
        return createTemplatePage;
    }

    public AddVolumePage addVolumePage() {
        if (Objects.isNull(addVolumePage)) {
            AddVolumePageLocators locators = new AddVolumePageLocators();
            AddVolumePageValidator validator = new AddVolumePageValidator(getFrameLocators(),
                    locators);
            addVolumePage = new AddVolumePage(getFrameLocators(), validator, locators);
        }
        return addVolumePage;
    }

    public SelectImagePage selectImagePage() {
        if (Objects.isNull(selectImagePage)) {
            SelectImagePageLocators locators = new SelectImagePageLocators();
            SelectImagePageValidator validator = new SelectImagePageValidator(getFrameLocators(),
                    locators);
            selectImagePage = new SelectImagePage(getFrameLocators(), validator, locators);
        }
        return selectImagePage;
    }

    public AddContainerPage addContainerPage() {
        if (Objects.isNull(addContainerPage)) {
            AddContainerPageLocators locators = new AddContainerPageLocators();
            AddContainerPageValidator validator = new AddContainerPageValidator(getFrameLocators(),
                    locators);
            addContainerPage = new AddContainerPage(getFrameLocators(), validator, locators);
        }
        return addContainerPage;
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
            tcpHealthConfigSubTab = new HttpHealthConfigSubTab(getFrameLocators(), locators);
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

    public AddNetworkPage addNetworkPage() {
        if (Objects.isNull(addNetworkPage)) {
            AddNetworkPageLocators locators = new AddNetworkPageLocators();
            AddNetworkPageValidator validator = new AddNetworkPageValidator(getFrameLocators(),
                    locators);
            addNetworkPage = new AddNetworkPage(getFrameLocators(), validator, locators);
        }
        return addNetworkPage;
    }

}
