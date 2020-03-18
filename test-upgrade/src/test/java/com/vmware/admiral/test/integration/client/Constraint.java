/*
 * Copyright (c) 2017-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.integration.client;

import java.util.List;
import javax.xml.bind.annotation.XmlType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
@XmlType(name = "ComputeConstraint")
public class Constraint {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Condition {

        @JsonIgnoreProperties(ignoreUnknown = true)
        @XmlType(name = "ConstraintConditionType")
        public enum Type {
            TAG, FIELD
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public enum Enforcement {
            HARD, SOFT
        }

        public Type type;

        public Enforcement enforcement = Enforcement.HARD;

        public QueryTask.Query.Occurance occurrence = QueryTask.Query.Occurance.MUST_OCCUR;

        public QueryTask.QueryTerm expression;

        public static Condition forTag(String key, String value, Enforcement enforcement,
                QueryTask.Query.Occurance occurrence) {
            Condition condition = new Condition();
            condition.type = Type.TAG;
            condition.enforcement = enforcement;
            condition.occurrence = occurrence;

            QueryTask.QueryTerm term = new QueryTask.QueryTerm();
            term.propertyName = key + ((value != null && !value.isEmpty()) ? (":" + value) : "");
            term.matchType = QueryTask.QueryTerm.MatchType.TERM;
            condition.expression = term;

            return condition;
        }

        public static Condition forTag(String constraint) {
            Condition condition = new Condition();
            condition.type = Type.TAG;
            condition.occurrence = constraint.startsWith("!")
                    ? QueryTask.Query.Occurance.MUST_NOT_OCCUR
                    : QueryTask.Query.Occurance.MUST_OCCUR;
            if (constraint.startsWith("!")) {
                constraint = constraint.substring(1);
            }

            QueryTask.QueryTerm term = new QueryTask.QueryTerm();
            int lastColon = constraint.lastIndexOf(":");
            if (lastColon < 0) {
                term.propertyName = constraint;
                // default to HARD
                condition.enforcement = Enforcement.HARD;
            } else {
                term.propertyName = constraint.substring(0, lastColon);
                condition.enforcement = Enforcement.valueOf(
                        constraint.substring(lastColon + 1).toUpperCase());
            }

            term.matchType = QueryTask.QueryTerm.MatchType.TERM;
            condition.expression = term;

            return condition;
        }
    }

    public List<Condition> conditions;
}
