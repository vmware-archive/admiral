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

package com.vmware.admiral.test.ui.pages.registries;

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLibrary;

public class GlobalRegistriesPageLibrary extends PageLibrary {

    private GlobalRegistriesPage globalRegistriesPage;
    private SourceRegistriesTab sourceRegistriesTab;
    private RegistryCertificateModalDialog registryCertificateModalDialog;
    private AddGlobalRegistryForm addGlobalRegistryForm;

    public GlobalRegistriesPage registriesPage() {
        if (Objects.isNull(globalRegistriesPage)) {
            GlobalRegistriesPageLocators locators = new GlobalRegistriesPageLocators();
            GlobalRegistriesPageValidator validator = new GlobalRegistriesPageValidator(
                    getFrameLocators(),
                    locators);
            globalRegistriesPage = new GlobalRegistriesPage(getFrameLocators(), validator,
                    locators);
        }
        return globalRegistriesPage;
    }

    public SourceRegistriesTab sourceRegistriesTab() {
        if (Objects.isNull(sourceRegistriesTab)) {
            SourceRegistriesTabLocators locators = new SourceRegistriesTabLocators();
            SourceRegistriesTabValidator validator = new SourceRegistriesTabValidator(
                    getFrameLocators(),
                    locators);
            sourceRegistriesTab = new SourceRegistriesTab(new By[] { By.cssSelector("iframe") },
                    validator, locators);
        }
        return sourceRegistriesTab;
    }

    public RegistryCertificateModalDialog registryCertificateModalDialog() {
        if (Objects.isNull(registryCertificateModalDialog)) {
            RegistryCertificateModalDialogLocators locators = new RegistryCertificateModalDialogLocators();
            RegistryCertificateModalDialogValidator validator = new RegistryCertificateModalDialogValidator(
                    getFrameLocators(),
                    locators);
            registryCertificateModalDialog = new RegistryCertificateModalDialog(
                    new By[] { By.cssSelector("iframe") },
                    validator,
                    locators);
        }
        return registryCertificateModalDialog;

    }

    public AddGlobalRegistryForm addRegistryForm() {
        if (Objects.isNull(addGlobalRegistryForm)) {
            AddGlobalRegistryFormLocators locators = new AddGlobalRegistryFormLocators();
            addGlobalRegistryForm = new AddGlobalRegistryForm(new By[] { By.cssSelector("iframe") },
                    locators);
        }
        return addGlobalRegistryForm;
    }

    @Override
    protected By[] getFrameLocators() {
        return new By[] { By.cssSelector("iframe") };
    }
}
