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

package com.vmware.admiral.test.integration.client.enumeration;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;

import com.vmware.admiral.test.integration.client.schema.SchemaConstants;

/**
 * Defines commonly used constants related to the Container service's schema.
 */
@XmlType
@XmlEnum
public enum ContainerSchemaConstants implements SchemaConstants {
    /** The request ID */
    @XmlEnumValue("container_request_id") CONTAINER_REQUEST_ID("container_request_id"),

    /** Defines the list of name of a container. */
    @XmlEnumValue("names") CONTAINER_NAMES("names"),

    /** Defines the container status for {@link ContainerStatus}. */
    @XmlEnumValue("status") CONTAINER_STATUS("status"),

    /**
     * Defines the container status for a {@link com.vmware.vcac.container.domain.ContainerState}.
     */
    @XmlEnumValue("power_state") CONTAINER_POWER_STATE("power_state"),

    /** Defines the container host instance id. */
    @XmlEnumValue("host_internal_id") CONTAINER_HOST_INTERNAL_ID("host_internal_id"),

    /** Defines the container host external id. */
    @XmlEnumValue("host_id") CONTAINER_HOST_ID("host_id"),

    /** Defines the container host url. */
    @XmlEnumValue("host_address") CONTAINER_HOST_URL("host_address"),

    /** Defines the container host name. */
    @XmlEnumValue("hostname") CONTAINER_HOST_NAME("hostname"),

    /** Defines the container external instance id. */
    @XmlEnumValue("container_id") CONTAINER_ID("container_id"),

    /** Defines the subtenant id. */
    @XmlEnumValue("subTenantId") SUB_TENANT_ID("subTenantId"),

    /** Defines the description of the container */
    @XmlEnumValue("__description_link") CONTAINER_DESCRIPTION("__description_link"),

    /** Defines the resource pool of the container host */
    @XmlEnumValue("__resourcepool_link") CONTAINER_RESOURCEPOOL("__resourcepool_link"),

    /** Defines the address of the container */
    @XmlEnumValue("address") CONTAINER_ADDRESS("address"),

    /** Defines which adapter which serve the provision request */
    @XmlEnumValue("adapter_management_reference") CONTAINER_ADAPTER("adapter_management_reference"),

    /** Defines which host which hosts the containers */
    @XmlEnumValue("host_reference") CONTAINER_HOST_REFERENCE("host_reference"),

    /** Defines the container type id for a given container or host. */
    @XmlEnumValue("container_type_id") CONTAINER_TYPE_ID("container_type_id"),

    /**
     * Defines the serviceTypeId for a given containerType identifying the service provider
     * registered in Component Registry.
     */
    @XmlEnumValue("container_service_type_id") CONTAINER_SERVICE_TYPE_ID("container_service_type_id"),

    /**
     * Defines the name of the collection property for containers in the data collection reply *
     * schema.
     */
    @XmlEnumValue("containers") CONTAINERS("containers"),

    /**
     * Defines the name of the collection property for a single container in the data collection
     * reply schema.
     */
    @XmlEnumValue("container") CONTAINER("container"),

    /**
     * Defines the name of the collection property for container hosts in the data collection reply
     * schema.
     */
    @XmlEnumValue("container_hosts") CONTAINER_HOSTS("container_hosts"),

    // Below constants are used for Docker container description
    // TODO not sure whether we need to define a new constants file for docker

    /** Defines a name of a container. */
    @XmlEnumValue("name") CONTAINER_NAME("name"),

    /** Defines the name of the image that a current container instance is based on. */
    @XmlEnumValue("image") CONTAINER_IMAGE("image"),

    /**
     * An image reference to a docker image in .tgz format to be downloaded to the server and pushed
     * to the local host repository.
     */
    @XmlEnumValue("image_reference") CONTAINER_IMAGE_REFERENCE("image_reference"),

    /** User to use inside the container */
    @XmlEnumValue("user") USER("user"),

    /** Memory limit in bytes. */
    @XmlEnumValue("memory_limit") MEMORY_LIMIT("memory_limit"),

    /** Total memory usage (memory + swap); set -1 to disable swap. */
    @XmlEnumValue("memory_swap_limit") MEMORY_SWAP_LIMIT("memory_swap_limit"),

    /** CPU Shares for container. */
    @XmlEnumValue("cpu_shares") CPU_SHARES("cpu_shares"),

    /** Force Docker to use specific DNS servers. */
    @XmlEnumValue("dns") DNS("dns"),

    /** A list of environment variables in the form of VAR=value. */
    @XmlEnumValue("env") ENV("env"),

    /** Environment variable name part of the {@link ENV} - VAR=value */
    @XmlEnumValue("var") ENV_VAR_NAME("var"),

    /** Value, part of key-value pairs. */
    @XmlEnumValue("value") VALUE("value"),

    /** Command to run. */
    @XmlEnumValue("command") COMMAND("command"),

    /** Set the entrypoints for the container. */
    @XmlEnumValue("entry_point") ENTRY_POINT("entry_point"),

    /** Mount a volume e.g /host:/container or /host:/container:ro */
    @XmlEnumValue("volumes") VOLUMES("volumes"),

    /** Working dir for commands to run in. */
    @XmlEnumValue("working_dir") WORKING_DIR("working_dir"),

    /** Run in privileged mode. */
    @XmlEnumValue("privileged") PRIVILEGED("privileged"),

    /** Domain name of the container. */
    @XmlEnumValue("domainname") DOMAIN_NAME("domainname"),

    /** Automatically publish all exposed ports declared for the image */
    @XmlEnumValue("publish_all") PUBLISH_ALL("publish_all"),

    /**
     * Port bindings host part of the port bindings - host:hostPort:containerPort.
     *
     * Port bindings in the format (tcp|udp//)ip:hostPort:containerPort | ip::containerPort |
     * hostPort:containerPort | containerPort where range of ports can also be provided
     */
    @XmlEnumValue("binding_host") PORT_BINDING_HOST("binding_host"),

    /**
     * Port bindings protocol (tcp or udp) part of the port bindings - host:hostPort:containerPort
     *
     * Port bindings in the format (tcp|udp//)ip:hostPort:containerPort | ip::containerPort |
     * hostPort:containerPort | containerPort where range of ports can also be provided
     */
    @XmlEnumValue("protocol") PORT_BINDING_PROTOCOL("protocol"),

    /**
     * Port bindings hostPart part of the port bindings - host:hostPort:containerPort
     *
     * Port bindings in the format (tcp|udp//)ip:hostPort:containerPort | ip::containerPort |
     * hostPort:containerPort | containerPort where range of ports can also be provided
     */
    @XmlEnumValue("host_port") PORT_BINDING_HOST_PORT("host_port"),

    /**
     * Port bindings containerPort part of the port bindings - host:hostPort:containerPort
     *
     * Port bindings in the format (tcp|udp//)ip:hostPort:containerPort | ip::containerPort |
     * hostPort:containerPort | containerPort where range of ports can also be provided
     */
    @XmlEnumValue("container_port") PORT_BINDING_CONTAINER_PORT("container_port"),

    /**
     * Network component name
     */
    @XmlEnumValue("name") NETWORK_NAME("name"),

    /**
     * Aliases of the container for the network
     */
    @XmlEnumValue("aliases") NETWORK_ALIASES("aliases"),

    /**
     * IPv6 address of the container
     */
    @XmlEnumValue("ipv6_address") NETWORK_IPV6_ADDRESS("ipv6_address"),

    /**
     * IPv4 address of the container
     */
    @XmlEnumValue("ipv4_address") NETWORK_IPV4_ADDRESS("ipv4_address"),

    /** The identity of a grouping associated usually with a host for multiple containers */
    @XmlEnumValue("pod") POD("pod"),

    /** Service links specifying the connection properties to a remote container. */
    @XmlEnumValue("links") LINKS("links"),

    /** Part of SERVICE_LINKS defining the name of the service/container. */
    @XmlEnumValue("service") LINKS_SERVICE("service"),

    /** Part of SERVICE_LINKS defining the alias for the service. */
    @XmlEnumValue("alias") LINKS_ALIAS("alias"),

    /**
     * Affinity or anti-affinity conditions of containers deployed or not deployed on the same host.
     * Format: "[!]serviceName[:soft|hard].
     *
     * If not specified, the default constraint type is "hard".
     *
     * Examples: ["cont1", "!cont2", "cont3:soft", "cont4:hard", "!cont5:soft", "!cont6:hard"]
     */
    @XmlEnumValue("affinity") AFFINITY("affinity"),

    /** custom DNS search domains (Use . if you don't wish to set the search domain) */
    @XmlEnumValue("dns_search") DNS_SEARCH("dns_search"),

    /** Add a custom host-to-IP mapping (host:ip) */
    @XmlEnumValue("extra_hosts") EXTRA_HOSTS("extra_hosts"),

    /** Mount volumes from the specified container(s) of the format <container name>[:<ro|rw>] */
    @XmlEnumValue("volumes_from") VOLUMES_FROM("volumes_from"),

    /** A list of kernel capabilities to add to the container. */
    @XmlEnumValue("cap_add") CAP_ADD("cap_add"),

    /** A list of kernel capabilities to drop from the container. */
    @XmlEnumValue("cap_drop") CAP_DROP("cap_drop"),

    /**
     * Restart policy to apply when a container exits (no, on-failure[:maximum_retry_count], always)
     */
    @XmlEnumValue("restart_policy") RESTART_POLICY("restart_policy"),

    /** When restart policy is set to on-failure, the max retries */
    @XmlEnumValue("maximum_retry_count") RESTART_POLICY_MAX_RESTARTS("maximum_retry_count"),

    /** Network mode for the container (bridge / none / container:<name|id> / host) */
    @XmlEnumValue("network_mode") NETWORK_MODE("network_mode"),

    /** Port part of the service address config */
    @XmlEnumValue("port") SERVICE_ADDRESS_CONFIG_PORT("port"),

    /** Zone id defining a group like datacenter or cluster of availability */
    @XmlEnumValue("zone_id") ZONE_ID("zone_id"),

    /** A list of devices the container specified in the format hostPath:containerPath:rwm */
    @XmlEnumValue("device") DEVICE("device"),

    @XmlEnumValue("ports") PORTS("ports"),

    @XmlEnumValue("networks") NETWORKS("networks"),

    @XmlEnumValue("parent_link") PARENT_LINK("parent_link"),

    @XmlEnumValue("groupResourcePlacementLink") GROUP_RESOURCE_PLACEMENT_LINK(
            "groupResourcePlacementLink"),

    @XmlEnumValue("operation_id") OPERATION_ID("operation_id"),

    @XmlEnumValue("custom_properties") CUSTOM_PROPERTIES("custom_properties"),

    @XmlEnumValue("id") ID("id"),

    @XmlEnumValue("is_encrypted") CUSTOM_PROPERTY_ENCRYPTED("is_encrypted"),

    @XmlEnumValue("prompt_user") CUSTOM_PROPERTY_PROMPT_USER("prompt_user"),

    @XmlEnumValue("health_config") HEALTH_CONFIG("health_config"),

    @XmlEnumValue("http_method") HTTP_METHOD("http_method"),

    @XmlEnumValue("http_version") HTTP_VERSION("http_version"),

    @XmlEnumValue("url_path") URL_PATH("url_path"),

    @XmlEnumValue("timeout_millis") TIMEOUT("timeout_millis"),

    @XmlEnumValue("healthy_threshold") HEALTHY_THRESHOLD("healthy_threshold"),

    @XmlEnumValue("unhealthy_threshold") UNHEALTHY_THRESHOLD("unhealthy_threshold"),

    /** Configuration for container logging. */
    @XmlEnumValue("log_config") LOG_CONFIG("log_config"),

    /** Logging driver. */
    @XmlEnumValue("type") LOG_CONFIG_TYPE("type"),

    /** Logging options. */
    @XmlEnumValue("config") LOG_CONFIG_CONFIG("config"),

    /** Id of deployment/reservation policy. */
    @XmlEnumValue("deployment_policy_id") DEPLOYMENT_POLICY_ID("deployment_policy_id");

    public static final String RESTART_POLICY_VALUE_NONE = "no";
    public static final String RESTART_POLICY_VALUE_ON_FAILURE = "on-failure";
    public static final String RESTART_POLICY_VALUE_ALWAYS = "always";

    public static final String PORT_BINDING_PROTOCOL_VALUE_TCP = "tcp";
    public static final String PORT_BINDING_PROTOCOL_VALUE_UDP = "udp";
    public static final String PORT_BINDING_PROTOCOL_VALUE_UNIX = "unix";

    public static final String NETWORK_MODE_VALUE_NONE = "none";
    public static final String NETWORK_MODE_VALUE_BRIDGE = "bridge";
    public static final String NETWORK_MODE_VALUE_HOST = "host";

    public static final String VOLUMES_VALUE_READ_ONLY = "ro";

    public static final String HEALTH_CONFIG_HTTP_V1_1 = "HTTP_v1_1";
    public static final String HEALTH_CONFIG_HTTP_V2 = "HTTP_v2";

    public static final String HEALTH_CONFIG_PROTOCOL_VALUE_HTTP = "HTTP";
    public static final String HEALTH_CONFIG_PROTOCOL_VALUE_TCP = "TCP";
    public static final String HEALTH_CONFIG_PROTOCOL_VALUE_COMMAND = "COMMAND";

    public static final String HEALTH_CONFIG_HTTP_METHOD_GET = "GET";
    public static final String HEALTH_CONFIG_HTTP_METHOD_POST = "POST";
    public static final String HEALTH_CONFIG_HTTP_METHOD_PATCH = "PATCH";
    public static final String HEALTH_CONFIG_HTTP_METHOD_PUT = "PUT";
    public static final String HEALTH_CONFIG_HTTP_METHOD_OPTIONS = "OPTIONS";

    private final String value;

    ContainerSchemaConstants(String value) {
        this.value = value;
    }

    /**
     * @return the value
     */
    @Override
    public String value() {
        return value;
    }
}
