/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.i18n;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.Test;

public class Utf8PropertiesResourceBundlesTest {

    private static final String RESOURCE_BUNDLE_BASE_NAME = "i18n/messages";
    private static final Locale LOCALE_BG = Locale.forLanguageTag("bg");

    private static final String TEST_MESSAGE_CODE = "test.message.code";
    private static final String TEST_MESSAGE_VALUE_EN = "Test message";
    private static final String TEST_MESSAGE_VALUE_BG = "Тестово съобщение";

    @Test
    public void testIsoCompliantMessage() {
        ResourceBundle bundle = ResourceBundle.getBundle(RESOURCE_BUNDLE_BASE_NAME, Locale.ENGLISH);
        assertEquals(TEST_MESSAGE_VALUE_EN, bundle.getString(TEST_MESSAGE_CODE));
    }

    @Test
    public void testUtf8LocalizationMessage() {
        ResourceBundle bundle = ResourceBundle.getBundle(RESOURCE_BUNDLE_BASE_NAME, LOCALE_BG);
        assertEquals(TEST_MESSAGE_VALUE_BG, bundle.getString(TEST_MESSAGE_CODE));
    }

}
