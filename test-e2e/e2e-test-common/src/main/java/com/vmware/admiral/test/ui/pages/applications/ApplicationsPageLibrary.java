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

package com.vmware.admiral.test.ui.pages.applications;

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ResourcePageLibrary;
import com.vmware.admiral.test.ui.pages.templates.create.CreateTemplatePageLocators;
import com.vmware.admiral.test.ui.pages.templates.create.CreateTemplatePageValidator;

public class ApplicationsPageLibrary extends ResourcePageLibrary {

    private ApplicationsPage applicationsPage;
    private CreateApplicationPage createApplicationPage;
    private By[] iframeLocators = new By[] { By.cssSelector("#admiral-content-frame") };

    public ApplicationsPage applicationsPage() {
        if (Objects.isNull(applicationsPage)) {
            ApplicationsPageLocators locators = new ApplicationsPageLocators();
            ApplicationsPageValidator validator = new ApplicationsPageValidator(getFrameLocators(),
                    locators);
            applicationsPage = new ApplicationsPage(getFrameLocators(), validator, locators);
        }
        return applicationsPage;
    }

    public CreateApplicationPage createApplicationPage() {
        if (Objects.isNull(createApplicationPage)) {
            CreateTemplatePageLocators locators = new CreateTemplatePageLocators();
            CreateTemplatePageValidator validator = new CreateTemplatePageValidator(
                    getFrameLocators(),
                    locators);
            createApplicationPage = new CreateApplicationPage(getFrameLocators(), validator,
                    locators);
        }
        return createApplicationPage;
    }

    @Override
    protected By[] getFrameLocators() {
        return iframeLocators;
    }

}
