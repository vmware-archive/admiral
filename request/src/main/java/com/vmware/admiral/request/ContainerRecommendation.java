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

import java.util.List;
import java.util.Map;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.xenon.common.LocalizableValidationException;

/**
 * The purpose of this class is to provide recommendation action based on the
 * state of the containers.
 */
public class ContainerRecommendation {
    public static final String INSPECTED_CONTAINER_STATES_NOT_PROVIDED = "Inspected container states not provided.";
    public static final String INSPECTED_CONTAINER_STATES_NOT_PROVIDED_CODE = "request.container-recommendation.inspected-container-states.not-provided";

    public enum Recommendation {
        REDEPLOY
    }

    private ContainerDescription containerDescription;
    private Map<String, List<ContainerState>> containersToBeRemoved;
    private Recommendation recommendation;

    public static ContainerRecommendation recommend(ContainerStateInspector inspectedContainerStates) {

        if (inspectedContainerStates == null) {
            throw new LocalizableValidationException(INSPECTED_CONTAINER_STATES_NOT_PROVIDED, INSPECTED_CONTAINER_STATES_NOT_PROVIDED_CODE);
        }

        ContainerDescription containerDescription = inspectedContainerStates.getContainerDescription();
        Map<String, List<ContainerState>> unhealthyContainersPerContextId = inspectedContainerStates
                .getUnhealthyContainersPerContextId();
        Recommendation recommendation = Recommendation.REDEPLOY;

        return new ContainerRecommendation(containerDescription, unhealthyContainersPerContextId, recommendation);
    }

    public ContainerRecommendation(ContainerDescription containerDescription,
            Map<String, List<ContainerState>> containersToBeRemoved,
            Recommendation recommendation) {
        this.containerDescription = containerDescription;
        this.containersToBeRemoved = containersToBeRemoved;
        this.recommendation = recommendation;
    }

    public ContainerDescription getContainerDescription() {
        return containerDescription;
    }

    public Map<String, List<ContainerState>> getContainersToBeRemoved() {
        return containersToBeRemoved;
    }

    public Recommendation getRecommendation() {
        return recommendation;
    }
}
