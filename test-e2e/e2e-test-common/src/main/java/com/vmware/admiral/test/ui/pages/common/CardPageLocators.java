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

package com.vmware.admiral.test.ui.pages.common;

import org.openqa.selenium.By;

public class CardPageLocators extends PageLocators {

    private final String CARD_TITLE_SELECTOR_XPATH = "//div[contains(concat(' ', @class, ' '), ' grid-item ')]//div[contains(concat(' ', @class, ' '), ' title ')]";
    private final String ANCESTOR_CARD_LOCATOR_XPATH = "/ancestor::div[contains(concat(' ', @class, ' '), ' grid-item ')]";
    private final String CARD_SELECTOR_BY_EXACT_TITLE_XPATH = CARD_TITLE_SELECTOR_XPATH
            + "[./@title='%s']" + ANCESTOR_CARD_LOCATOR_XPATH;
    private final String CARD_SELECTOR_BY_TITLE_PREFIX_XPATH = CARD_TITLE_SELECTOR_XPATH
            + "[starts-with(@title, '%s-')]" + ANCESTOR_CARD_LOCATOR_XPATH;
    private final String CARD_HEADER_XPATH = "//div[contains(concat(' ', @class, ' '), ' status ')]";
    private final String CARD_TITLE_XPATH = "//div[contains(concat(' ', @class, ' '), ' title ')]";
    private final String CARD_DELETE_BUTTON_XPATH = "//a[contains(concat(' ', @class, ' '), ' container-action-remove ')]";
    private final String CARD_DELETE_CONFIRM_XPATH = "//div[contains(concat(' ', @class, ' '), ' delete-inline-item-confirmation-confirm ')]";
    private final String CARD_DELETE_CONFIRMATION_HOLDER_XPATH = "//div[contains(concat(' ', @class, ' '), ' delete-inline-item-confirmation-holder')]";

    public String cardByExactTitleXpath(String title) {
        return String.format(CARD_SELECTOR_BY_EXACT_TITLE_XPATH, title);
    }

    public String cardByTitlePrefixXpath(String titlePrefix) {
        return String.format(CARD_SELECTOR_BY_TITLE_PREFIX_XPATH, titlePrefix);
    }

    public By cardByExactTitle(String title) {
        return By.xpath(cardByExactTitleXpath(title));
    }

    public By cardHeaderByExactTitle(String title) {
        return By.xpath(cardByExactTitleXpath(title) + CARD_HEADER_XPATH);
    }

    public By cardTitleByExactTitle(String title) {
        return By.xpath(cardByExactTitleXpath(title) + CARD_TITLE_XPATH);
    }

    public By cardDeleteButtonByExactTitle(String title) {
        return By.xpath(cardByExactTitleXpath(title) + CARD_DELETE_BUTTON_XPATH);
    }

    public By cardDeleteConfirmButtonByExactTitle(String title) {
        return By.xpath(cardByExactTitleXpath(title) + CARD_DELETE_CONFIRM_XPATH);
    }

    public By cardByTitlePrefix(String titlePrefix) {
        return By.xpath(cardByTitlePrefixXpath(titlePrefix));
    }

    public By cardHeaderByTitlePrefix(String titlePrefix) {
        return By.xpath(cardByTitlePrefixXpath(titlePrefix) + CARD_HEADER_XPATH);
    }

    public By cardTitleByTitlePrefix(String titlePrefix) {
        return By.xpath(cardByTitlePrefixXpath(titlePrefix) + CARD_TITLE_XPATH);
    }

    public By cardDeleteButtonByTitlePrefix(String titlePrefix) {
        return By.xpath(cardByTitlePrefixXpath(titlePrefix) + CARD_DELETE_BUTTON_XPATH);
    }

    public By cardDeleteConfirmButtonByTitlePrefix(String titlePrefix) {
        return By.xpath(cardByTitlePrefixXpath(titlePrefix) + CARD_DELETE_CONFIRM_XPATH);
    }

    public By cardDeleteConfirmationHolderByExactTitle(String exactTitle) {
        return By.xpath(cardByExactTitleXpath(exactTitle) + CARD_DELETE_CONFIRMATION_HOLDER_XPATH);
    }

    public By cardDeleteConfirmationHolderByTitlePrefix(String titlePrefix) {
        return By
                .xpath(cardByTitlePrefixXpath(titlePrefix) + CARD_DELETE_CONFIRMATION_HOLDER_XPATH);
    }

}
