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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.Utils;

/**
 * Helper class for representing different compute components as unified entity.
 */
public class ReservationComponentDescription extends ComponentDescription {

    private transient CommonReservationResourceDescription commonDescription;

    @Override
    public void updateServiceDocument(ServiceDocument serviceDocument) {
        super.updateServiceDocument(serviceDocument);

        this.commonDescription = Utils.fromJson(componentJson,
                CommonReservationResourceDescription.class);
    }

    public CommonReservationResourceDescription getCommonDescription() {
        return commonDescription;
    }

    /**
     * A placeholder for Reservation-based tasks, implementing common properties of resource description classes like
     * of {@link ContainerDescription}, {@link ComputeDescription} and {@link KubernetesDescription}.
     *
     */
    public static class CommonReservationResourceDescription extends ResourceState {
        /** Memory limit in bytes. */
        @JsonProperty("memory_limit")
        @Documentation(description = "Memory limit in bytes.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Long memoryLimit;

        /**
         * Document id of the deployment policy if any. Description with a deployment
         * policy will be deployed on hosts/policies with the same policy.
         */
        @JsonProperty("deployment_policy_id")
        @Documentation(description = "Document link to the deployment policy if any. Description with a deployment "
                + "policy will be deployed on hosts/policies with the same policy.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String deploymentPolicyId;

        /** Data-center or other identification of the group of resources */
        @JsonProperty("zone_id")
        @Documentation(description = "Data-center or other identification of the group of resources.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String zoneId;

        /** The number of nodes to be provisioned. */
        @Documentation(description = "The number of nodes to be provisioned. Default is one.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Integer _cluster;
    }
}
