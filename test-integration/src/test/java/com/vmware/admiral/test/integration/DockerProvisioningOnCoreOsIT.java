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

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_COMMAND_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_CONFIG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_DOMAINNAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_ENTRYPOINT_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_ENV_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOSTNAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.BINDS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.CAP_ADD_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.CAP_DROP_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.CPU_SHARES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.DEVICES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.DNS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.DNS_SEARCH_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.EXTRA_HOSTS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.PRIVILEGED_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.RESTART_POLICY_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.RESTART_POLICY_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.RESTART_POLICY_RETRIES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_PORT_BINDINGS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_USER_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_VOLUMES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_WORKING_DIR_PROP_NAME;
import static com.vmware.admiral.test.integration.TestPropertiesUtil.getTestRequiredProp;
import static com.vmware.admiral.test.integration.data.IntegratonTestStateFactory.CONTAINER_DCP_TEST_LATEST_ID;
import static com.vmware.admiral.test.integration.data.IntegratonTestStateFactory.CONTAINER_DCP_TEST_LATEST_IMAGE;
import static com.vmware.admiral.test.integration.data.IntegratonTestStateFactory.CONTAINER_DCP_TEST_LATEST_NAME;
import static com.vmware.admiral.test.integration.data.IntegratonTestStateFactory.CONTAINER_IMAGE_DOWNLOAD_URL_FORMAT;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.docker.util.DockerDevice;
import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerLogService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.LogConfig;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.xenon.common.Utils;

public class DockerProvisioningOnCoreOsIT extends BaseProvisioningOnCoreOsIT {
    private static final String[] TEST_COMMAND = { "/etc/hosts", "-" };
    private static final String TEST_PORT_BINDINGS = "127.0.0.1::8282/tcp";
    private static final String TEST_ENV_PROP = "TEST_PROP WITH SPACE=testValue with space ' \" \\' attempt injection";
    private static final String[] TEST_ENV = { TEST_ENV_PROP };
    private static final String TEST_RESTART_POLICY_NAME = "on-failure";
    private static final int TEST_RESTART_POLICY_RETRIES = 3;
    private static final String TEST_USER = "root";
    // private static final int TEST_MEMORY_LIMIT = 5_000_000;
    // private static final int TEST_MEMORY_SWAP = -1;
    private static final int TEST_CPU_SHARES = 512;
    private static final String[] TEST_DNS = { "8.8.8.8", "9.9.9.9" };
    private static final String[] TEST_DNS_SEARCH = { "eng.vmware.com", "vmware.com" };
    private static final String[] TEST_ENTRY_POINT = { "/bin/cat" }; // more than one elements is
    // ignored in SSH adapter

    private static final String[] TEST_VOLUMES = { "/tmp:/mnt/tmp:ro" };
    private static final String[] TEST_CAP_ADD = { "NET_ADMIN" };
    private static final String[] TEST_CAP_DROP = { "MKNOD" };
    private static final String[] TEST_DEVICES = { "/dev/null:/dev/null2:rwm" };
    private static final String TEST_HOSTNAME = "test-hostname";
    private static final String TEST_DOMAINNAME = "eng.vmware.com";
    private static final String[] TEST_EXTRA_HOSTS = { "vmware-vra:10.148.85.240" };
    private static final String TEST_WORKING_DIR = "/tmp";
    private static final boolean TEST_PRIVILEGED = true;

    @Test
    public void testProvisionDockerContainerOnCoreOSWithImageDownloadAPI()
            throws Exception {

        doProvisionDockerContainerOnCoreOS(true, DockerAdapterType.API);
    }

    @Ignore("https://jira-hzn.eng.vmware.com/browse/VBV-653")
    @Test
    public void testProvisionDockerContainerOnCoreOSWithImageDownloadSSH()
            throws Exception {

        doProvisionDockerContainerOnCoreOS(true, DockerAdapterType.SSH);
    }

    @Test
    public void testProvisionDockerContainerOnCoreOSWithRegistryImageAPI()
            throws Exception {

        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API);
    }

    @Ignore("https://jira-hzn.eng.vmware.com/browse/VBV-653")
    @Test
    public void testProvisionDockerContainerOnCoreOSWithRegistryImageSSH()
            throws Exception {

        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.SSH);
    }

    @Test
    public void testProvisionDockerContainerOnCoreOSWithV2RegistryImageAPI()
            throws Exception {

        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API,
                RegistryType.V2_SSL_SECURE);
    }

    @Test
    public void testProvisionDockerContainerOnCoreOSWithInsecureRegistryImageAPI()
            throws Exception {

        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API,
                RegistryType.V1_HTTP_INSECURE);
    }

    @Test
    public void testProvisionDockerContainerOnCoreOSWithSecureRegistryImageAPI()
            throws Exception {

        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API,
                RegistryType.V2_BASIC_AUTH);
    }

    @Ignore("https://jira-hzn.eng.vmware.com/browse/VBV-654")
    @Test
    public void testProvisionDockerContainerOnCoreOSUsingLocalImageWithPriority()
            throws Exception {
        setupCoreOsHost(DockerAdapterType.API);

        logger.info(
                "---------- 5. Create test docker image container description using local image with priority. "
                        + "--------");
        String contDescriptionLink = getResourceDescriptionLinkUsingLocalImage(false,
                RegistryType.V1_SSL_SECURE);
        requestContainerAndDelete(contDescriptionLink);
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        ContainerDescription containerDec = createContainerDescription(downloadImage, registryType,
                false);
        return containerDec.documentSelfLink;
    }

    private String getResourceDescriptionLinkUsingLocalImage(boolean downloadImage,
            RegistryType registryType) throws Exception {
        ContainerDescription containerDec = createContainerDescription(downloadImage, registryType,
                true);
        return containerDec.documentSelfLink;
    }

    private ContainerDescription createContainerDescription(boolean downloadImage,
            RegistryType registryType, Boolean useLocalImageWithPriority) throws Exception {
        ContainerDescription containerDesc = new ContainerDescription();
        containerDesc.documentSelfLink = CONTAINER_DCP_TEST_LATEST_ID;

        if (downloadImage) {
            containerDesc.image = CONTAINER_DCP_TEST_LATEST_IMAGE;
            containerDesc.imageReference = buildDownloadURI(CONTAINER_DCP_TEST_LATEST_IMAGE);
        } else {
            containerDesc.image = getImageName(registryType);
        }

        containerDesc.customProperties = new HashMap<String, String>();
        containerDesc.customProperties.put("DOCKER_CONTAINER_CREATE_USE_LOCAL_IMAGE_WITH_PRIORITY",
                useLocalImageWithPriority.toString());

        containerDesc.name = CONTAINER_DCP_TEST_LATEST_NAME;
        containerDesc.command = TEST_COMMAND;

        containerDesc.portBindings = new PortBinding[] { PortBinding
                .fromDockerPortMapping(DockerPortMapping.fromString(TEST_PORT_BINDINGS)) };

        containerDesc.logConfig = createLogConfig();
        containerDesc.env = TEST_ENV;
        containerDesc.restartPolicy = TEST_RESTART_POLICY_NAME;
        containerDesc.maximumRetryCount = TEST_RESTART_POLICY_RETRIES;
        containerDesc.user = TEST_USER;
        // TODO: Enable once the Reservations, ResourcePool properties and host stats
        /// gathering are implemented fully
        // containerDesc.memoryLimit = TEST_MEMORY_LIMIT;
        // containerDesc.memorySwap = TEST_MEMORY_SWAP;
        containerDesc.cpuShares = TEST_CPU_SHARES;
        containerDesc.dns = TEST_DNS;
        containerDesc.dnsSearch = TEST_DNS_SEARCH;
        containerDesc.entryPoint = TEST_ENTRY_POINT;
        containerDesc.volumes = TEST_VOLUMES;
        containerDesc.capAdd = TEST_CAP_ADD;
        containerDesc.capDrop = TEST_CAP_DROP;
        containerDesc.device = TEST_DEVICES;
        containerDesc.hostname = TEST_HOSTNAME;
        containerDesc.domainName = TEST_DOMAINNAME;
        containerDesc.extraHosts = TEST_EXTRA_HOSTS;
        containerDesc.workingDir = TEST_WORKING_DIR;
        containerDesc.privileged = TEST_PRIVILEGED;
        containerDesc = postDocument(ContainerDescriptionService.FACTORY_LINK, containerDesc);

        return containerDesc;
    }

    private LogConfig createLogConfig() {
        LogConfig logConfig = new LogConfig();
        logConfig.type = "json-file";
        logConfig.config = new HashMap<>();
        logConfig.config.put("max-size", "200k");
        return logConfig;
    }

    private URI buildDownloadURI(String imageName) {
        // replace ':' with '-' in the imageName
        if (imageName.indexOf(':') >= 0) {
            imageName = imageName.replaceAll(":", "-");
        }

        String downloadImageUrl = getTestRequiredProp("docker.images.download.url");
        String result = String.format(
                CONTAINER_IMAGE_DOWNLOAD_URL_FORMAT, downloadImageUrl, imageName);

        return URI.create(result);
    }

    @Override
    protected void validateAfterStart(String resourceDescLink, RequestBrokerState request)
            throws Exception {

        super.validateAfterStart(resourceDescLink, request);

        String containerStateLink = request.resourceLinks.iterator().next();

        logger.info("---------- V1. Request Stop operation on the container. --------");
        RequestBrokerState day2StopRequest = new RequestBrokerState();
        day2StopRequest.resourceType = ResourceType.CONTAINER_TYPE.getName();
        day2StopRequest.operation = ContainerOperationType.STOP.id;
        day2StopRequest.resourceLinks = request.resourceLinks;
        day2StopRequest = postDocument(RequestBrokerFactoryService.SELF_LINK, day2StopRequest);

        waitForTaskToComplete(day2StopRequest.documentSelfLink);

        ContainerState containerState = getDocument(containerStateLink, ContainerState.class);
        assertNotNull(containerState);
        assertEquals(PowerState.STOPPED, containerState.powerState);

        logger.info("---------- V2. Request Start operation on the container. --------");
        RequestBrokerState day2StartRequest = new RequestBrokerState();
        day2StartRequest.resourceType = ResourceType.CONTAINER_TYPE.getName();
        day2StartRequest.operation = ContainerOperationType.START.id;
        day2StartRequest.resourceLinks = request.resourceLinks;
        day2StartRequest = postDocument(RequestBrokerFactoryService.SELF_LINK, day2StartRequest);

        waitForTaskToComplete(day2StartRequest.documentSelfLink);

        containerState = getDocument(containerStateLink, ContainerState.class);
        assertNotNull(containerState);
        assertEquals(PowerState.RUNNING, containerState.powerState);
        assertArrayEquals(DOCKER_CONTAINER_COMMAND_PROP_NAME, containerState.command, TEST_COMMAND);
        assertEquals(0, containerState.documentExpirationTimeMicros);
        verifyPortMappings(containerState, TEST_PORT_BINDINGS);
        verifyConfig(containerState);
        verifyHostConfig(containerState);

        logger.info("---------- V3. Request container logs for the created container. --------");
        // Fetch the logs from the log service
        String logRequestUriPath = String.format("%s?%s=%s", ContainerLogService.SELF_LINK,
                ContainerLogService.CONTAINER_ID_QUERY_PARAM, extractId(containerStateLink));

        LogService.LogServiceState logServiceState = getDocument(logRequestUriPath,
                LogService.LogServiceState.class);
        assertNotNull(logServiceState);
        assertNotNull(logServiceState.logs);
        assertTrue("The logs is empty", logServiceState.logs.length > 0);
        assertNotEquals("The logs is empty", "--".getBytes(), logServiceState);

        logger.info("---------- V4. Request container stats for the created container. --------");
        RequestBrokerState day2FetchStatsRequest = new RequestBrokerState();
        day2FetchStatsRequest.resourceType = ResourceType.CONTAINER_TYPE.getName();
        day2FetchStatsRequest.operation = ContainerOperationType.STATS.id;
        day2FetchStatsRequest.resourceLinks = request.resourceLinks;
        day2FetchStatsRequest = postDocument(RequestBrokerFactoryService.SELF_LINK,
                day2FetchStatsRequest);

        waitForTaskToComplete(day2FetchStatsRequest.documentSelfLink);
    }

    /**
     * Verify the port mappings in the created container
     *
     * @param containerState
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void verifyPortMappings(ContainerState containerState, String expectedPortBindings) {
        Map<String, Object> hostConfig = Utils.fromJson(
                containerState.attributes.get(DOCKER_CONTAINER_HOST_CONFIG_PROP_NAME),
                Map.class);

        Map<String, List<Map<String, String>>> hostConfigPorts = (Map) hostConfig
                .get(DOCKER_CONTAINER_PORT_BINDINGS_PROP_NAME);

        assertEquals("Unexpected number of port mappings", 1, hostConfigPorts.size());
        Entry<String, List<Map<String, String>>> entry = hostConfigPorts.entrySet().iterator()
                .next();

        DockerPortMapping portMapping = DockerPortMapping.fromMap(entry);
        DockerPortMapping expectedPortMapping = DockerPortMapping.fromString(expectedPortBindings);

        assertEquals("port mapping host ip", expectedPortMapping.getHostIp(),
                portMapping.getHostIp());
        assertEquals("port mapping container port", expectedPortMapping.getContainerPort(),
                portMapping.getContainerPort());
        assertNotNull("port mapping host port", portMapping.getHostPort());
    }

    private void verifyRestartPolicy(Map<String, Object> hostConfig, String restartPolicyName,
            int restartPolicyRetries) {

        Object restartPolicy = hostConfig.get(RESTART_POLICY_PROP_NAME);

        String actualRestartPolicyName = getNestedPropertyByPath(restartPolicy,
                RESTART_POLICY_NAME_PROP_NAME);

        assertEquals("Retry policy name", restartPolicyName, actualRestartPolicyName);

        Number actualRetries = getNestedPropertyByPath(restartPolicy,
                RESTART_POLICY_RETRIES_PROP_NAME);

        assertNotNull("Missing restart policy retries", actualRetries);
        assertEquals("Retry policy max retries", restartPolicyRetries,
                actualRetries.intValue());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void verifyDevices(Map<String, Object> hostConfig, String[] expectedDevices) {
        List<Map<String, String>> devices = (List) hostConfig.get(DEVICES_PROP_NAME);
        assertNotNull(DEVICES_PROP_NAME + " missing", devices);
        List<String> deviceStrings = devices.stream()
                .map(device -> DockerDevice.fromMap(device).toString())
                .collect(Collectors.toList());

        assertEquals(DEVICES_PROP_NAME, Arrays.asList(expectedDevices), deviceStrings);
    }

    @SuppressWarnings("unchecked")
    private void verifyConfig(ContainerState containerState) {
        Map<String, Object> config = Utils.fromJson(
                containerState.attributes.get(DOCKER_CONTAINER_CONFIG_PROP_NAME),
                Map.class);

        assertEquals(DOCKER_CONTAINER_USER_PROP_NAME, TEST_USER,
                config.get(DOCKER_CONTAINER_USER_PROP_NAME));
        assertArrayPropEquals(DOCKER_CONTAINER_ENTRYPOINT_PROP_NAME, TEST_ENTRY_POINT, config);

        assertEquals(DOCKER_CONTAINER_HOSTNAME_PROP_NAME, TEST_HOSTNAME,
                config.get(DOCKER_CONTAINER_HOSTNAME_PROP_NAME));
        assertEquals(DOCKER_CONTAINER_DOMAINNAME_PROP_NAME, TEST_DOMAINNAME,
                config.get(DOCKER_CONTAINER_DOMAINNAME_PROP_NAME));
        assertEquals(DOCKER_CONTAINER_WORKING_DIR_PROP_NAME, TEST_WORKING_DIR,
                config.get(DOCKER_CONTAINER_WORKING_DIR_PROP_NAME));

        Collection<String> actualEnvironment = (Collection<String>) config
                .get(DOCKER_CONTAINER_ENV_PROP_NAME);

        // verify that the actual environment contains (at least) all of the configured variables
        assertTrue("environment variables missing",
                actualEnvironment.containsAll(Arrays.asList(TEST_ENV)));

        Map<String, String> actualVolumes = (Map<String, String>) config
                .get(DOCKER_CONTAINER_VOLUMES_PROP_NAME);

        assertNotNull("volumes is null", actualVolumes);
        for (String volume : TEST_VOLUMES) {
            // the volume created is the second element in the
            String[] split = volume.split(":");
            assertTrue("Missing volume: " + volume, actualVolumes.containsKey(split[1]));
        }
    }

    @SuppressWarnings("unchecked")
    private void verifyHostConfig(ContainerState containerState) {
        Map<String, Object> hostConfig = Utils.fromJson(
                containerState.attributes.get(DOCKER_CONTAINER_HOST_CONFIG_PROP_NAME),
                Map.class);
        // TODO: Enable once the Reservations, ResourcePool properties and host stats
        /// gathering are implemented fully
        // assertIntPropEquals(MEMORY_PROP_NAME, TEST_MEMORY_LIMIT, hostConfig);
        // assertIntPropEquals(MEMORY_SWAP_PROP_NAME, TEST_MEMORY_SWAP, hostConfig);
        assertIntPropEquals(CPU_SHARES_PROP_NAME, TEST_CPU_SHARES, hostConfig);

        assertArrayPropEquals(DNS_PROP_NAME, TEST_DNS, hostConfig);
        assertArrayPropEquals(DNS_SEARCH_PROP_NAME, TEST_DNS_SEARCH, hostConfig);
        assertArrayPropEquals(EXTRA_HOSTS_PROP_NAME, TEST_EXTRA_HOSTS, hostConfig);
        assertArrayPropEquals(BINDS_PROP_NAME, TEST_VOLUMES, hostConfig);
        assertArrayPropEquals(CAP_ADD_PROP_NAME, TEST_CAP_ADD, hostConfig);
        assertArrayPropEquals(CAP_DROP_PROP_NAME, TEST_CAP_DROP, hostConfig);

        assertEquals(PRIVILEGED_PROP_NAME, TEST_PRIVILEGED, hostConfig.get(PRIVILEGED_PROP_NAME));

        verifyRestartPolicy(hostConfig, TEST_RESTART_POLICY_NAME, TEST_RESTART_POLICY_RETRIES);
        verifyDevices(hostConfig, TEST_DEVICES);
    }

    private void assertArrayPropEquals(String key, String[] expected, Map<String, Object> map) {
        assertEquals(key, Arrays.asList(expected), map.get(key));
    }

    private void assertIntPropEquals(String key, int expected, Map<String, Object> map) {
        assertEquals(key, expected, numberAsInt(map, key));
    }

    private int numberAsInt(Map<String, Object> map, String key) {
        Number number = (Number) map.get(key);
        return number.intValue();
    }

}
