/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common.util;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.vmware.admiral.common.util.ValidationUtils;
import com.vmware.xenon.common.LocalizableValidationException;

public class ValidationUtilsTest {

    @Test
    public void testValidateHost() {
        try {
            ValidationUtils.validateHost("a b");
            fail("expected to fail");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("is not valid"));
        }

        try {
            ValidationUtils.validateHost("a&b");
            fail("expected to fail");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("is not valid"));
        }

        try {
            ValidationUtils.validateHost("a%b");
            fail("expected to fail");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("is not valid"));
        }

        try {
            ValidationUtils.validateHost("a_b");
            fail("expected to fail");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("is not valid"));
        }

        ValidationUtils.validateHost("ab");
        ValidationUtils.validateHost("a-b");
        ValidationUtils.validateHost("a-b.com");
    }

    @Test
    public void testValidateContainerName() {
        try {
            ValidationUtils.validateContainerName("a");
            fail("expected to fail");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("Invalid container name"));
        }

        try {
            ValidationUtils.validateContainerName("a b");
            fail("expected to fail");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("Invalid container name"));
        }

        try {
            ValidationUtils.validateContainerName("a&b");
            fail("expected to fail");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("Invalid container name"));
        }

        try {
            ValidationUtils.validateContainerName("a%b");
            fail("expected to fail");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("Invalid container name"));
        }

        try {
            ValidationUtils.validateContainerName("_ab");
            fail("expected to fail");
        } catch (LocalizableValidationException e) {
            assertTrue(e.getMessage().contains("Invalid container name"));
        }

        ValidationUtils.validateContainerName("ab_");
        ValidationUtils.validateContainerName("a_b");
        ValidationUtils.validateContainerName("a-b.com");
    }
}
