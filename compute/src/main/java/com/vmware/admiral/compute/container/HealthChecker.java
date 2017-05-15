/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.container;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.HealthChecker.HealthConfig.HttpVersion;
import com.vmware.admiral.compute.container.ShellContainerExecutorService.ShellContainerExecutorState;
import com.vmware.admiral.compute.container.maintenance.ContainerStats;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument.Documentation;
import com.vmware.xenon.common.ServiceDocument.UsageOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Describes a container instance. The same description service instance can be re-used across many
 * container instances acting as a shared template.
 */

public class HealthChecker {
    public static final String SERVICE_REFERRER_PATH = "/health-checker";

    private static final int DEFAULT_PORT = 80;
    private static final int DEFAULT_TIMEOUT = 2000;
    private static volatile HealthChecker instance;

    public static class HealthConfig {

        public static final String FIELD_NAME_AUTOREDEPLOY = "autoredeploy";

        public enum RequestProtocol {
            HTTP, TCP, COMMAND
        }

        public enum HttpVersion {
            HTTP_v1_1, HTTP_v2
        }

        public RequestProtocol protocol;

        public Integer port;

        @JsonProperty("url_path")
        public String urlPath;

        @JsonProperty("http_version")
        public HttpVersion httpVersion;

        @JsonProperty("http_method")
        public Action httpMethod;

        @JsonProperty("timeout_millis")
        public Integer timeoutMillis;

        @JsonProperty("healthy_threshold")
        public Integer healthyThreshold;

        @JsonProperty("unhealthy_threshold")
        public Integer unhealthyThreshold;

        /** If set to true the health check is ignored on provision. Default is true. */
        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @Documentation(description = "Ignore health check on provision. Default is true.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @JsonProperty("ignore_on_provision")
        public Boolean ignoreOnProvision;

        /** If set to true the unhealthy containers from a description will be redeployed. */
        @Since(ReleaseConstants.RELEASE_VERSION_0_9_5)
        @Documentation(description = "Automatic redeployment of containers.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Boolean autoredeploy;

        public String command;
    }

    private Bootstrap bootstrap;

    public static HealthChecker getInstance() {
        if (instance == null) {
            synchronized (HealthChecker.class) {
                if (instance == null) {
                    instance = new HealthChecker();
                }
            }
        }

        return instance;
    }

    private HealthChecker() {
        initialize();
    }

    /**
     * Initialize Netty bootstrap
     */
    private void initialize() {
        this.bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel arg0) throws Exception {
                        // Nothing to setup
                    }
                });
    }

    void doHealthCheck(ServiceHost host, String containerDescriptionLink) {
        host.sendRequest(Operation
                .createGet(host, containerDescriptionLink)
                .setReferer(UriUtils.buildUri(host, SERVICE_REFERRER_PATH))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Failed to fetch %s : %s",
                                containerDescriptionLink, Utils.toJson(ex));
                    } else {
                        processContainerHealth(host, o.getBody(ContainerDescription.class));
                    }
                }));
    }

    public void doHealthCheckRequest(ServiceHost host, ContainerState containerState,
            HealthConfig healthConfig, Consumer<ContainerStats> callback) {
        if (healthConfig == null || healthConfig.protocol == null) {
            return;
        }
        if (containerState.powerState == PowerState.PAUSED ||
                containerState.powerState == PowerState.RETIRED ||
                containerState.powerState == PowerState.PROVISIONING ||
                containerState.powerState == PowerState.STOPPED) {

            if (callback != null) {
                callback.accept(null);
            }

            return;
        }

        switch (healthConfig.protocol) {
        case HTTP:
            healthCheckHttp(host, containerState, healthConfig, null, null, callback);
            break;
        case TCP:
            healthCheckTcp(host, containerState, healthConfig, null, null, callback);
            break;
        case COMMAND:
            healthCheckExec(host, containerState, healthConfig, callback);
            break;
        default:
            host.log(Level.SEVERE, "Health config protocol not supported: %s",
                    healthConfig.protocol);
            break;
        }
    }

    private void processContainerHealth(ServiceHost host,
            ContainerDescription containerDescription) {

        QueryTask compositeQueryTask = QueryUtil.buildQuery(ContainerState.class, true);

        QueryUtil.addExpandOption(compositeQueryTask);

        String containerDescriptionLink = UriUtils.buildUriPath(
                ManagementUriParts.CONTAINER_DESC,
                Service.getId(containerDescription.documentSelfLink));
        QueryUtil.addListValueClause(compositeQueryTask,
                ContainerState.FIELD_NAME_DESCRIPTION_LINK,
                Arrays.asList(containerDescriptionLink));

        new ServiceDocumentQuery<ContainerState>(host, ContainerState.class)
                .query(compositeQueryTask, (r) -> {
                    if (r.hasException()) {
                        host.log(Level.SEVERE,
                                "Failed to retrieve container's health config: %s - %s",
                                r.getDocumentSelfLink(), r.getException());
                    } else if (r.hasResult()) {
                        doHealthCheckRequest(host, r.getResult(), containerDescription.healthConfig,
                                null);
                    }
                });
    }

    private void healthCheckExec(ServiceHost host, ContainerState containerState,
            HealthConfig healthConfig,
            Consumer<ContainerStats> callback) {

        ShellContainerExecutorState executorState = new ShellContainerExecutorState();
        executorState.command = healthConfig.command.split(" ");
        executorState.attachStdOut = false;

        URI executeUri = UriUtils.buildUri(host, ShellContainerExecutorService.SELF_LINK);

        executeUri = UriUtils.extendUriWithQuery(executeUri,
                ShellContainerExecutorService.CONTAINER_LINK_URI_PARAM,
                containerState.documentSelfLink);

        host.sendRequest(Operation
                .createPost(executeUri)
                .setReferer(UriUtils.buildUri(host, SERVICE_REFERRER_PATH))
                .setBody(executorState)
                .setCompletion((o, e) -> {
                    String body = o.getBody(String.class);
                    if (e == null && body != null && !body.isEmpty()) {
                        // We have requested stderr only
                        e = new RuntimeException(
                                String.format("Health check failed: %s",
                                        o.getBody(String.class)));
                    }
                    handleHealthResponse(host, containerState, e, callback);
                }));
    }

    private void healthCheckTcp(ServiceHost host, ContainerState containerState,
            HealthConfig healthConfig, String targetAddress, Integer targetPort,
            Consumer<ContainerStats> callback) {
        if (targetAddress == null) {
            determineContainerHostPort(host, containerState, healthConfig,
                    (address, port) -> healthCheckTcp(host, containerState, healthConfig,
                            address, port, callback));
            return;
        }

        this.bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, getTimeoutMillis(healthConfig));

        targetPort = targetPort != null && targetPort > 0 ? targetPort : DEFAULT_PORT;

        InetSocketAddress remoteAddress = new InetSocketAddress(targetAddress, targetPort);
        ChannelFuture channelFuture = bootstrap.connect(remoteAddress);
        OperationContext origContext = OperationContext.getOperationContext();

        channelFuture.addListener((ChannelFutureListener) result -> {
            try {
                OperationContext.setFrom(origContext);
                handleHealthResponse(host, containerState, result.cause(), callback);
            } finally {
                result.channel().close();
            }
        });
    }

    private void healthCheckHttp(ServiceHost host, ContainerState containerState,
            HealthConfig healthConfig, String targetAddress, Integer targetPort,
            Consumer<ContainerStats> callback) {

        if (targetAddress == null) {
            determineContainerHostPort(host, containerState, healthConfig,
                    (address, port) -> healthCheckHttp(host, containerState, healthConfig,
                            address, port, callback));
            return;
        }

        URI uri;
        try {
            uri = constructUri(healthConfig, targetAddress, targetPort);
        } catch (URISyntaxException e) {
            host.log(Level.SEVERE, "Health config for container description %s is invalid: %s",
                    containerState.descriptionLink, Utils.toJson(e));
            return;
        }

        // with createGet the authorization context is propagated to the completion handler...
        Operation op = Operation
                .createGet(uri)
                .setAction(healthConfig.httpMethod)
                .setReferer(UriUtils.buildUri(host, SERVICE_REFERRER_PATH))
                .setCompletion((o, ex) -> handleHealthResponse(host, containerState, ex, callback));

        if (healthConfig.httpVersion == HttpVersion.HTTP_v2) {
            op.setConnectionSharing(true);
        }

        op.setExpiration(ServiceUtils.getExpirationTimeFromNowInMicros(
                TimeUnit.MILLISECONDS.toMicros(getTimeoutMillis(healthConfig))));

        host.sendRequest(op);
    }

    private int getTimeoutMillis(HealthConfig healthConfig) {
        return healthConfig.timeoutMillis == null || healthConfig.timeoutMillis < 0
                ? DEFAULT_TIMEOUT : healthConfig.timeoutMillis;
    }

    private URI constructUri(HealthConfig healthConfig, String address, Integer port)
            throws URISyntaxException {
        String urlPath = UriUtils.URI_PATH_CHAR;
        if (healthConfig.urlPath != null && healthConfig.urlPath.length() > 0) {
            urlPath = UriUtils.buildUriPath(healthConfig.urlPath);
        }

        if (healthConfig.port != null && healthConfig.port > 0) {
            return new URI(UriUtils.HTTP_SCHEME, null, address, port, urlPath, null, null);
        } else {
            return new URI(UriUtils.HTTP_SCHEME, address, urlPath, null);
        }
    }

    private void determineContainerHostPort(ServiceHost host, ContainerState containerState,
            HealthConfig healthConfig, BiConsumer<String, Integer> callback) {

        if (containerState.ports != null && healthConfig.port != null) {
            for (PortBinding portBinding : containerState.ports) {
                if (portBinding.hostPort != null && portBinding.containerPort != null
                        && !portBinding.hostPort.isEmpty()
                        && Integer.parseInt(portBinding.containerPort) == healthConfig.port) {
                    getHostPortBinding(host, containerState, Integer.parseInt(portBinding.hostPort),
                            null,
                            callback);
                    return;
                }
            }
        }

        host.log(Level.WARNING,
                "Container does not expose ports - using container address as public");
        callback.accept(containerState.address, healthConfig.port);
    }

    private void getHostPortBinding(ServiceHost host, ContainerState containerState, int port,
            String hostAddress, BiConsumer<String, Integer> callback) {
        if (hostAddress == null || hostAddress.isEmpty()) {
            getContainerHost(host, containerState.parentLink,
                    (h) -> getHostPortBinding(host, containerState, port, h.address, callback));
            return;
        }

        callback.accept(UriUtilsExtended.extractHost(hostAddress), port);
    }

    private void getContainerHost(ServiceHost host, String parentLink,
            Consumer<ComputeState> callback) {
        host.sendRequest(Operation
                .createGet(UriUtils.buildUri(host, parentLink))
                .setReferer(UriUtils.buildUri(host, SERVICE_REFERRER_PATH))
                .setCompletion(
                        (ob, ex) -> {
                            if (ex != null) {
                                host.log(Level.SEVERE,
                                        "Unable to retrieve container's host during health "
                                                + "check: %s",
                                        Utils.toJson(ex));
                            } else {
                                callback.accept(ob.getBody(ComputeState.class));
                            }
                        }));
    }

    private void handleHealthResponse(ServiceHost host, ContainerState containerState, Throwable ex,
            Consumer<ContainerStats> callback) {
        if (ex != null) {
            host.log(Level.WARNING, "Health check status is failed for container %s : %s",
                    containerState, Utils.toJson(ex));
        }

        /* if ex != null, the health check is failed */
        ContainerStats containerStats = new ContainerStats();
        containerStats.healthCheckSuccess = (ex == null);
        containerStats.containerStopped = containerState.powerState == PowerState.STOPPED;
        URI uri = UriUtils.buildUri(host, containerState.documentSelfLink);
        host.sendRequest(Operation.createPatch(uri)
                .setBody(containerStats)
                .setReferer(UriUtils.buildUri(host, SERVICE_REFERRER_PATH))
                .setCompletion((ob, exception) -> {
                    if (exception != null) {
                        host.log(Level.WARNING,
                                "Failed to patch health status on periodic maintenance: %s : %s",
                                containerState.documentSelfLink, Utils.toJson(exception));

                        if (callback != null) {
                            callback.accept(null);
                        }

                        return;
                    }
                    if (callback != null) {
                        callback.accept(ob.getBody(ContainerStats.class));
                    }
                }));
    }

}
