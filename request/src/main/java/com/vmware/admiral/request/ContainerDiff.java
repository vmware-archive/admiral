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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.xenon.common.LocalizableValidationException;

/**
 * Inspect the container states and  grouped them by context_id.
 */
public class ContainerDiff {
    public static final String CONTAINER_DESCRIPTION_NOT_PROVIDED_MESSAGE = "Container " +
            "description not provided.";
    public static final String CONTAINER_DESCRIPTION_NOT_PROVIDED_CODE = "request" +
            ".container-inspector.container-description.not-provided";
    public static final String GROUPED_CONTAINERS_NOT_PROVIDED_MESSAGE = "Grouped containers not " +
            "provided.";
    public static final String GROUPED_CONTAINERS_NOT_PROVIDED_CODE = "request" +
            ".container-inspector.grouped-containers.not-provided";

    public final ContainerState currentState;
    public List<ContainerPropertyDiff<?>> diffs = new ArrayList<>();

    public static class ContainerPropertyDiff<T> {
        public final String containerDescriptionPropertyName;
        public final String containerStatePropertyName;
        public final T desired;
        public final T actual;

        public ContainerPropertyDiff(String containerDescriptionPropertyName, String
                containerStatePropertyName, T desired, T actual) {
            this.containerDescriptionPropertyName = containerDescriptionPropertyName;
            this.containerStatePropertyName = containerStatePropertyName;
            this.desired = desired;
            this.actual = actual;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ContainerPropertyDiff<?> that = (ContainerPropertyDiff<?>) o;

            return new EqualsBuilder()
                    .append(containerDescriptionPropertyName, that.containerDescriptionPropertyName)
                    .append(containerStatePropertyName, that.containerStatePropertyName)
                    .append(desired, that.desired)
                    .append(actual, that.actual)
                    .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(containerDescriptionPropertyName)
                    .append(containerStatePropertyName)
                    .append(desired)
                    .append(actual)
                    .toHashCode();
        }
    }

    public ContainerDiff(ContainerState currentState) {
        this.currentState = currentState;
    }

    public static List<ContainerDiff> inspect(ContainerDescription containerDescription,
                                              List<ContainerState> actualContainers) {
        if (containerDescription == null) {
            throw new LocalizableValidationException(CONTAINER_DESCRIPTION_NOT_PROVIDED_MESSAGE,
                    CONTAINER_DESCRIPTION_NOT_PROVIDED_CODE);
        }

        if (actualContainers == null) {
            throw new LocalizableValidationException(GROUPED_CONTAINERS_NOT_PROVIDED_MESSAGE,
                    GROUPED_CONTAINERS_NOT_PROVIDED_CODE);
        }

        List<ContainerDiff> containerDiffs = new ArrayList<>();
        actualContainers.forEach(container -> {
                    ContainerDiff containerDiff = new ContainerDiff(container);
                    if (!Arrays.deepEquals(container.env, containerDescription.env)) {
                        containerDiff.diffs.add(
                                new ContainerPropertyDiff<>(ContainerDescription.FIELD_NAME_ENV,
                                        ContainerState.FIELD_NAME_ENV, containerDescription.env,
                                        container.env));
                    }
                    if (Objects.equals(container.powerState, PowerState.ERROR)) {
                        containerDiff.diffs.add(new ContainerPropertyDiff(null,
                                ContainerState.FIELD_NAME_POWER_STATE, PowerState.RUNNING,
                                PowerState.ERROR));
                    }
                    if (!containerDiff.diffs.isEmpty()) {
                        containerDiffs.add(containerDiff);
                    }
                }
        );

        return containerDiffs;
    }
}
