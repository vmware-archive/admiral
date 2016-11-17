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

package com.vmware.admiral.compute.container;

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.AssertUtil.assertTrue;
import static com.vmware.admiral.common.util.ValidationUtils.validateContainerName;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.CloneableResource;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.HttpVersion;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.RequestProtocol;
import com.vmware.admiral.compute.content.EnvDeserializer;
import com.vmware.admiral.compute.content.EnvSerializer;
import com.vmware.admiral.compute.content.ServiceLinkDeserializer;
import com.vmware.admiral.compute.content.ServiceLinkSerializer;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * Describes a container instance. The same description service instance can be re-used across many
 * container instances acting as a shared template.
 */
public class ContainerDescriptionService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.CONTAINER_DESC;
    protected static final long MAINTENANCE_INTERVAL_MICROS = Long.getLong(
            "dcp.management.container.stats.periodic.maintenance.period.micros",
            TimeUnit.SECONDS.toMicros(120));

    // minimal container memory size fields
    private static final long CONTAINER_MIN_MEMORY_BYTES = 4_194_304;
    private static final String CONTAINER_MIN_MEMORY_PROPERTY = "docker.container.min.memory";
    private static AtomicLong containerMinMemory = new AtomicLong(-1);

    @JsonFilter(YamlMapper.SERVICE_DOCUMENT_FILTER)
    @JsonIgnoreProperties({ "customProperties" })
    public static class ContainerDescription extends ResourceState implements CloneableResource {
        /** Enatai adapter way to create valid URI from docker image reference */
        public static final String DOCKER_IMAGE_REPO_SCHEMA_PREFIX = "docker://";

        public static final String FIELD_NAME_POD = "pod";
        public static final String FIELD_NAME_IMAGE = "image";
        public static final String FIELD_NAME_VOLUMES = "volumes";
        public static final String FIELD_NAME_VOLUMES_FROM = "volumesFrom";
        public static final String FIELD_NAME_VOLUME_DRIVER = "volumeDriver";
        public static final String FIELD_NAME_LINKS = "links";
        public static final String FIELD_NAME_AFFINITY = "affinity";
        public static final String FIELD_NAME_DEPLOYMENT_POLICY_ID = "deploymentPolicyId";
        public static final String FIELD_NAME_PARENT_DESCRIPTION_LINK = "parentDescriptionLink";

        /** (Required) The docker image */
        @Documentation(description = "The docker image.")
        public String image;

        /** Commands to run. */
        @Documentation(description = "Commands to run.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] command;

        /** Link to the parent container description */
        @JsonProperty("parent_description_link")
        @Documentation(description = "Link to the parent container description.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String parentDescriptionLink;
        /**
         * (Optional) An image reference to a docker image in .tgz format to be downloaded to the
         * server and pushed to the local host repository.
         */
        @Documentation(description = "An image reference to a docker image in .tgz format to be downloaded to the server and pushed to the local host repository.")
        @JsonIgnore
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public URI imageReference;

        /** Instance Adapter reference for provisioning of containers */
        @Documentation(description = "Instance Adapter reference for provisioning of containers.")
        @JsonIgnore
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public URI instanceAdapterReference;

        /** Data-center or other identification of the group of resources */
        @JsonProperty("zone_id")
        @Documentation(description = "Data-center or other identification of the group of resources.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String zoneId;

        /** The identity of a grouping associated usually with a host for multiple containers */
        @Documentation(description = "The identity of a grouping associated usually with a host for multiple containers.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String pod;

        /**
         * Affinity or anti-affinity conditions of containers deployed or not deployed on the same
         * host. Format: "[!]serviceName[:soft|hard].
         *
         * If not specified, the default constraint type is "hard".
         *
         * Examples: ["cont1", "!cont2", "cont3:soft", "cont4:hard", "!cont5:soft", "!cont6:hard"]
         */
        @Documentation(description = " Affinity or anti-affinity conditions of containers deployed "
                + "or not deployed on the same host. Format: [!]serviceName[:soft|hard]."
                + "If not specified, the default constraint type is 'hard'. "
                + "Examples: ['cont1', '!cont2', 'cont3:soft', 'cont4:hard', '!cont5:soft', '!cont6:hard']")
        @PropertyOptions(usage = PropertyUsageOption.OPTIONAL,
                indexing = PropertyIndexingOption.EXPAND)
        public String[] affinity;

        /** The number of nodes to be provisioned. */
        @Documentation(description = "The number of nodes to be provisioned. Default is one.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Integer _cluster;

        /** User to use inside the container */
        @Documentation(description = "User to use inside the container.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String user;

        /** Memory limit in bytes. */
        @JsonProperty("memory_limit")
        @Documentation(description = "Memory limit in bytes.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Long memoryLimit;

        /** Total memory usage (memory + swap); set -1 to disable swap. */
        @JsonProperty("memory_swap_limit")
        @Documentation(description = "Total memory usage (memory + swap); "
                + "set -1 to set infinite (disable swap limit), 0 to disable swap.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Long memorySwapLimit;

        /** CPU Shares for container. */
        @JsonProperty("cpu_shares")
        @Documentation(description = "CPU Shares for container.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Integer cpuShares;

        /** Force Docker to use specific DNS servers. */
        @Documentation(description = "Force Docker to use specific DNS servers.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] dns;

        /** A list of environment variables in the form of VAR=value. */
        @JsonSerialize(contentUsing = EnvSerializer.class)
        @JsonDeserialize(contentUsing = EnvDeserializer.class)
        @Documentation(description = "A list of environment variables in the form of VAR=value.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] env;

        /** Set the entrypoints for the container. */
        @JsonProperty("entry_point")
        @Documentation(description = "Set the entrypoints for the container.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] entryPoint;

        /** Mount a volume e.g /host:/container or /host:/container:ro */
        @Documentation(description = "Mount a volume e.g /host:/container or /host:/container:ro")
        @PropertyOptions(usage = PropertyUsageOption.OPTIONAL,
                indexing = PropertyIndexingOption.EXPAND)
        public String[] volumes;

        /** Working dir for commands to run in. */
        @JsonProperty("working_dir")
        @Documentation(description = "Working dir for commands to run in.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String workingDir;

        /** Run in privileged mode. */
        @Documentation(description = "Run in privileged mode")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Boolean privileged;

        /** Hostname of the container. */
        @Documentation(description = "Hostname of the container.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String hostname;

        /** Domain name of the container. */
        @JsonProperty("domain_name")
        @Documentation(description = "Domain name of the container.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String domainName;

        /** Add a custom host-to-IP mapping (host:ip) */
        @JsonProperty("extra_hosts")
        @Documentation(description = "Add a custom host-to-IP mapping (host:ip).")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] extraHosts;

        /** Automatically publish all exposed ports declared for the image */
        @JsonProperty("publish_all")
        @JsonInclude(value = Include.NON_EMPTY)
        @Documentation(description = "Automatically bind all exposed ports declared for the image.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Boolean publishAll;

        /**
         * Port bindings in the format ip:hostPort:containerPort | ip::containerPort |
         * hostPort:containerPort | containerPort where range of ports can also be provided
         */
        @JsonProperty("ports")
        @Documentation(description = "Port bindings in the format: "
                + "ip:hostPort:containerPort | ip::containerPort | hostPort:containerPort | containerPort"
                + " where range of ports can also be provided.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public PortBinding[] portBindings;

        @JsonProperty("log_config")
        @Documentation(description = "Log configuration of the container")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public LogConfig logConfig;

        @JsonProperty("health_config")
        @Documentation(description = "Health service for this container.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public HealthConfig healthConfig;

        /** A list of services (in a blueprint) the container depends on */
        @JsonProperty("depends_on")
        @Documentation(description = "A list of services (in a blueprint) the container depends on.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] dependsOn;

        /** A list of services (in a blueprint) the container depends on */
        @JsonProperty("links")
        @JsonSerialize(contentUsing = ServiceLinkSerializer.class)
        @JsonDeserialize(contentUsing = ServiceLinkDeserializer.class)
        @Documentation(description = "A list of services (in a blueprint) the container depends on.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.LINKS)
        public String[] links;

        /** Custom DNS search domains (Use . if you don't wish to set the search domain) */
        @JsonProperty("dns_search")
        @Documentation(description = "Custom DNS search domains (Use . if you don't wish to set the search domain).")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] dnsSearch;

        /**
         * Mount volumes from the specified container(s) of the format <container name>[:<ro|rw>]
         */
        @JsonProperty("volumes_from")
        @Documentation(description = "Mount volumes from the specified container(s) of the format <container name>[:<ro|rw>]")
        @PropertyOptions(usage = PropertyUsageOption.OPTIONAL,
                indexing = PropertyIndexingOption.EXPAND)
        public String[] volumesFrom;

        /** Specify volume driver name.*/
        @JsonProperty("volume_driver")
        @Documentation(description = "Specify volume driver name (default \"local\")")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String volumeDriver;

        /** A list of kernel capabilities to add to the container. */
        @JsonProperty("cap_add")
        @Documentation(description = "A list of kernel capabilities to add to the container.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] capAdd;

        /** A list of kernel capabilities to drop from the container. */
        @JsonProperty("cap_drop")
        @Documentation(description = "A list of kernel capabilities to add to the container.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] capDrop;

        /** Restart policy to apply when a container exits (no, on-failure[:max-retry], always). */
        @JsonProperty("restart_policy")
        @Documentation(description = "Restart policy to apply when a container exits (no, on-failure[:max-retry], always).", exampleString = "always")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String restartPolicy;

        /** When restart policy is set to on-failure, the max retries */
        @JsonProperty("maximum_retry_count")
        @Documentation(description = "When restart policy is set to on-failure, the max retries.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Integer maximumRetryCount;

        /** Network mode for the container (bridge / none / container:<name|id> / host) */
        @JsonProperty("network_mode")
        @Documentation(description = "Network mode for the container (bridge / none / container:<name|id> / host).", exampleString = "bridge")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String networkMode;

        /** Networks to join, referencing declared or already existing networks */
        @Documentation(description = "Networks to join, referencing declared or already existing networks.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, ServiceNetwork> networks;

        /** PID namespace for the container ( "" / host ) */
        @JsonProperty("pid_mode")
        @Documentation(description = "PID namespace for the container ( '' / host )")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String pidMode;

        /**
         * A list of devices to add to the container specified in the format
         * hostPath:containerPath:rwm
         */
        @Documentation(description = "A list of devices to add to the container specified in the format hostPath:containerPath:rwm")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String[] device;

        /**
         * Document id of the deployment policy if any. Container description with a deployment
         * policy will be deployed on hosts/policies with the same policy.
         */
        @JsonProperty("deployment_policy_id")
        @Documentation(description = "Document link to the deployment policy if any. Container description with a deployment "
                + "policy will be deployed on hosts/policies with the same policy.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String deploymentPolicyId;

        @JsonAnySetter
        private void putProperty(String key, String value) {
            if (customProperties == null) {
                customProperties = new HashMap<>();
            }
            customProperties.put(key, value);
        }

        @JsonAnyGetter
        private Map<String, String> getProperties() {
            return customProperties;
        }

        /**
         * Compute DockerHost URI based on {@link ComputeState} properties.
         */
        public static URI getDockerHostUri(ComputeState hostComputeState) {
            String dockerHostScheme = null;
            String dockerHostAddress = null;
            String dockerHostPortStr = null;
            String dockerHostPath = null;
            int dockerHostPort = -1;

            if (hostComputeState.customProperties != null) {
                dockerHostScheme = hostComputeState.customProperties
                        .get(ContainerHostService.DOCKER_HOST_SCHEME_PROP_NAME);
                dockerHostPortStr = hostComputeState.customProperties
                        .get(ContainerHostService.DOCKER_HOST_PORT_PROP_NAME);

                if (dockerHostPortStr != null) {
                    dockerHostPort = Integer.parseInt(dockerHostPortStr);
                }
                dockerHostPath = hostComputeState.customProperties
                        .get(ContainerHostService.DOCKER_HOST_PATH_PROP_NAME);

                dockerHostAddress = hostComputeState.customProperties
                        .get(ContainerHostService.DOCKER_HOST_ADDRESS_PROP_NAME);
            }

            if (dockerHostAddress == null) {
                dockerHostAddress = hostComputeState.address;
            }

            AssertUtil.assertNotNull(dockerHostAddress, "address");

            URI uri = UriUtilsExtended.buildDockerUri(dockerHostScheme,
                    dockerHostAddress, dockerHostPort, dockerHostPath);
            hostComputeState.customProperties.put(
                    ContainerHostService.DOCKER_HOST_SCHEME_PROP_NAME, uri.getScheme());
            hostComputeState.customProperties.put(
                    ContainerHostService.DOCKER_HOST_PORT_PROP_NAME, String.valueOf(uri.getPort()));
            return uri;
        }

        @Override
        public Operation createCloneOperation(Service sender) {
            this.parentDescriptionLink = this.documentSelfLink;
            this.documentSelfLink = null;
            return Operation.createPost(sender, FACTORY_LINK)
                    .setBody(this);
        }
    }

    @JsonFilter(YamlMapper.SERVICE_DOCUMENT_FILTER)
    public static class CompositeTemplateContainerDescription extends ContainerDescription {

        @JsonProperty("networks")
        public List<ServiceNetwork> networksList;
    }

    public ContainerDescriptionService() {
        super(ContainerDescription.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.setMaintenanceIntervalMicros(MAINTENANCE_INTERVAL_MICROS);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation startPost) {
        if (!checkForBody(startPost)) {
            return;
        }
        try {
            ContainerDescription state = startPost.getBody(ContainerDescription.class);
            logFine("Initial name is %s", state.name);
            validateState(state);
            startPost.complete();
        } catch (Throwable e) {
            logSevere(e);
            startPost.fail(e);
        }
    }

    private void validateState(ContainerDescription state) {
        assertNotNull(state.image, "image");
        validateContainerName(state.name);

        if (state.instanceAdapterReference == null) {
            state.instanceAdapterReference = UriUtils.buildUri(
                    ManagementUriParts.ADAPTER_DOCKER);
        }
        if (state.memoryLimit != null) {
            long mem = getContainerMinMemoryLimit();
            assertTrue(state.memoryLimit == 0 || state.memoryLimit >= mem,
                    "Memory limit must be at least " + mem + " bytes (or 0 if no limit).");
        }
        if (state.memorySwapLimit != null) {
            assertTrue(state.memorySwapLimit >= -1,
                    "Memory swap limit must be greater or equal to -1.");
        }
        if (state.maximumRetryCount != null) {
            assertTrue(state.maximumRetryCount >= 0,
                    "Max retry count must be a non-negative integer.");
        }
        if (state.networkMode != null) {
            assertTrue(state.networkMode.matches("bridge|host|none|container:\\w+"),
                    "Network mode must be one of none, bridge, host, container:<name|id>.");
        }
        if (state.restartPolicy != null) {
            assertTrue(state.restartPolicy.matches("no|always|on-failure"),
                    "Restart policy must be one of no, on-failure, always.");
        }
        // Since there's no way to set the dependsOn field from the UI, we can ensure at least that
        // the field includes exactly the services that the container has links to. This should fix
        // dependency issues when deploying Composite Templates with containers and links between
        // them.
        if (state.links != null && state.links.length > 0) {
            Set<String> dependencies = new LinkedHashSet<>();
            for (String link : state.links) {
                if (link == null || link.trim().isEmpty()) {
                    continue;
                } else if (link.contains(":")) {
                    dependencies.add(link.split(":")[0]);
                } else {
                    dependencies.add(link);
                }
            }
            if (!dependencies.isEmpty()) {
                state.dependsOn = dependencies.toArray(new String[] {});
            }
        }

        if (state.volumes != null) {
            for (String volume : state.volumes) {
                assertNotNull(volume, "volume");
                String[] split = volume.split(":");
                assertTrue(split.length >= 2, "Volume must be host_path:container_path[:ro]");
            }
        }

        if (state.healthConfig != null && state.healthConfig.protocol == null) {
            state.healthConfig = null;
        }

        if (state.hostname != null) {
            state.hostname = state.hostname.trim();
        }

        if (state.workingDir != null) {
            state.workingDir = state.workingDir.trim();
        }
    }

    @Override
    public void handleMaintenance(Operation post) {
        if (getProcessingStage() != ProcessingStage.AVAILABLE) {
            logFine("Skipping maintenance since service is not available: %s ", getUri());
            post.complete();
            return;
        }

        if (DeploymentProfileConfig.getInstance().isTest()) {
            logFine("Skipping scheduled maintenance in test mode: %s", getUri());
            post.complete();
            return;
        }

        logFine("Performing maintenance for: %s", getUri());

        new HealthChecker(getHost()).doHealthCheck(UriUtils.buildUri(getHost(), getSelfLink()));

        post.complete();
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        ContainerDescription putBody = put.getBody(ContainerDescription.class);

        try {
            validateState(putBody);
            this.setState(put, putBody);
            put.setBody(putBody).complete();
        } catch (Throwable e) {
            put.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        ContainerDescription currentState = getState(patch);
        ContainerDescription patchBody = patch.getBody(ContainerDescription.class);

        ServiceDocumentDescription docDesc = getDocumentTemplate().documentDescription;
        String currentSignature = Utils.computeSignature(currentState, docDesc);

        PropertyUtils.mergeServiceDocuments(currentState, patchBody);

        if (patchBody.networkMode != null && patchBody.networkMode.trim().isEmpty()) {
            currentState.networkMode = null;
        }
        if (patchBody.networks != null) {
            currentState.networks = patchBody.networks;
        }

        validateState(currentState);

        String newSignature = Utils.computeSignature(currentState, docDesc);

        // if the signature hasn't change we shouldn't modify the state
        if (currentSignature.equals(newSignature)) {
            currentState = null;
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        }

        patch.setBody(currentState).complete();
    }

    @Override
    public void handleDelete(Operation delete) {

        if (delete.getBodyRaw() == null) {
            super.handleDelete(delete);
            return;
        }

        QueryTask compositeQueryTask = QueryUtil.buildQuery(CompositeDescription.class, true);

        String descriptionLinksItemField = QueryTask.QuerySpecification.buildCollectionItemName(
                CompositeDescription.FIELD_NAME_DESCRIPTION_LINKS);

        QueryUtil.addExpandOption(compositeQueryTask);
        QueryUtil.addListValueClause(compositeQueryTask,
                descriptionLinksItemField, Arrays.asList(getSelfLink()));

        List<String> compositeDescriptions = new ArrayList<String>();
        new ServiceDocumentQuery<CompositeDescription>(getHost(), CompositeDescription.class)
                .query(compositeQueryTask, (r) -> {
                    if (r.hasException()) {
                        logSevere(
                                "Failed to retrieve composite-descriptions: %s - %s",
                                r.getDocumentSelfLink(), r.getException());
                    } else if (r.hasResult()) {
                        compositeDescriptions.addAll(r.getResult().descriptionLinks);
                    } else {
                        deleteContainerDescriptionLink(getState(delete), compositeDescriptions);
                    }

                });

        super.handleDelete(delete);
    }

    private void deleteContainerDescriptionLink(ContainerDescription currentState,
            List<String> compositeDescriptions) {
        if (compositeDescriptions == null || compositeDescriptions.isEmpty()) {
            return;
        }

        QueryTask compositeQueryTask = QueryUtil.buildQuery(ContainerDescription.class, true);

        String linksItemField = QueryTask.QuerySpecification.buildCollectionItemName(
                ContainerDescription.FIELD_NAME_LINKS);

        QueryUtil.addExpandOption(compositeQueryTask);
        QueryUtil.addListValueClause(compositeQueryTask,
                ContainerDescription.FIELD_NAME_SELF_LINK, compositeDescriptions);
        QueryUtil.addListValueClause(compositeQueryTask,
                linksItemField, Arrays.asList(String.format("*%s*", currentState.name)),
                MatchType.WILDCARD);

        new ServiceDocumentQuery<ContainerDescription>(getHost(), ContainerDescription.class)
                .query(compositeQueryTask,
                        (r) -> {
                            if (r.hasException()) {
                                logSevere(
                                        "Failed to retrieve composite-descriptions: %s - %s",
                                        r.getDocumentSelfLink(), r.getException());
                            } else if (r.hasResult()) {
                                ContainerDescription containerDesc = r.getResult();
                                List<String> newLinksList = new ArrayList<String>();

                                for (int i = 0; i < containerDesc.links.length; i++) {
                                    if (!containerDesc.links[i].split(":")[0]
                                            .equals(currentState.name)) {
                                        newLinksList.add(containerDesc.links[i]);
                                    }
                                }

                                ContainerDescription patch = new ContainerDescription();
                                patch.links = newLinksList
                                        .toArray(new String[newLinksList
                                                .size()]);
                                sendRequest(Operation
                                        .createPatch(this,
                                                containerDesc.documentSelfLink)
                                        .setBody(patch)
                                        .setCompletion(
                                                (o, ex) -> {
                                                    if (ex != null) {
                                                        logSevere(
                                                                "Failed to delete container-description link for %s - %s",
                                                                containerDesc.documentSelfLink,
                                                                ex);
                                                    }
                                                }));
                            }
                        });
    }

    public static Long getContainerMinMemoryLimit() {
        if (containerMinMemory.get() == -1) {
            try {
                String value = ConfigurationUtil.getProperty(CONTAINER_MIN_MEMORY_PROPERTY);
                long l = Long.parseLong(value);
                containerMinMemory.compareAndSet(-1, l);
                return containerMinMemory.get();
            } catch (Exception e) {
                containerMinMemory.set(CONTAINER_MIN_MEMORY_BYTES);
            }
        }
        return containerMinMemory.get();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ContainerDescription template = (ContainerDescription) super.getDocumentTemplate();

        // FIXME this causes issues when the text contains non alpha characters
        // ServiceDocumentTemplateUtil.indexTextProperty(template,
        // ContainerDescription.FIELD_NAME_NAME);

        template.name = "SampleContainer";
        template.imageReference = URI.create("docker://esxhost-01:443/registry");
        template.memoryLimit = 4L * 1024 * 1024;
        template.memorySwapLimit = 0L;
        template.cpuShares = 5;
        template.portBindings = Arrays.stream(new String[] {
                "5000:5000",
                "127.0.0.1::20080",
                "127.0.0.1:20080:80",
                "1234:1234/tcp" })
                .map((s) -> PortBinding.fromDockerPortMapping(DockerPortMapping.fromString(s)))
                .collect(Collectors.toList())
                .toArray(new PortBinding[0]);
        template.publishAll = true;
        LogConfig logConfig = new LogConfig();
        logConfig.type = "json-file";
        logConfig.config = Collections.emptyMap();
        template.logConfig = logConfig;
        template.dns = new String[] { "dns entries (string)" };
        template.env = new String[] {
                "ENV_VAR=value (string)",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/local/go/bin"
                        +
                        ":/go/bin" };
        template.command = new String[] { "/usr/lib/postgresql/9.3/bin/postgres" };
        template.entryPoint = new String[] { "./dockerui" };
        template.volumes = new String[] { "/var/run/docker.sock:/var/run/docker.sock" };
        template.volumesFrom = new String[] { "parent", "other:ro" };
        template.dependsOn = new String[] { "parent" };
        template.workingDir = "/container";
        template.links = new String[] { "service:alias" };
        template.capAdd = new String[] { "NET_ADMIN" };
        template.capDrop = new String[] { "MKNOD" };
        template.device = new String[] { "/dev/sdc:/dev/xvdc:rwm" };

        template.dnsSearch = new String[] { "dns search entries (string)" };
        template.extraHosts = new String[] { "hostname:ip" };
        template.affinity = new String[] { "container", "container:soft", "!container:hard" };
        template.customProperties = new HashMap<String, String>(1);
        template.customProperties.put("propKey string", "customPropertyValue string");

        template.healthConfig = new HealthConfig();
        template.healthConfig.port = 80;
        template.healthConfig.httpMethod = Action.GET;
        template.healthConfig.protocol = RequestProtocol.HTTP;
        template.healthConfig.httpVersion = HttpVersion.HTTP_v1_1;
        template.healthConfig.urlPath = "/ping";
        template.healthConfig.timeoutMillis = 2000;
        template.healthConfig.healthyThreshold = 2;
        template.healthConfig.unhealthyThreshold = 2;

        template.networks = new LinkedHashMap<>();
        template.networkMode = "";

        return template;
    }
}
