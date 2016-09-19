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

package com.vmware.admiral.common;

public interface ManagementUriParts {
    // Registry/Config/Credentials:
    String CONFIG = "/config";
    String REGISTRIES = CONFIG + "/registries";
    String REGISTRY_HOSTS = CONFIG + "/registry-spec";
    String CONFIG_PROPS = CONFIG + "/props";
    String ENVIRONMENT_MAPPING = CONFIG + "/env-mapping";
    String SSL_TRUST_CERTS = CONFIG + "/trust-certs";
    String SSL_TRUST_CERTS_IMPORT = CONFIG + "/trust-certs-import";
    String USER_INITIALIZATION_SERVICE = CONFIG + "/user-init-service";

    String CERT_DISTRIBUTION_ADD_REGISTRY = CONFIG + "/cert-dist-add-reg";
    String CERT_DISTRIBUTION_ADD_HOST = CONFIG + "/certs-dist-add-host";
    String ENDPOINTS = CONFIG + "/endpoints";

    // Resources:
    String DESCRIPTION_SUFFIX = "-descriptions";
    String CLONE_SUFFIX = "-clone";
    String EXPAND_SUFFIX = "?$expand=true";

    String RESOURCES = "/resources";
    String RESOURCE_GROUP_POLICIES = RESOURCES + "/group-policies";
    String RESOURCE_NAME_PREFIXES = RESOURCES + "/name-prefixes";
    String DEPLOYMENT_POLICIES = RESOURCES + "/deployment-policies";
    String ELASTIC_PLACEMENT_ZONES = RESOURCES + "/elastic-placement-zones";
    String ELASTIC_PLACEMENT_ZONE_CONFIGURATION = ELASTIC_PLACEMENT_ZONES + "-config";

    String CONTAINERS = RESOURCES + "/containers";
    String CONTAINER_LOGS = RESOURCES + "/container-logs";
    String CONTAINER_SHELL = RESOURCES + "/container-shell";
    String CONTAINER_DESC = RESOURCES + "/container" + DESCRIPTION_SUFFIX;
    String COMPOSITE_DESC = RESOURCES + "/composite" + DESCRIPTION_SUFFIX;
    String COMPOSITE_DESC_CLONE = RESOURCES + "/composite" + DESCRIPTION_SUFFIX + CLONE_SUFFIX;
    String COMPOSITE_DESC_CONTENT = RESOURCES + "/composite-templates";
    String COMPOSITE_COMPONENT = RESOURCES + "/composite-components";
    String COMPOSITE_CONTENT_COMPOSE = RESOURCES + "/composite-content/compose";

    String CONTAINER_HOSTS = RESOURCES + "/hosts";
    String CONTAINER_HOST_DATA_COLLECTION = RESOURCES + "/hosts-data-collections";
    String HOST_CONTAINER_LIST_DATA_COLLECTION = RESOURCES + "/host-container-list-data-collection";

    String EVENT_LOG = RESOURCES + "/event-logs";
    String NOTIFICATIONS = RESOURCES + "/notifications";

    String CONTAINER_NETWORKS = RESOURCES + "/container-networks";
    String CONTAINER_NETWORK_DESC = RESOURCES + "/container-network" + DESCRIPTION_SUFFIX;

    String CONTAINER_VOLUMES = RESOURCES + "/container-volumes";
    String CONTAINER_VOLUMES_DESC = RESOURCES + "/container-volume" + DESCRIPTION_SUFFIX;

    // Request tasks:
    String REQUEST = "/request";

    String REQUESTS = "/requests";
    String REQUEST_STATUS = "/request-status";
    String REQUEST_RESOURCE_OPERATIONS = REQUEST + "/resource-operations";
    String REQUEST_REMOVAL_OPERATIONS = REQUEST + "/resource-removal-operations";
    String REQUEST_ALLOCATION_TASKS = REQUEST + "/allocation-tasks";
    String REQUEST_COMPUTE_ALLOCATION_TASKS = REQUEST + "/compute-allocation-tasks";
    String REQUEST_CONTAINER_NETWORK_ALLOCATION_TASKS = REQUEST
            + "/container-network-allocation-tasks";
    String REQUEST_CONTAINER_NETWORK_REMOVAL_TASKS = REQUEST + "/container-network-removal-tasks";
    String REQUEST_CONTAINER_VOLUME_ALLOCATION_TASKS = REQUEST
            + "/container-volume-allocation-tasks";
    String REQUEST_RESERVATION_TASKS = REQUEST + "/reservation-tasks";
    String REQUEST_RESERVATION_ALLOCATION_TASKS = REQUEST + "/reservation-allocation-tasks";
    String REQUEST_COMPUTE_RESERVATION_TASKS = REQUEST + "/compute-reservation-tasks";
    String REQUEST_RESERVATION_REMOVAL_TASKS = REQUEST + "/reservation-removal-tasks";
    String REQUEST_HOST_REMOVAL_OPERATIONS = REQUEST + "/host-removal-operations";
    String REQUEST_COMPUTE_REMOVAL_OPEARTIONS = REQUEST + "/compute-removal-operations";
    String REQUEST_COMPUTE_RESOURCE_OPERATIONS = REQUEST + "/compute-resource-operations";
    String REQUEST_COMPOSITION_TASK = REQUEST + "/composition-tasks";
    String REQUEST_COMPOSITION_REMOVAL_TASK = REQUEST + "/composition-removal-tasks";
    String REQUEST_COMPOSITION_SUB_TASK = REQUEST + "/composition-sub-tasks";
    String REQUEST_CONTAINER_CLUSTERING_TASK = REQUEST + "/clustering-task";
    String REQUEST_PROVISION_CONTAINER_HOSTS = REQUEST + "/provision-container-hosts-tasks";
    String REQUEST_PROVISION_CONTAINER_NETWORK_TASKS = REQUEST
            + "/provision-container-network-tasks";
    String REQUEST_PROVISION_CONTAINER_VOLUME_TASKS = REQUEST
            + "/provision-container-volume-tasks";
    String REQUEST_COMPUTE_PROVISION_TASKS = REQUEST + "/compute-provision-tasks";
    String REQUEST_PROVISION_PLACEMENT_TASKS = REQUEST + "/placement-tasks";
    String REQUEST_PROVISION_COMPUTE_PLACEMENT_TASKS = REQUEST + "/compute-placement-tasks";
    String REQUEST_PROVISION_NAME_PREFIXES_TASKS = REQUEST + "/resource-prefix-tasks";
    String REQUEST_PROVISION_SERVICE_LINKS_TASKS = REQUEST + "/service-link-processing-tasks";
    String REQUEST_PROVISION_EXPOSE_SERVICE_TASKS = REQUEST + "/expose-service-processing-tasks";
    String REQUEST_CALLBACK_HANDLER_TASKS = REQUEST + "/callback-handler/";

    String DELETE_SERVICE_DOCUMENTS = "/delete-tasks";

    String COUNTER_SUB_TASKS = "/counter-subtasks";

    // Continuous delivery:
    String CONTINUOUS_DELIVERY = "/continous-delivery";
    String SELF_PROVISIONING = CONTINUOUS_DELIVERY + "/self-provisioning";

    // Image operations:
    String IMAGES = "/images";
    String TEMPLATES = "/templates";
    String LOGS = "/logs";
    String POPULAR_IMAGES = "/popular-images";

    // Adapters:
    String ADAPTERS = "/adapters";
    String ADAPTER_DOCKER = ADAPTERS + "/docker-service";
    String ADAPTER_DOCKER_HOST = ADAPTERS + "/host-docker-service";
    String ADAPTER_DOCKER_OPERATIONS = ADAPTER_DOCKER + "/operations";
    String ADAPTER_REGISTRY = ADAPTERS + "/registry-service";
    String ADAPTER_DOCKER_VOLUME = ADAPTERS + "/volume-docker-service";
    String ADAPTER_DOCKER_NETWORK = ADAPTERS + "/network-docker-service";

    String ADAPTER_ETCD_KV = "/v2/keys";
    String ADAPTER_ETCD_MEMBERS = "/v2/members";
    String KV_STORE = "/kv-store";

    // UI Service:
    String UI_SERVICE = "/";
    String CONTAINER_ICONS_RESOURCE_PATH = "/container-icons";
    String CONTAINER_IDENTICONS_RESOURCE_PATH = "/container-identicons";
    String CONTAINER_IMAGE_ICONS = "/container-image-icons";
    String REVERSE_PROXY = "/rp";

    String USER_SESSION_SERVICE = "/user-session";

    String REQUEST_PARAM_VALIDATE_OPERATION_NAME = "validate";

    String REQUEST_PARAM_ENUMERATE_OPERATION_NAME = "enumerate";

    String EXEC = "/exec";
}
