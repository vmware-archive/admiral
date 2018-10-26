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

import com.vmware.xenon.common.UriUtils;

/**
 * Service paths used by the photon model.
 */
public class UriPaths {
    public static final String URI_PREFIX_PROPERTY_NAME = "uri.prefix";
    public static final String URI_PREFIX = UriUtils
            .normalizeUriPath(System.getProperty(URI_PREFIX_PROPERTY_NAME, ""));

    public static final String PROVISIONING = "/provisioning";
    public static final String RESOURCES = URI_PREFIX + "/resources";
    public static final String CONFIG = "/config";
    public static final String ADAPTER = "/adapter";
    public static final String TASKS = "/tasks";
    public static final String SCHEDULES = "/schedules";

    public static final String RESOURCES_NETWORKS = RESOURCES + "/networks";
    public static final String RESOURCES_NETWORK_INTERFACES = RESOURCES + "/network-interfaces";
    public static final String RESOURCES_NETWORK_INTERFACE_DESCRIPTIONS = RESOURCES
            + "/network-interfaces-descriptions";
    public static final String RESOURCES_SUBNETS = RESOURCES + "/sub-networks";
    public static final String RESOURCES_SECURITY_GROUPS = RESOURCES + "/security-groups";
    public static final String RESOURCES_LOAD_BALANCERS = RESOURCES + "/load-balancers";
    public static final String RESOURCES_LOAD_BALANCER_DESCRIPTIONS = RESOURCES
            + "/load-balancer-descriptions";

    public static final String RESOURCES_IMAGES = RESOURCES + "/images";

    public static final String RESOURCES_ROUTERS = RESOURCES + "/routers";

    public static final String MONITORING = "/monitoring";

    public static final String PROPERTY_PREFIX = "photon-model.";

    public enum AdapterTypePath {
        INSTANCE_ADAPTER("instanceAdapter", "instance-adapter"), NETWORK_ADAPTER("networkAdapter",
                "network-adapter"), SUBNET_ADAPTER("subnetAdapter",
                        "sub-network-adapter"), SECURITY_GROUP_ADAPTER("securityGroupAdapter",
                                "security-group-adapter"), LOAD_BALANCER_ADAPTER(
                                        "loadBalancerAdapter",
                                        "load-balancer-adapter"), STATS_ADAPTER("statsAdapter",
                                                "stats-adapter"), COST_STATS_ADAPTER(
                                                        "costStatsAdapter",
                                                        "cost-stats-adapter"), BOOT_ADAPTER(
                                                                "bootAdapter",
                                                                "boot-adapter"), POWER_ADAPTER(
                                                                        "powerAdapter",
                                                                        "power-adapter"), ENDPOINT_CONFIG_ADAPTER(
                                                                                "endpointConfigAdapter",
                                                                                "endpoint-config-adapter"), ENUMERATION_ADAPTER(
                                                                                        "enumerationAdapter",
                                                                                        "enumeration-adapter"), IMAGE_ENUMERATION_ADAPTER(
                                                                                                "imageEnumerationAdapter",
                                                                                                "image-enumeration-adapter"), ENUMERATION_CREATION_ADAPTER(
                                                                                                        "enumerationCreationAdapter",
                                                                                                        "enumeration-creation-adapter"), ENUMERATION_DELETION_ADAPTER(
                                                                                                                "enumerationDeletionAdapter",
                                                                                                                "enumeration-deletion-adapter"), COMPUTE_DESCRIPTION_CREATION_ADAPTER(
                                                                                                                        "computeDescriptionCreationAdapter",
                                                                                                                        "compute-description-creation-adapter"), COMPUTE_STATE_CREATION_ADAPTER(
                                                                                                                                "computeStateCreationAdapter",
                                                                                                                                "compute-state-creation-adapter"), STATIC_CONTENT_ADAPTER(
                                                                                                                                        "staticContent",
                                                                                                                                        "static-content");

        /**
         * endpoint type agnostic key used for transport purposes to identify concrete
         * AdapterTypePath's
         */
        public final String key;

        private final String path;

        private AdapterTypePath(String key, String path) {
            this.key = key;
            this.path = path;
        }

        public String adapterLink(String endpointType) {
            return UriUtils.buildUriPath(PROVISIONING, endpointType, this.path);
        }

    }
}
