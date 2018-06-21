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

package com.vmware.photon.controller.model;

import java.util.List;

import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * Definition of a constraint that one resource may have on other resources, typically expressed in
 * the resource's desired state.
 */
public class Constraint {
    /**
     * A definition of a single condition that is part of the constraint.
     */
    public static class Condition {
        /**
         * Supported types of constraint conditions.
         */
        public static enum Type {
            /**
             * The condition is against a tag on the resource.
             */
            TAG,

            /**
             * The condition is against a field on the resource.
             */
            FIELD
        }

        /**
         * Condition enforcement.
         */
        public static enum Enforcement {
            /**
             * Hard enforcement - the condition must be met.
             */
            HARD,

            /**
             * Soft enforcement - meeting the condition would be preferable but no failure will
             * occur if it is not met.
             */
            SOFT
        }

        /**
         * The type of the condition.
         */
        public Type type;

        /**
         * Whether this is a hard or soft condition.
         */
        public Enforcement enforcement = Enforcement.HARD;

        /**
         * The {@code occurrence} field allows turning the condition to an anti-condition - for
         * example, resources that do not have the given tag.
         */
        public Occurance occurrence = Occurance.MUST_OCCUR;

        /**
         * The actual condition expression.
         *
         * <p>
         * <ul>
         * <li>For tag conditions, the {@code propertyName} field should contain the string
         * representation of the tag to match (key:value) and no {@code matchValue} is expected.
         * </ul>
         */
        public QueryTerm expression;

        public static Condition forTag(String key, String value, Enforcement enforcement,
                Occurance occurrence) {
            Condition condition = new Condition();
            condition.type = Type.TAG;
            condition.enforcement = enforcement;
            condition.occurrence = occurrence;

            QueryTerm term = new QueryTerm();
            term.propertyName = key + ((value != null && !value.isEmpty()) ? (":" + value) : "");
            term.matchType = MatchType.TERM;
            condition.expression = term;

            return condition;
        }
    }

    /**
     * The list of conditions that define this constraint.
     */
    public List<Condition> conditions;
}
