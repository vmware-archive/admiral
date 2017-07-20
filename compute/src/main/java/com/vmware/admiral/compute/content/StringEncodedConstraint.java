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

/**
 * Definition of a constraint to be used in the template instead of the more complex
 * underlying {@link com.vmware.photon.controller.model.Constraint}/{@link com.vmware.photon.controller.model.Constraint.Condition} structure.
 *
 * This allows for yaml declaration like this:
 * <pre>
 *   constraints:
 *     - tag: "!location:eu:hard"
 *     - tag: "location:us:soft"
 *     - tag: "!windows"
 * </pre>
 */
public class StringEncodedConstraint {
    /**
     * Defines a tag constraint in a single string in the following format:
     *
     * {@code [!]tagKey[:tagValue]:[soft|hard]}
     */
    public String tag;
}
