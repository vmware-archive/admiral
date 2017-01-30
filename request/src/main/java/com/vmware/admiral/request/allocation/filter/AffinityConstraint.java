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

package com.vmware.admiral.request.allocation.filter;

import static com.vmware.admiral.request.allocation.filter.AffinityConstraint.AffinityConstraintType.ANTI_AFFINITY_PREFIX;
import static com.vmware.admiral.request.allocation.filter.AffinityConstraint.AffinityConstraintType.HARD;
import static com.vmware.admiral.request.allocation.filter.AffinityConstraint.AffinityConstraintType.SOFT;

/**
 * Affinity constraint value type representing the name of a constraint, the type and the
 * anti-affinity indicator.
 */
public class AffinityConstraint {

    public static enum AffinityConstraintType {
        HARD(":hard"), SOFT(":soft");

        public static final String ANTI_AFFINITY_PREFIX = "!";

        AffinityConstraintType(String value) {
            this.value = value;
        }

        private final String value;

        public String getValue() {
            return value;
        }
    }

    public AffinityConstraint() {
        this.type = AffinityConstraintType.HARD;
    }

    public AffinityConstraint(String name) {
        this();
        this.name = name;
    }

    public String name;
    public AffinityConstraintType type;
    public boolean antiAffinity;

    public boolean isHard() {
        return type == AffinityConstraintType.HARD;
    }

    public boolean isSoft() {
        return type == AffinityConstraintType.SOFT;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (antiAffinity ? 1231 : 1237);
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AffinityConstraint other = (AffinityConstraint) obj;
        if (antiAffinity != other.antiAffinity) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (type != other.type) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "AffinityConstraint [name=" + name + ", type=" + type + ", antiAffinity="
                + antiAffinity + "]";
    }

    /**
     * Parses an {@code AffinityConstraint} instance from the given string representation.
     */
    public static AffinityConstraint fromString(String str) {
        AffinityConstraint constraint = new AffinityConstraint();
        final boolean anti_affinity = str.startsWith(ANTI_AFFINITY_PREFIX);
        if (anti_affinity) {
            str = str.replaceFirst(ANTI_AFFINITY_PREFIX, "");
            constraint.antiAffinity = true;
        }
        if (str.endsWith(SOFT.getValue())) {
            constraint.name = str.substring(0, str.length() - SOFT.getValue().length());
            constraint.type = SOFT;
        } else if (str.endsWith(HARD.getValue())) {
            constraint.name = str.substring(0, str.length() - HARD.getValue().length());
            constraint.type = HARD;
        } else {
            constraint.name = str;
            constraint.type = HARD;
        }

        return constraint;
    }
}
