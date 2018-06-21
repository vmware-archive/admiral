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

package com.vmware.photon.controller.model.resources;

import java.util.EnumSet;
import java.util.function.Function;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.Utils;

public class ResourceUtils {

    /**
     * Optional link fields in resources cannot be cleared with a regular PATCH request because the
     * automatic merge just ignores {@code null} fields from the PATCH body, for optimization
     * purposes.
     *
     * This constant can be used instead of a {@code null} value. It is applicable only for
     * {@link PropertyUsageOption.LINK} fields that are marked with
     * {@link PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL} flag in the resource state document.
     */
    public static final String NULL_LINK_VALUE = "__noLink";

    /**
     * This method handles merging of state for patch requests. It first checks to see if the patch
     * body is for updating collections. If not it invokes the mergeWithState() method. Finally,
     * users can specify a custom callback method to perform service specific merge operations.
     *
     * <p>
     * If no changes are made to the current state, a response code {@code NOT_MODIFIED} is returned
     * with no body. If changes are made, the response body contains the full updated state.
     *
     * @param op
     *            Input PATCH operation
     * @param currentState
     *            The current state of the service
     * @param description
     *            The service description
     * @param stateClass
     *            Service state class
     * @param customPatchHandler
     *            custom callback handler
     */
    public static <T extends ResourceState> void handlePatch(Operation op, T currentState,
            ServiceDocumentDescription description, Class<T> stateClass,
            Function<Operation, Boolean> customPatchHandler) {
        try {
            boolean hasStateChanged;

            // apply standard patch merging
            EnumSet<Utils.MergeResult> mergeResult = Utils.mergeWithStateAdvanced(description,
                    currentState, stateClass, op);
            hasStateChanged = mergeResult.contains(Utils.MergeResult.STATE_CHANGED);

            if (!mergeResult.contains(Utils.MergeResult.SPECIAL_MERGE)) {
                T patchBody = op.getBody(stateClass);

                // apply ResourceState-specific merging
                hasStateChanged |= ResourceUtils.mergeResourceStateWithPatch(currentState,
                        patchBody);

                // handle NULL_LINK_VALUE links
                hasStateChanged |= nullifyLinkFields(description, currentState, patchBody);
            }

            // apply custom patch handler, if any
            if (customPatchHandler != null) {
                hasStateChanged |= customPatchHandler.apply(op);
            }

            if (!hasStateChanged) {
                op.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            } else {
                op.setBody(currentState);
            }
            op.complete();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            op.fail(e);
        }
    }

    /**
     * Updates the state of the service based on the input patch.
     *
     * @param source
     *            currentState of the service
     * @param patch
     *            patch state
     * @return whether the state has changed or not
     */
    private static boolean mergeResourceStateWithPatch(ResourceState source,
            ResourceState patch) {
        boolean isChanged = false;

        // tenantLinks requires special handling so that although it is a list, duplicate items
        // are not allowed (i.e. it should behave as a set)
        if (patch.tenantLinks != null && !patch.tenantLinks.isEmpty()) {
            if (source.tenantLinks == null || source.tenantLinks.isEmpty()) {
                source.tenantLinks = patch.tenantLinks;
                isChanged = true;
            } else {
                for (String e : patch.tenantLinks) {
                    if (!source.tenantLinks.contains(e)) {
                        source.tenantLinks.add(e);
                        isChanged = true;
                    }
                }
            }
        }

        return isChanged;
    }

    /**
     * Nullifies link fields if the patch body contains NULL_LINK_VALUE links.
     */
    private static <T extends ResourceState> boolean nullifyLinkFields(
            ServiceDocumentDescription desc, T currentState, T patchBody) {
        boolean modified = false;
        for (PropertyDescription prop : desc.propertyDescriptions.values()) {
            if (prop.usageOptions != null &&
                    prop.usageOptions.contains(PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL) &&
                    prop.usageOptions.contains(PropertyUsageOption.LINK)) {
                Object patchValue = ReflectionUtils.getPropertyValue(prop, patchBody);
                if (NULL_LINK_VALUE.equals(patchValue)) {
                    Object currentValue = ReflectionUtils.getPropertyValue(prop, currentState);
                    modified |= currentValue != null;
                    ReflectionUtils.setPropertyValue(prop, currentState, null);
                }
            }
        }
        return modified;
    }
}
