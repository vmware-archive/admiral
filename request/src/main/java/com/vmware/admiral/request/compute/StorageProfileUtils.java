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

package com.vmware.admiral.request.compute;

import static com.vmware.admiral.request.compute.TagConstraintUtils.getTagLinkForCondition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.photon.controller.model.Constraint;

/**
 * Contains methods that are needed by placement for selecting the storage profile.
 */
public class StorageProfileUtils {

    /**
     * Get tag link for each of the condition
     */
    public static Map<Constraint.Condition, String> extractStorageTagConditions(
            Constraint constraint, List<String> tenantLinks) {
        if (constraint == null) {
            return null;
        }

        Map<Constraint.Condition, String> tagLinkByCondition = new HashMap<>();
        List<String> tLinks = QueryUtil.getTenantLinks(tenantLinks);
        for (Constraint.Condition condition : constraint.conditions) {
            String tagLink = getTagLinkForCondition(condition, tLinks);
            if (tagLink != null) {
                tagLinkByCondition.put(condition, tagLink);
            }
        }

        return tagLinkByCondition;
    }
}
