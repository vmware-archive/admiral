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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vmware.admiral.compute.content.TemplateComputeDescription.StringEncodedConstraint;
import com.vmware.photon.controller.model.Constraint.Condition;
import com.vmware.photon.controller.model.Constraint.Condition.Enforcement;
import com.vmware.photon.controller.model.Constraint.Condition.Type;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

/**
 * Utility class for conversions between {@link StringEncodedConstraint} that is used in declarative
 * compute description and {@link Condition} that is used in the back-end.
 */
public class ConstraintConverter {
    private static final String TAG_CONSTRAINT_PATTERN =
            "^(!)?([^\\:]+)(?:\\:([^\\:]+))??(?:\\:(\\bsoft\\b|\\bhard\\b))?$";
    private static final Pattern tagConstraintPattern = Pattern.compile(TAG_CONSTRAINT_PATTERN,
            Pattern.CASE_INSENSITIVE);

    public static StringEncodedConstraint encodeCondition(Condition condition) {
        if (condition == null || !Type.TAG.equals(condition.type) || condition.expression == null
                || condition.expression.propertyName == null) {
            return null;
        }

        StringBuilder tagStringBuilder = new StringBuilder();
        if (Occurance.MUST_NOT_OCCUR.equals(condition.occurrence)) {
            tagStringBuilder.append("!");
        }
        tagStringBuilder.append(condition.expression.propertyName);
        if (!Enforcement.HARD.equals(condition.enforcement)) {
            tagStringBuilder.append(":");
            tagStringBuilder.append(condition.enforcement.toString().toLowerCase());
        }

        StringEncodedConstraint stringConstraint = new StringEncodedConstraint();
        stringConstraint.tag = tagStringBuilder.toString();
        return stringConstraint;
    }

    public static Condition decodeCondition(StringEncodedConstraint stringConstraint) {
        if (stringConstraint == null || stringConstraint.tag == null
                || stringConstraint.tag.isEmpty()) {
            return null;
        }

        Matcher m = tagConstraintPattern.matcher(stringConstraint.tag);
        if (!m.find() || (m.groupCount() != 4)) {
            Utils.logWarning("Invalid tag constraint : %s", stringConstraint.tag);
            return null;
        }

        Occurance occurrence = m.group(1) != null ? Occurance.MUST_NOT_OCCUR : Occurance.MUST_OCCUR;
        Enforcement enforcement = m.group(4) != null ? Enforcement.valueOf(m.group(4).toUpperCase())
                : Enforcement.HARD;
        return Condition.forTag(m.group(2), m.group(3), enforcement, occurrence);
    }
}
