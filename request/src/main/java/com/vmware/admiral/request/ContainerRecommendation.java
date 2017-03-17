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

package com.vmware.admiral.request;

import java.util.Objects;
import java.util.function.Predicate;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.ContainerDiff.ContainerPropertyDiff;
import com.vmware.xenon.common.LocalizableValidationException;

/**
 * The purpose of this class is to provide recommendation action based on the
 * state of the containers.
 */
public class ContainerRecommendation {
    public static final String INSPECTED_CONTAINER_STATES_NOT_PROVIDED = "Inspected container " +
            "states not provided.";
    public static final String INSPECTED_CONTAINER_STATES_NOT_PROVIDED_CODE = "request" +
            ".container-recommendation.inspected-container-states.not-provided";

    public static Recommendation recommend(ContainerDiff diff) {
        if (diff == null) {
            throw new LocalizableValidationException(INSPECTED_CONTAINER_STATES_NOT_PROVIDED,
                    INSPECTED_CONTAINER_STATES_NOT_PROVIDED_CODE);
        }

        Predicate<ContainerPropertyDiff> envPredicate = d -> Objects.equals(ContainerDescription
                .FIELD_NAME_ENV, d.containerDescriptionPropertyName);
        Predicate<ContainerPropertyDiff> statePredicate = d -> Objects.equals(ContainerState
                .FIELD_NAME_POWER_STATE, d.containerStatePropertyName);

        if (diff.diffs.stream().anyMatch(envPredicate.or(statePredicate))) {
            return Recommendation.REDEPLOY;
        }
        return Recommendation.NONE;
    }

    public enum Recommendation {
        REDEPLOY, NONE
    }
}
