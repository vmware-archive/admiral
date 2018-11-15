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

package com.vmware.admiral.compute.container.volume;

import static java.util.Collections.disjoint;

import java.net.URI;

import static com.vmware.admiral.compute.ContainerHostService.DEFAULT_VMDK_DATASTORE_PROP_NAME;
import static com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.DEFAULT_VOLUME_DRIVER;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.VolumeOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState.PowerState;
import com.vmware.admiral.service.common.AbstractCallbackServiceHandler;
import com.vmware.admiral.service.common.AbstractCallbackServiceHandler.CallbackServiceHandlerState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * Utility class for docker volume related operations.
 */
public class VolumeUtil {

    private static final String HOST_CONTAINER_DIR_DELIMITER = ":/";

    public static final String ERROR_VOLUME_NAME_IS_REQUIRED = "Volume name is required.";
    public static final String ERROR_VOLUME_NAME_INVALID = "\"%s\" includes invalid characters for"
            + " a local volume name, only \"[a-zA-Z0-9][a-zA-Z0-9@_.-]+\" are allowed.";

    private static final Pattern RX_VOLUME_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_.-]+$");

    public static void validateLocalVolumeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new LocalizableValidationException(ERROR_VOLUME_NAME_IS_REQUIRED,
                    "compute.volumes.name.required");
        }

        Matcher matcher = RX_VOLUME_NAME.matcher(name);
        if (!matcher.matches()) {
            String errMsg = String.format(ERROR_VOLUME_NAME_INVALID, name);
            throw new LocalizableValidationException(errMsg, "compute.volumes.name.invalid");
        }
    }

    /**
     * Parses volume host directory only.
     *
     * @param volume
     *            - volume name. It might be named volume [volume-name] or path:
     *            [/host-directory:/container-directory, named-volume1:/container-directory]
     * @return host directory or named volume itself.
     */
    public static String parseVolumeHostDirectory(String volume) {

        if (StringUtils.isEmpty(volume)) {
            return volume;
        }

        if (!volume.contains(HOST_CONTAINER_DIR_DELIMITER)) {
            return volume;
        }

        String[] hostContainerDir = volume.split(HOST_CONTAINER_DIR_DELIMITER);

        if (hostContainerDir.length != 2) {
            Utils.logWarning("Cannot parse volume '%s'", volume);
            throw new LocalizableValidationException("Invalid volume directory.",
                    "compute.volumes.invalid.directory");
        }

        return hostContainerDir[0];
    }

    /**
     * Creates additional affinity rules between container descriptions which share
     * local volumes. Each container group should be deployed on a single host.
     */
    public static void applyLocalNamedVolumeConstraints(
            Collection<ComponentDescription> componentDescriptions) {

        Map<String, ContainerVolumeDescription> volumes = filterDescriptions(
                ContainerVolumeDescription.class, componentDescriptions);

        List<String> localVolumes = volumes.values().stream()
                .filter(v -> DEFAULT_VOLUME_DRIVER.equals(v.driver))
                .map(v -> v.name)
                .collect(Collectors.toList());

        if (localVolumes.isEmpty()) {
            return;
        }

        Map<String, ContainerDescription> containers = filterDescriptions(
                ContainerDescription.class, componentDescriptions);

        // sort containers by local volume: each set is a group of container names
        // that share a particular local volume
        List<Set<String>> localVolumeContainers = localVolumes.stream()
                .map(v -> filterByVolume(v, containers.values()))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (localVolumeContainers.isEmpty()) {
            return;
        }

        /** Merge sets of containers sharing local volumes
         *
         *  C1  C2  C3  C4  C5  C6
         *   \  /\  /   |    \  /
         *    L1  L2    L3    L4
         *
         *    Input: [C1, C2], [C2, C3], [C4], [C5, C6]
         *    Output: [C1, C2, C3], [C4], [C5, C6]
         */
        localVolumeContainers = mergeSets(localVolumeContainers);

        Map<String, List<ContainerVolumeDescription>> containerToVolumes =
                containers.values().stream()
                        .collect(Collectors.toMap(cd -> cd.name,
                                cd -> filterVolumes(cd, volumes.values())));

        Map<String, Integer> containerToDriverCount = containerToVolumes.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(),
                        e -> e.getValue().stream()
                                .map(vd -> vd.driver)
                                .collect(Collectors.toSet()).size()));

        for (Set<String> s: localVolumeContainers) {
            if (s.size() > 1) {
                // find the container with highest number of required drivers
                int max = s.stream()
                        .map(cn -> containerToDriverCount.get(cn))
                        .max((vc1, vc2) -> Integer.compare(vc1, vc2))
                        .get();
                Set<String> maxDrivers = s.stream()
                        .filter(cn -> containerToDriverCount.get(cn) == max)
                        .collect(Collectors.toSet());

                String maxCont = maxDrivers.iterator().next();
                s.remove(maxCont);
                s.stream().forEach(cn -> addAffinity(maxCont, containers.get(cn)));
            }
        }
    }

    public static ContainerVolumeDescription createContainerVolumeDescription(
            ContainerVolumeState state) {

        ContainerVolumeDescription volumeDescription = new ContainerVolumeDescription();

        volumeDescription.documentSelfLink = state.descriptionLink;
        volumeDescription.documentDescription = state.documentDescription;
        volumeDescription.tenantLinks = state.tenantLinks;
        volumeDescription.instanceAdapterReference = state.adapterManagementReference;
        volumeDescription.name = state.name;
        volumeDescription.customProperties = state.customProperties;

        // TODO - fill in other volume settings

        return volumeDescription;
    }

    public static String buildVolumeLink(String name) {
        return UriUtils.buildUriPath(ContainerVolumeService.FACTORY_LINK, buildVolumeId(name));
    }

    public static String buildVolumeId(String resourceName) {
        return resourceName.replaceAll(" ", "-");
    }

    public static QueryTask getVolumeByHostAndNameQueryTask(String hostLink, String volumeName) {

        QueryTask queryTask = QueryUtil.buildQuery(ContainerVolumeState.class, true);

        String parentLinksItemField = QueryTask.QuerySpecification
                .buildCollectionItemName(ContainerVolumeState.FIELD_NAME_PARENT_LINKS);
        QueryTask.Query parentsClause = new QueryTask.Query()
                .setTermPropertyName(parentLinksItemField)
                .setTermMatchValue(hostLink)
                .setTermMatchType(MatchType.TERM)
                .setOccurance(Occurance.MUST_OCCUR);

        QueryTask.Query nameClause = new QueryTask.Query()
                .setTermPropertyName(ContainerVolumeState.FIELD_NAME_NAME)
                .setCaseInsensitiveTermMatchValue(volumeName)
                .setTermMatchType(MatchType.TERM)
                .setOccurance(Occurance.MUST_OCCUR);

        QueryTask.Query stateClause = new QueryTask.Query()
                .setTermPropertyName(ContainerVolumeState.FIELD_NAME_POWER_STATE)
                .setTermMatchValue(PowerState.CONNECTED.toString())
                .setTermMatchType(MatchType.TERM)
                .setOccurance(Occurance.MUST_OCCUR);

        Query intermediate = new QueryTask.Query().setOccurance(Occurance.MUST_OCCUR);
        intermediate.addBooleanClause(parentsClause);
        intermediate.addBooleanClause(nameClause);
        intermediate.addBooleanClause(stateClause);

        queryTask.querySpec.query.addBooleanClause(intermediate);

        QueryUtil.addExpandOption(queryTask);
        QueryUtil.addBroadcastOption(queryTask);

        return queryTask;
    }

    public static void groupByVmdkDatastore(ServiceHost serviceHost, Collection<String> hostLinks,
            BiConsumer<Map<String, Set<String>>, Throwable> callback) {

        if (hostLinks.size() == 0) {
            callback.accept(Collections.emptyMap(), null);
            return;
        }

        Map<String, Set<String>> hostLinksByDatastore = new ConcurrentHashMap<>();

        final AtomicInteger counter = new AtomicInteger(hostLinks.size());
        final AtomicBoolean hasError = new AtomicBoolean(false);

        for (String hostLink : hostLinks) {
            discoverVmdkDatastoreForHost(serviceHost, hostLink, (datastore, error) -> {
                if (error != null) {
                    serviceHost.log(Level.WARNING,
                            "Failed to discover default vmdk datastore for host [%s]. Error: %s",
                            hostLink, Utils.toString(error));
                    if (hasError.compareAndSet(false, true)) {
                        callback.accept(null, error);
                    }
                } else {
                    hostLinksByDatastore
                            .computeIfAbsent(datastore, d -> ConcurrentHashMap.newKeySet())
                            .add(hostLink);

                    if (counter.decrementAndGet() == 0 && !hasError.get()) {
                        callback.accept(hostLinksByDatastore, null);
                    }
                }
            });
        }
    }

    public static List<String> extractVolumeNames(String[] volumes) {
        if (volumes == null || volumes.length == 0) {
            return Collections.emptyList();
        }

        return Arrays.stream(volumes)
                .map((v) -> extractVolumeName(v))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static boolean isContainerRequest(Map<String, String> customProperties) {
        if (customProperties != null) {
            return customProperties.containsKey("container_request_id");
        }
        return false;
    }

    private static String extractVolumeName(String volume) {
        String hostPart = volume.split(":/")[0];
        // a mount point starts with either / | ~ | . | ..
        if (!hostPart.isEmpty() &&  !hostPart.matches("^(/|~|\\.|\\.\\.).*$")) {
            return hostPart;
        }
        return null;
    }

    private static void discoverVmdkDatastoreForHost(ServiceHost serviceHost, String hostLink,
            BiConsumer<String, Throwable> consumer) {

        fetchVmdkDatastoreForHost(serviceHost, hostLink, (datastore, err) -> {
            if (err != null) {
                consumer.accept(null, err);
                return;
            }

            if (datastore != null) {
                consumer.accept(datastore, null);
                return;
            }

            performVmdkDatastoreDiscovery(serviceHost, hostLink, (discoveredDatastore, t) -> {
                if (t != null) {
                    consumer.accept(null, t);
                    return;
                }

                consumer.accept(discoveredDatastore, null);
            });
        });
    }

    private static void fetchVmdkDatastoreForHost(ServiceHost serviceHost, String hostLink,
            BiConsumer<String, Throwable> consumer) {

        Operation getHostOp = Operation.createGet(serviceHost, hostLink)
                .setReferer(serviceHost.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        consumer.accept(null, e);
                        return;
                    }

                    ComputeState host = o.getBody(ComputeState.class);

                    String datastore = null;
                    if (host.customProperties != null) {
                        datastore = host.customProperties.get(DEFAULT_VMDK_DATASTORE_PROP_NAME);
                    }

                    consumer.accept(datastore, null);
                });
        serviceHost.sendRequest(getHostOp);
    }

    private static void performVmdkDatastoreDiscovery(ServiceHost serviceHost, String hostLink,
            BiConsumer<String, Throwable> consumer) {

        // adapter result handler
        BiConsumer<CallbackServiceHandlerState, ServiceErrorResponse> actualCallback = (o, e) -> {
            if (e != null) {
                consumer.accept(null, new Exception(e.message));
                return;
            }

            fetchVmdkDatastoreForHost(serviceHost, hostLink, (datastore, err) -> {
                if (err != null) {
                    consumer.accept(null, err);
                    return;
                }

                if (datastore == null) {
                    String errMsg = String.format(
                            "Could not fetch datastore name for host [%s]"
                                    + " after performing datastore discovery.",
                            hostLink);
                    consumer.accept(null, new IllegalStateException(errMsg));
                    return;
                }

                consumer.accept(datastore, null);
            });
        };

        startAndCreateCallbackHandlerService(serviceHost, actualCallback, (taskCallback) -> {
            AdapterRequest volumeRequest = new AdapterRequest();
            volumeRequest.resourceReference = UriUtils.buildUri(serviceHost, hostLink);
            volumeRequest.serviceTaskCallback = taskCallback;
            volumeRequest.operationTypeId = VolumeOperationType.DISCOVER_VMDK_DATASTORE.id;

            Operation adapterRequest = Operation
                    .createPatch(serviceHost, ManagementUriParts.ADAPTER_DOCKER_VOLUME)
                    .setReferer(serviceHost.getUri())
                    .setBody(volumeRequest)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            consumer.accept(null, e);
                            return;
                        }
                    });
            serviceHost.sendRequest(adapterRequest);
        });
    }

    private static void addAffinity(String affinityTo, ContainerDescription cd) {
        if (cd.affinity != null) {
            boolean alreadyIn = Arrays.stream(cd.affinity).anyMatch(af -> affinityTo.equals(af));
            if (!alreadyIn) {
                int newSize = cd.affinity.length + 1;
                cd.affinity = Arrays.copyOf(cd.affinity, newSize);
                cd.affinity[newSize - 1] = affinityTo;
            }
        } else {
            cd.affinity = new String[] { affinityTo };
        }
    }

    private static Set<String> filterByVolume(String volumeName,
            Collection<ContainerDescription> descs) {

        Predicate<ContainerDescription> hasVolume = cd -> {
            if (cd.volumes != null) {
                return Arrays.stream(cd.volumes).anyMatch(v -> v.startsWith(volumeName));
            }
            return false;
        };

        return descs.stream()
                .filter(hasVolume)
                .map(cd -> cd.name)
                .collect(Collectors.toSet());
    }

    private static List<ContainerVolumeDescription> filterVolumes(ContainerDescription cd,
            Collection<ContainerVolumeDescription> volumes) {

        if (cd.volumes == null) {
            return Collections.emptyList();
        }

        Predicate<ContainerVolumeDescription> hasVolume = vd -> Arrays.stream(cd.volumes)
                .anyMatch(v -> v.startsWith(vd.name));

        return volumes.stream()
                .filter(hasVolume)
                .collect(Collectors.toList());
    }

    private static <T extends ResourceState> Map<String, T> filterDescriptions(
            Class<T> clazz, Collection<ComponentDescription> componentDescriptions) {

        return componentDescriptions.stream()
                .filter(cd -> clazz.isInstance(cd.getServiceDocument()))
                .map(cd -> clazz.cast(cd.getServiceDocument()))
                .collect(Collectors.toMap(c -> c.name, c -> c));
    }

    private static List<Set<String>> mergeSets(List<Set<String>> list) {
        if (list.size() < 2) {
            return list;
        }

        for (int i = 0; i < list.size() - 1; i++) {
            Set<String> current = list.get(i);
            Set<String> next = list.get(i + 1);
            if (!disjoint(current, next)) {
                list.remove(current);
                list.remove(next);
                current.addAll(next);
                list.add(current);
                list = mergeSets(list);
                break;
            }
        }

        return list;
    }

    private static void startAndCreateCallbackHandlerService(ServiceHost serviceHost,
            BiConsumer<CallbackServiceHandlerState, ServiceErrorResponse> actualCallback,
            Consumer<ServiceTaskCallback> caller) {
        if (actualCallback == null) {
            caller.accept(ServiceTaskCallback.createEmpty());
            return;
        }
        CallbackServiceHandlerState body = new CallbackServiceHandlerState();
        String callbackLink = ManagementUriParts.REQUEST_CALLBACK_HANDLER_TASKS
                + UUID.randomUUID().toString();
        body.documentSelfLink = callbackLink;
        URI callbackUri = UriUtils.buildUri(serviceHost, callbackLink);
        Operation startPost = Operation
                .createPost(callbackUri)
                .setBody(body)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        serviceHost.log(Level.WARNING,
                                "Failure creating callback handler. Error %s",
                                Utils.toString(e));
                        return;
                    }
                    serviceHost.log(Level.FINE,
                            "Callback task created with uri: %s, %s",
                            callbackUri, o.getUri());
                    caller.accept(ServiceTaskCallback.create(callbackUri.toString()));
                });

        VmdkDatastoreDiscoveredCallbackHandler service = new VmdkDatastoreDiscoveredCallbackHandler(
                actualCallback);
        service.setCompletionCallback(() -> serviceHost.stopService(service));
        serviceHost.startService(startPost, service);
    }

    private static class VmdkDatastoreDiscoveredCallbackHandler extends
            AbstractCallbackServiceHandler {

        private final BiConsumer<CallbackServiceHandlerState, ServiceErrorResponse> consumer;

        public VmdkDatastoreDiscoveredCallbackHandler(
                BiConsumer<CallbackServiceHandlerState, ServiceErrorResponse> consumer) {

            this.consumer = consumer;
        }

        @Override
        protected void handleFailedStagePatch(CallbackServiceHandlerState state) {
            ServiceErrorResponse err = state.taskInfo.failure;
            logWarning("Failed updating host info");
            if (err != null && err.stackTrace != null) {
                logFine("Task failure stack trace: %s", err.stackTrace);
                logWarning("Task failure error message: %s", err.message);
                consumer.accept(state, err);

                if (completionCallback != null) {
                    completionCallback.run();
                }
            }
        }

        @Override
        protected void handleFinishedStagePatch(CallbackServiceHandlerState state) {
            consumer.accept(state, null);

            if (completionCallback != null) {
                completionCallback.run();
            }
        }
    }
}
