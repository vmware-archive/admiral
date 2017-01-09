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

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

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
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Describes a container instance. The same description service instance can be re-used across many
 * container instances acting as a shared template.
 */

public class HealthChecker {

    public static class HealthConfig {

        public static enum RequestProtocol {
            HTTP, TCP, COMMAND
        }

        public static enum HttpVersion {
            HTTP_v1_1, HTTP_v2
        }

        public static final String FIELD_NAME_PORT = "port";
        public static final String FIELD_NAME_URL = "url";
        public static final String FIELD_NAME_METHOD = "method";

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

        public String command;

        public boolean continueProvisioningOnError;
    }

    private final ServiceHost host;

    public HealthChecker(ServiceHost host) {
        this.host = host;
    }

    public void doHealthCheck(URI healthConfigLink) {
        doHealthCheck(healthConfigLink, null);
    }

    public void doHealthCheck(URI healthConfigLink, Consumer<ContainerStats> callback) {
        host.sendRequest(Operation
                .createGet(healthConfigLink)
                .setReferer(host.getPublicUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.WARNING,
                                "Failed to fetch self state for periodic maintenance: %s",
                                o.getUri());
                    } else {
                        ContainerDescription containerDescription = o
                                .getBody(ContainerDescription.class);
                        processContainerHealth(containerDescription, callback);
                    }
                }));

    }

    public void doHealthCheckRequest(ContainerState containerState,
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
            healthCheckHttp(containerState, healthConfig, null, callback);
            break;
        case TCP:
            healthCheckTcp(containerState, healthConfig, null, callback);
            break;
        case COMMAND:
            healthCheckExec(containerState, healthConfig, callback);
            break;
        default:
            host.log(Level.WARNING, "Health config protocol not supported: %s",
                    healthConfig.protocol);
            break;
        }
    }

    private void processContainerHealth(ContainerDescription containerDescription, Consumer<ContainerStats> callback) {

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
                        doHealthCheckRequest(r.getResult(), containerDescription.healthConfig, callback);
                    }
                });
    }

    private void healthCheckExec(ContainerState containerState, HealthConfig healthConfig, Consumer<ContainerStats> callback) {

        ShellContainerExecutorState executorState = new ShellContainerExecutorState();
        executorState.command = healthConfig.command.split(" ");
        executorState.attachStdOut = false;

        URI executeUri = UriUtils.buildUri(host, ShellContainerExecutorService.SELF_LINK);

        executeUri = UriUtils.extendUriWithQuery(executeUri,
                ShellContainerExecutorService.CONTAINER_LINK_URI_PARAM,
                containerState.documentSelfLink);

        host.sendRequest(Operation
                .createPost(executeUri)
                .setReferer(host.getPublicUri())
                .setBody(executorState)
                .setCompletion((o, e) -> {
                    String body = o.getBody(String.class);
                    if (e == null && body != null && !body.isEmpty()) {
                        // We have requested stderr only
                        e = new RuntimeException(
                                String.format("Health check failed: %s",
                                        o.getBody(String.class)));
                    }
                    handleHealthResponse(containerState, e, callback);
                }));

    }

    private void healthCheckTcp(ContainerState containerState, HealthConfig healthConfig,
            String[] hostPortBindings, Consumer<ContainerStats> callback) {
        if (hostPortBindings == null) {
            determineContainerHostPort(containerState, healthConfig,
                    (bindings) -> healthCheckTcp(containerState, healthConfig,
                            bindings, callback));
            return;
        }

        Integer configPort = Integer.valueOf(hostPortBindings[1]);
        int port = configPort > 0 ? configPort : 80;
        Bootstrap bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .remoteAddress(new InetSocketAddress(hostPortBindings[0], port))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, healthConfig.timeoutMillis)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel arg0) throws Exception {
                        // Nothing to setup
                    }
                });

        ChannelFuture channelFuture = bootstrap.connect();
        channelFuture.addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture result) throws Exception {
                handleHealthResponse(containerState, result.cause(), callback);
                result.channel().close();
            }

        });
    }

    private void healthCheckHttp(ContainerState containerState, HealthConfig healthConfig,
            String[] hostPortBindings, Consumer<ContainerStats> callback) {

        if (hostPortBindings == null) {
            determineContainerHostPort(containerState, healthConfig,
                    (bindings) -> healthCheckHttp(containerState, healthConfig,
                            bindings, callback));
            return;
        }

        if (healthConfig.urlPath == null || healthConfig.urlPath.isEmpty()) {
            healthConfig.urlPath = "/";
        }

        URI uri = null;
        try {
            if (!healthConfig.urlPath.startsWith("/")) {
                healthConfig.urlPath = "/" + healthConfig.urlPath;
            }

            if (healthConfig.port != null && healthConfig.port > 0) {
                uri = new URI(UriUtils.HTTP_SCHEME, null, hostPortBindings[0],
                        Integer.parseInt(hostPortBindings[1]),
                        healthConfig.urlPath, null, null);
            } else {
                uri = new URI(UriUtils.HTTP_SCHEME, hostPortBindings[0], healthConfig.urlPath, null);
            }
        } catch (URISyntaxException e) {
            host.log(Level.WARNING, "Health config for container description %s is invalid: %s",
                    containerState.descriptionLink, e);
            return;
        }

        // with createGet the authorization context is propagated to the completion handler...
        Operation op = Operation
                .createGet(uri)
                .setAction(healthConfig.httpMethod)
                .setReferer(host.getPublicUri())
                .setCompletion(
                        (o, ex) -> {
                            handleHealthResponse(containerState, ex, callback);
                        });

        if (healthConfig.httpVersion == HttpVersion.HTTP_v2) {
            op.setConnectionSharing(true);
        }

        if (healthConfig.timeoutMillis != null && healthConfig.timeoutMillis > 0) {
            op.setExpiration(ServiceUtils.getExpirationTimeFromNowInMicros(
                    TimeUnit.MILLISECONDS.toMicros(healthConfig.timeoutMillis)));
        }

        host.sendRequest(op);
    }

    private void determineContainerHostPort(ContainerState containerState,
            HealthConfig healthConfig, Consumer<String[]> callback) {

        if (containerState.ports != null) {
            for (PortBinding portBinding : containerState.ports) {
                if (portBinding.hostPort != null && portBinding.containerPort != null
                        && !portBinding.hostPort.isEmpty()
                        && Integer.parseInt(portBinding.containerPort) == healthConfig.port) {
                    getHostPortBinding(containerState, portBinding.hostPort, null,
                            callback);
                    return;
                }
            }
        }
        host.log(Level.WARNING,
                "Container does not expose ports - using container address as public");
        callback.accept(new String[] { containerState.address,
                String.valueOf(healthConfig.port) });
    }

    public void getHostPortBinding(ContainerState containerState, String port,
            String hostAddress, Consumer<String[]> callback) {
        if (hostAddress == null || hostAddress.isEmpty()) {
            getContainerHost(containerState.parentLink,
                    (host) -> getHostPortBinding(containerState, port,
                            host.address, callback));
            return;
        }

        callback.accept(new String[] { UriUtilsExtended.extractHost(hostAddress), port });
    }

    private void getContainerHost(String parentLink, Consumer<ComputeState> callback) {
        host.sendRequest(Operation
                .createGet(UriUtils.buildUri(host, parentLink))
                .setReferer(host.getPublicUri())
                .setCompletion(
                        (ob, ex) -> {
                            if (ex != null) {
                                host.log(Level.SEVERE,
                                        "Unable to retrieve container's host during health check: %s",
                                        ex);
                            } else {
                                callback.accept(ob.getBody(ComputeState.class));
                            }
                        }));

    }

    private void handleHealthResponse(ContainerState containerState, Throwable ex, Consumer<ContainerStats> callback) {
        if (ex != null) {
            host.log(Level.WARNING, "Health check status is failed for container %s : %s",
                    containerState, ex);

        }

        /* if ex != null, the health check is failed */
        ContainerStats containerStats = new ContainerStats();
        containerStats.healthCheckSuccess = (ex == null);
        containerStats.containerStopped = containerState.powerState == PowerState.STOPPED;
        URI uri = UriUtils.buildUri(host, containerState.documentSelfLink);
        host.sendRequest(Operation.createPatch(uri)
                .setBody(containerStats)
                .setReferer(host.getPublicUri())
                .setCompletion((ob, exception) -> {
                    if (exception != null) {
                        host.log(Level.WARNING,
                                "Failed to patch health status on periodic maintenance: %s",
                                containerState.documentSelfLink);

                        if (callback != null) {
                            callback.accept(null);
                        }

                        return;
                    }
                    ContainerStats stats = ob.getBody(ContainerStats.class);
                    if (callback != null) {
                        callback.accept(stats);
                    }
                }));
    }
}
