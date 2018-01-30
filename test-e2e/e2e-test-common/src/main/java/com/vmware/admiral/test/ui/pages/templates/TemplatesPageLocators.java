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

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ResourcePageLocators;

public class TemplatesPageLocators extends ResourcePageLocators {

    private final By IMPORT_TEMPLATE_BUTTON = By
            .cssSelector(".btn.btn-link[href*=\"import-template\"]");
    private final String CARD_RELATIVE_PROVISION_BUTTON = "//button[contains(concat(' ', @class, ' '), ' create-container-btn ')]";
    private final String CARD_RELATIVE_PENCIL_BUTTON = "//i[contains(concat(' ', @class, ' '), ' fa-pencil ')]";

    public By importTemplateButton() {
        return IMPORT_TEMPLATE_BUTTON;
    }

    public By provisionButtonByCardTitle(String title) {
        return By.xpath(cardByExactTitleXpath(title) + CARD_RELATIVE_PROVISION_BUTTON);
    }

    public By editTemplateButtonByCardTitle(String title) {
        return By.xpath(cardByExactTitleXpath(title) + CARD_RELATIVE_PENCIL_BUTTON);
    }

}
