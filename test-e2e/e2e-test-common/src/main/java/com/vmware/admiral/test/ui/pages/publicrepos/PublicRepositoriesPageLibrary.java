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

package com.vmware.admiral.test.ui.pages.publicrepos;

import java.util.Objects;

import com.vmware.admiral.test.ui.pages.common.ResourcePageLibrary;

public class PublicRepositoriesPageLibrary extends ResourcePageLibrary {

    private PublicRepositoriesPage publicRepositories;

    public PublicRepositoriesPage publicRepositories() {
        if (Objects.isNull(publicRepositories)) {
            PublicRepositoriesPageLocators locators = new PublicRepositoriesPageLocators();
            PublicRepositoriesPageValidator validator = new PublicRepositoriesPageValidator(
                    getFrameLocators(), locators);
            publicRepositories = new PublicRepositoriesPage(getFrameLocators(), validator,
                    locators);
        }
        return publicRepositories;
    }

}
