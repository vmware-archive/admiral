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

package com.vmware.admiral.vic.test.ui.pages.internalrepos;

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLibrary;

public class BuiltInRepositoriesPageLibrary extends PageLibrary {

    private BuiltInRepositoriesListPage builtInRepositoriesListPage;
    private DeleteRepositoryModalDialog deleteRepositoryModalDialog;
    private BuiltInRepositoriesCardPage builtInRepositoriesCardPage;

    public BuiltInRepositoriesListPage internalRepositoriesListPage() {
        if (Objects.isNull(builtInRepositoriesListPage)) {
            BuiltInRepositoriesListPageLocators locators = new BuiltInRepositoriesListPageLocators();
            BuiltInRepositoriesListPageValidator validator = new BuiltInRepositoriesListPageValidator(
                    getFrameLocators(), locators);
            builtInRepositoriesListPage = new BuiltInRepositoriesListPage(getFrameLocators(),
                    validator,
                    locators);
        }
        return builtInRepositoriesListPage;
    }

    public BuiltInRepositoriesCardPage internalRepositoriesCardPage() {
        if (Objects.isNull(builtInRepositoriesCardPage)) {
            BuiltInRepositoriesCardPageLocators locators = new BuiltInRepositoriesCardPageLocators();
            BuiltInRepositoriesCardPageValidator validator = new BuiltInRepositoriesCardPageValidator(
                    getFrameLocators(), locators);
            builtInRepositoriesCardPage = new BuiltInRepositoriesCardPage(getFrameLocators(),
                    validator,
                    locators);
        }
        return builtInRepositoriesCardPage;
    }

    public DeleteRepositoryModalDialog deleteRepositoryModalDialog() {
        if (Objects.isNull(deleteRepositoryModalDialog)) {
            DeleteRepositoryModalDialogLocators locators = new DeleteRepositoryModalDialogLocators();
            DeleteRepositoryModalDialogValidator validator = new DeleteRepositoryModalDialogValidator(
                    getFrameLocators(), locators);
            deleteRepositoryModalDialog = new DeleteRepositoryModalDialog(getFrameLocators(),
                    validator, locators);
        }
        return deleteRepositoryModalDialog;
    }

    @Override
    protected By[] getFrameLocators() {
        return null;
    }

}
