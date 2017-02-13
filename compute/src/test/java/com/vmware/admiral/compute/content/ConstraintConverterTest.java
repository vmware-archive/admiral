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

package com.vmware.admiral.compute.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.vmware.admiral.compute.content.TemplateComputeDescription.StringEncodedConstraint;
import com.vmware.photon.controller.model.Constraint.Condition;
import com.vmware.photon.controller.model.Constraint.Condition.Enforcement;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

/**
 * Tests for the {@link ConstraintConverter} class.
 */
public class ConstraintConverterTest {
    @Test
    public void testConversions() {
        test("key:soft", "key", false, false, "key:soft");
        test("key:value:soft", "key:value", false, false, "key:value:soft");
        test("!key:soft", "key", true, false, "!key:soft");

        test("key:hard", "key", false, true, "key");
        test("key:value:hard", "key:value", false, true, "key:value");
        test("!key:hard", "key", true, true, "!key");

        test("key", "key", false, true, "key");
        test("key:value", "key:value", false, true, "key:value");
        test("!key:value", "key:value", true, true, "!key:value");

        test("key:hard:hard", "key:hard", false, true, "key:hard");
        test("key:hard:soft", "key:hard", false, false, "key:hard:soft");

        test("key:sOFt", "key", false, false, "key:soft");
        test("key:VALUE:HARD", "key:VALUE", false, true, "key:VALUE");

        test("key with space:value", "key with space:value", false, true, "key with space:value");
        test("!key with space:value with space:soft", "key with space:value with space", true,
                false, "!key with space:value with space:soft");
        test("!key:value with special chars?! wow!!:hard", "key:value with special chars?! wow!!",
                true, true, "!key:value with special chars?! wow!!");
        test("!!key", "!key", true, true, "!!key");
    }

    @Test
    public void testInvalidConstraints() {
        testInvalid("key:value:value");
        testInvalid(":key");
    }

    private void test(String tagConstraint, String expectedPropertyName, boolean isAnti,
            boolean isHard, String expectedGeneratedTagConstraint) {
        StringEncodedConstraint stringConstraint = new StringEncodedConstraint();
        stringConstraint.tag = tagConstraint;

        Condition condition = ConstraintConverter.decodeCondition(stringConstraint);
        assertEquals(expectedPropertyName, condition.expression.propertyName);
        assertEquals(isAnti, Occurance.MUST_NOT_OCCUR.equals(condition.occurrence));
        assertEquals(isHard, Enforcement.HARD.equals(condition.enforcement));

        StringEncodedConstraint generatedTagConstraint = ConstraintConverter
                .encodeCondition(condition);
        assertEquals(expectedGeneratedTagConstraint, generatedTagConstraint.tag);
    }

    private void testInvalid(String tagConstraint) {
        StringEncodedConstraint stringConstraint = new StringEncodedConstraint();
        stringConstraint.tag = tagConstraint;

        Condition condition = ConstraintConverter.decodeCondition(stringConstraint);
        assertNull(condition);
    }
}
