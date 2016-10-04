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

package com.vmware.admiral.adapter.docker.service;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;

/**
 * Interface to be implemented by docker command executors
 */

public interface DockerAdapterCommandExecutor {
    String DOCKER_CONTAINER_ID_PROP_NAME = "Id";
    String DOCKER_CONTAINER_NAME_PROP_NAME = "Name";
    String DOCKER_CONTAINER_NAMES_PROP_NAME = "Names";
    String DOCKER_CONTAINER_COMMAND_PROP_NAME = "Cmd";
    String DOCKER_CONTAINER_IMAGE_PROP_NAME = "Image";
    String DOCKER_CONTAINER_TTY_PROP_NAME = "Tty";
    String DOCKER_CONTAINER_OPEN_STDIN_PROP_NAME = "OpenStdin";
    String DOCKER_CONTAINER_CREATED_PROP_NAME = "Created";
    String DOCKER_CONTAINER_EXPOSED_PORTS_PROP_NAME = "ExposedPorts";
    String DOCKER_CONTAINER_PORT_BINDINGS_PROP_NAME = "PortBindings";
    String DOCKER_CONTAINER_ENV_PROP_NAME = "Env";
    String DOCKER_CONTAINER_USER_PROP_NAME = "User";
    String DOCKER_CONTAINER_ENTRYPOINT_PROP_NAME = "Entrypoint";
    String DOCKER_CONTAINER_VOLUMES_PROP_NAME = "Volumes";
    String DOCKER_CONTAINER_HOSTNAME_PROP_NAME = "Hostname";
    String DOCKER_CONTAINER_DOMAINNAME_PROP_NAME = "Domainname";
    String DOCKER_CONTAINER_WORKING_DIR_PROP_NAME = "WorkingDir";

    String DOCKER_CONTAINER_STATE_PROP_NAME = "State";
    String DOCKER_CONTAINER_STATE_RUNNING_PROP_NAME = "Running";
    String DOCKER_CONTAINER_STATE_STARTED_PROP_NAME = "StartedAt";
    String DOCKER_CONTAINER_STATE_PID_PROP_NAME = "Pid";
    String DOCKER_CONTAINER_CONFIG_PROP_NAME = "Config";

    String DOCKER_CONTAINER_NETWORK_ID_PROP_NAME = "Id";
    String DOCKER_CONTAINER_NETWORK_NAME_PROP_NAME = "Name";
    String DOCKER_CONTAINER_NETWORK_DRIVER_PROP_NAME = "Driver";
    String DOCKER_CONTAINER_NETWORK_OPTIONS_PROP_NAME = "Options";
    String DOCKER_CONTAINER_NETWORK_IPAM_PROP_NAME = "IPAM";
    String DOCKER_CONTAINER_NETWORK_IPAM_DRIVER_PROP_NAME = "Driver";
    String DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_PROP_NAME = "Config";
    String DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_SUBNET_PROP_NAME = "Subnet";
    String DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_GATEWAY_PROP_NAME = "Gateway";
    String DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_IP_RANGE_PROP_NAME = "IPRange";
    String DOCKER_CONTAINER_NETWORK_IPAM_CONFIG_AUX_ADDRESSES_PROP_NAME = "AuxiliaryAddresses";
    String DOCKER_CONTAINER_NETWORK_CONTAINERS_PROP_NAME = "Containers";
    String DOCKER_CONTAINER_NETWORK_SETTINGS_PROP_NAME = "NetworkSettings";
    String DOCKER_CONTAINER_NETWORK_SETTINGS_PORTS_PROP_NAME = "Ports";
    String DOCKER_CONTAINER_NETWORK_SETTINGS_IP_ADDRESS_PROP_NAME = "IPAddress";
    String DOCKER_CONTAINER_NETWORKING_CONFIG_PROP_NAME = "NetworkingConfig";
    String DOCKER_CONTAINER_NETWORK_CHECK_DUPLICATE_PROP_NAME = "CheckDuplicate";

    String DOCKER_CONTAINER_HOST_CONFIG_PROP_NAME = "HostConfig";

    String DOCKER_CONTAINER_LOG_CONFIG_PROP_NAME = "LogConfig";
    String DOCKER_CONTAINER_LOG_CONFIG_PROP_TYPE_NAME = "Type";
    String DOCKER_CONTAINER_LOG_CONFIG_PROP_CONFIG_NAME = "Config";

    String DOCKER_CONTAINER_STOP_TIME = "Time";
    String DOCKER_CONTAINER_NO_STREAM = "NoStream";

    // Flag that forces container creation from a local image and only if the image is not available
    // download it
    // from a registry.
    String DOCKER_CONTAINER_CREATE_USE_LOCAL_IMAGE_WITH_PRIORITY = "UseLocalImageWithPriority";

    String DOCKER_EXEC_ATTACH_STDIN_PROP_NAME = "AttachStdin";
    String DOCKER_EXEC_ATTACH_STDOUT_PROP_NAME = "AttachStdout";
    String DOCKER_EXEC_ATTACH_STDERR_PROP_NAME = "AttachStderr";
    String DOCKER_EXEC_TTY_PROP_NAME = "Tty";
    String DOCKER_EXEC_COMMAND_PROP_NAME = "Cmd";
    String DOCKER_EXEC_DETACH_PROP_NAME = "Detach";
    String DOCKER_EXEC_ID_PROP_NAME = "Id";

    // CHECKSTYLE:OFF
    interface DOCKER_CONTAINER_HOST_CONFIG {
        String RESTART_POLICY_PROP_NAME = "RestartPolicy";
        String RESTART_POLICY_NAME_PROP_NAME = "Name";
        String RESTART_POLICY_RETRIES_PROP_NAME = "MaximumRetryCount";
        String MEMORY_PROP_NAME = "Memory";
        String MEMORY_SWAP_PROP_NAME = "MemorySwap";
        String CPU_SHARES_PROP_NAME = "CpuShares";
        String DNS_PROP_NAME = "Dns";
        String DNS_SEARCH_PROP_NAME = "DnsSearch";
        String EXTRA_HOSTS_PROP_NAME = "ExtraHosts";
        String BINDS_PROP_NAME = "Binds";
        String VOLUMES_FROM_PROP_NAME = "VolumesFrom";
        String VOLUME_DRIVER = "VolumeDriver";
        String CAP_ADD_PROP_NAME = "CapAdd";
        String CAP_DROP_PROP_NAME = "CapDrop";
        String NETWORK_MODE_PROP_NAME = "NetworkMode";
        String DEVICES_PROP_NAME = "Devices";
        String PRIVILEGED_PROP_NAME = "Privileged";
        String LINKS_PROP_NAME = "Links";
        String PID_MODE_PROP_NAME = "PidMode";
        String PUBLISH_ALL = "PublishAllPorts";

        interface DEVICE {
            String PATH_ON_HOST_PROP_NAME = "PathOnHost";
            String PATH_IN_CONTAINER_PROP_NAME = "PathInContainer";
            String CGROUP_PERMISSIONS_PROP_NAME = "CgroupPermissions";
        }
    }

    interface DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG {

        String CONTAINER_PROP_NAME = "Container";

        String ENDPOINT_CONFIG_PROP_NAME = "EndpointConfig";

        interface ENDPOINT_CONFIG {
            String IPAM_CONFIG_PROP_NAME = "IPAMConfig";

            interface IPAM_CONFIG {
                String IPV4_CONFIG = "IPv4Address";
                String IPV6_CONFIG = "IPv6Address";
                String LINK_LOCAL_IPS = "LinkLocalIPs";
            }

            String LINKS = "Links";
            String ALIASES = "Aliases";
        }

    }

    // CHECKSTYLE:ON

    String DOCKER_IMAGE_REFERENCE_PROP_NAME = "imageReference";
    String DOCKER_IMAGE_FROM_PROP_NAME = "fromImage";
    String DOCKER_IMAGE_SRC_PROP_NAME = "fromSrc";
    String DOCKER_IMAGE_REPOSITORY_PROP_NAME = "repo";
    String DOCKER_IMAGE_TAG_PROP_NAME = "tag";
    String DOCKER_IMAGE_DATA_PROP_NAME = "imageData";
    String DOCKER_IMAGE_REGISTRY_AUTH = "X-Registry-Auth";

    String DOCKER_VOLUME_NAME_PROP_NAME = "Name";
    String DOCKER_VOLUME_DRIVER_PROP_NAME = "Driver";

    // Fetch logs query param
    String STD_ERR = "stderr";
    String STD_OUT = "stdout";
    String TAIL = "tail";
    String TIMESTAMPS = "timestamps";
    String SINCE = "since";
    int DEFAULT_VALUE_TAIL = 1000;

    // Management operations:
    void stop();

    // Container operations:
    void loadImage(CommandInput input, CompletionHandler completionHandler);

    void createImage(CommandInput input, CompletionHandler completionHandler);

    void createContainer(CommandInput input, CompletionHandler completionHandler);

    void startContainer(CommandInput input, CompletionHandler completionHandler);

    void stopContainer(CommandInput input, CompletionHandler completionHandler);

    void inspectContainer(CommandInput input, CompletionHandler completionHandler);

    void execContainer(CommandInput input, CompletionHandler completionHandler);

    void fetchContainerStats(CommandInput input, CompletionHandler completionHandler);

    void removeContainer(CommandInput input, CompletionHandler completionHandler);

    void fetchContainerLog(CommandInput input, CompletionHandler completionHandler);

    // Host operations:
    void hostPing(CommandInput input, CompletionHandler completionHandler);

    void hostInfo(CommandInput input, CompletionHandler completionHandler);

    void hostVersion(CommandInput input, CompletionHandler completionHandler);

    void listContainers(CommandInput input, CompletionHandler completionHandler);

    // Network operations:
    void createNetwork(CommandInput input, CompletionHandler completionHandler);

    void listNetworks(CommandInput input, CompletionHandler completionHandler);

    void inspectNetwork(CommandInput input, CompletionHandler completionHandler);

    void removeNetwork(CommandInput input, CompletionHandler completionHandler);

    void connectContainerToNetwork(CommandInput input, CompletionHandler completionHandler);

    // TODO uncomment and implement
    // void connectNetwork(CommandInput input, CompletionHandler completionHandler);
    //
    // void disconnectNetwork(CommandInput input, CompletionHandler completionHandler);

    // Volume operations:
    void createVolume(CommandInput input, CompletionHandler completionHandler);

    void listVolumes(CommandInput input, CompletionHandler completionHandler);

    void inspectVolume(CommandInput input, CompletionHandler completionHandler);

    void removeVolume(CommandInput input, CompletionHandler completionHandler);

    void handleMaintenance(Operation post);
}