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

package com.vmware.admiral.adapter.docker.service;

import static com.vmware.admiral.compute.ContainerHostService.SSL_TRUST_ALIAS_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.SSL_TRUST_CERT_PROP_NAME;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.yaml.snakeyaml.util.UriEncoder;

import com.vmware.admiral.adapter.docker.util.DockerStreamUtil;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.DelegatingX509KeyManager;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Docker command executor implementation based on DCP and the docker remote API
 */
public class RemoteApiDockerAdapterCommandExecutorImpl implements
        DockerAdapterCommandExecutor, DockerAdapterStreamCommandExecutor {

    private static final Logger logger = Logger
            .getLogger(RemoteApiDockerAdapterCommandExecutorImpl.class.getName());

    private static final int SSL_TRUST_RETRIES_COUNT = Integer.getInteger(
            "com.vmware.admiral.adapter.ssltrust.delegate.retries", 5);
    private static final long SSL_TRUST_RETRIES_WAIT = Long.getLong(
            "com.vmware.admiral.adapter.ssltrust.delegate.retries.wait.millis", 500);
    private static final int URL_CONNECTION_READ_TIMEOUT = Integer.getInteger(
            "com.vmware.admiral.adapter.url.connection.read.timeout", 20000);
    private static final String THREAD_POOL_QUEUE_SIZE_PROP_NAME = "com.vmware.admiral.adapter.thread.pool.queue.size";
    private static final int THREAD_POOL_QUEUE_SIZE = Integer.getInteger(
            THREAD_POOL_QUEUE_SIZE_PROP_NAME, 1000);
    private static final int THREAD_POOL_KEEPALIVE_SECONDS = Integer.getInteger(
            "com.vmware.admiral.adapter.thread.pool.keepalive.seconds", 30);

    public static final String MEDIA_TYPE_APPLICATION_TAR = "application/tar";

    private static volatile RemoteApiDockerAdapterCommandExecutorImpl INSTANCE;

    private static final Pattern ERROR_PATTERN = Pattern.compile("\"error\":\"(.*)\"");

    private static final AtomicInteger threadCount = new AtomicInteger(0);
    private static final ThreadFactory threadFactory =
            (r) -> new Thread(r, "EventsReader-" + threadCount.incrementAndGet());

    private static ExecutorService executor = new ThreadPoolExecutor(
            THREAD_POOL_QUEUE_SIZE,
            THREAD_POOL_QUEUE_SIZE,
            THREAD_POOL_KEEPALIVE_SECONDS,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>(THREAD_POOL_QUEUE_SIZE),
            threadFactory,
            new ThreadPoolExecutor.AbortPolicy());

    private final ServiceHost host;
    private final ServiceClient serviceClient;
    // Used for commands like exec start
    private final ServiceClient attachServiceClient;
    // Used for commands like load image from tar
    private final ServiceClient largeDataClient;
    // Used for storing the runnning threads which handles opened connections to hosts
    private volatile Map<String, Thread> runningThreads = new HashMap<>();

    private final DelegatingX509KeyManager keyManager = new DelegatingX509KeyManager();
    private ServerX509TrustManager trustManager;

    private final int DOCKER_REQUEST_PAYLOAD_SIZE_LIMIT = Integer.getInteger(
            "adapter.docker.api.client.request_payload_limit", 1024 * 1024 * 256);

    public final int DOCKER_REQUEST_TIMEOUT_SECONDS = Integer.getInteger(
            "adapter.docker.api.client.request_timeout_seconds", 60 * 2);

    private final int DOCKER_IMAGE_REQUEST_TIMEOUT_SECONDS = Integer.getInteger(
            "adapter.docker.api.client.image_request_timeout_seconds", 60 * 10);

    private enum ClientMode {
        DEFAULT,
        ATTACH,
        LARGE_DATA
    }

    protected RemoteApiDockerAdapterCommandExecutorImpl(ServiceHost host,
            TrustManager trustManager) {
        this.host = host;
        this.serviceClient = ServiceClientFactory.createServiceClient(trustManager, keyManager);
        this.attachServiceClient = ServiceClientFactory.createServiceClient(trustManager,
                keyManager);
        this.largeDataClient = ServiceClientFactory.createServiceClient(
                trustManager, keyManager, DOCKER_REQUEST_PAYLOAD_SIZE_LIMIT);

        if (trustManager instanceof ServerX509TrustManager) {
            this.trustManager = (ServerX509TrustManager) trustManager;
        }
    }

    public static RemoteApiDockerAdapterCommandExecutorImpl create(ServiceHost host,
            TrustManager trustManager) {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        synchronized (RemoteApiDockerAdapterCommandExecutorImpl.class) {
            if (INSTANCE == null) {
                INSTANCE = new RemoteApiDockerAdapterCommandExecutorImpl(host, trustManager);
            }
        }
        return INSTANCE;
    }

    @Override
    public void stop() {
        logger.info("Stopping service clients");

        if (attachServiceClient != null) {
            attachServiceClient.stop();
        }
        if (this.serviceClient != null) {
            this.serviceClient.stop();
        }
        if (largeDataClient != null) {
            largeDataClient.stop();
        }

        INSTANCE = null;
    }

    @Override
    public void handlePeriodicMaintenance(Operation post) {
        if (attachServiceClient != null) {
            attachServiceClient.handleMaintenance(Operation.createPost(post.getUri()));
        }
        if (serviceClient != null) {
            serviceClient.handleMaintenance(Operation.createPost(post.getUri()));
        }
        if (largeDataClient != null) {
            largeDataClient.handleMaintenance(Operation.createPost(post.getUri()));
        }
        post.complete();
    }

    // image operations ----------------------------------------------------------------------------

    @Override
    public void buildImage(CommandInput input, Operation.CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        URI uri = UriUtils.extendUri(input.getDockerUri(), "/build");

        Map<String, Object> props = input.getProperties();
        byte[] imageData = (byte[]) props.get(DockerAdapterCommandExecutor
                .DOCKER_BUILD_IMAGE_DOCKERFILE_DATA);

        props.remove(DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_DOCKERFILE_DATA);
        props.remove(SSL_TRUST_ALIAS_PROP_NAME);
        props.remove(SSL_TRUST_CERT_PROP_NAME);

        uri = extendUriWithQuery(uri, input);

        logger.info("Building image on: " + uri);

        Operation op = Operation.createPost(uri)
                .setBodyNoCloning(imageData)
                .setContentType(MEDIA_TYPE_APPLICATION_TAR)
                .setCompletion((o, ex) -> {
                    String body = o.getBody(String.class);
                    logger.info("Dump response body: " + body);
                    if (ex == null) {
                        // check the last status to make sure there was no error during download
                        // the response is not valid json so this is hacky way to find out if there
                        // was an error
                        Matcher matcher = ERROR_PATTERN.matcher(body);
                        if (matcher.find()) {
                            logger.info("Build failure detected! Response Body: " + body);
                            ex = new RuntimeException("Error: " + body);
                        }
                    } else {
                        logger.severe("Unable to create image! Reason: " + ex.getMessage());
                    }
                    completionHandler.handle(o, ex);
                });

        prepareRequest(op, true);
        largeDataClient.send(op);

        logger.info("Building image request sent.");
    }

    @Override
    public void deleteImage(CommandInput input, Operation.CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/images/%s", input.getProperties()
                .get(DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_TAG_PROP_NAME));
        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        logger.info("Deleting image: " + uri);

        sendDelete(uri, completionHandler);
    }

    @Override
    public void inspectImage(CommandInput input, Operation.CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String
                .format("/images/%s/json", input.getProperties().get(DockerAdapterCommandExecutor
                        .DOCKER_BUILD_IMAGE_INSPECT_NAME_PROP_NAME));
        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        logger.info("Inspecting image: " + uri);

        sendRequest(Service.Action.GET, uri, null, completionHandler, ClientMode.LARGE_DATA);
    }

    @Override
    public void loadImage(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        Object imageData = input.getProperties().get(DOCKER_IMAGE_DATA_PROP_NAME);
        URI uri = UriUtils.extendUri(input.getDockerUri(), "/images/load");
        logger.info("Loading image: " + uri);

        sendPost(uri, imageData, ClientMode.LARGE_DATA, completionHandler);
    }

    @Override
    public void createImage(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        URI uri = UriUtils.extendUri(input.getDockerUri(), "/images/create");
        // extract X-Registry-Auth as a request header
        String registryAuth = (String) input.getProperties().remove(DOCKER_IMAGE_REGISTRY_AUTH);

        // convert the properties to URL parameters, the way /images/create likes them
        // expect fromImage or fromSrc
        uri = extendUriWithQuery(uri, input);

        Operation op = Operation.createPost(uri)
                .setBodyNoCloning("")
                .setCompletion((o, ex) -> {
                    if (ex == null) {
                        // check the last status to make sure there was no error during download
                        // the response is not valid json so this is hacky way to find out if there
                        // was an error
                        String body = o.getBody(String.class);

                        Matcher matcher = ERROR_PATTERN.matcher(body);
                        if (matcher.find()) {
                            completionHandler.handle(o, new RuntimeException("Error: "
                                    + matcher.group(1)));
                            return;
                        }
                    }
                    completionHandler.handle(o, ex);
                });

        if (registryAuth != null) {
            op.addRequestHeader(DOCKER_IMAGE_REGISTRY_AUTH, registryAuth);
        }

        logger.info("Creating image: " + uri);

        prepareRequest(op, true);
        serviceClient.send(op);
    }

    @Override
    public void tagImage(CommandInput input, Operation.CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        Map<String, Object> props = input.getProperties();
        String path = String
                .format("/images/%s/tag", props.get(DockerAdapterCommandExecutor
                        .DOCKER_IMAGE_NAME_PROP_NAME));
        URI uri = UriUtils.extendUri(input.getDockerUri(), path);

        props.remove(DOCKER_IMAGE_NAME_PROP_NAME);

        // convert the properties to URL parameters, the way /images/{name}/tag likes them
        uri = extendUriWithQuery(uri, input);

        logger.info("Tagging image: " + uri);

        sendPost(uri, null, ClientMode.DEFAULT, completionHandler);
    }

    // container operations ------------------------------------------------------------------------

    @Override
    public void createContainer(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        URI uri = UriUtils.extendUri(input.getDockerUri(), "/containers/create");
        String containerName = (String) input.getProperties().get(DOCKER_CONTAINER_NAME_PROP_NAME);
        if (containerName != null) {
            uri = UriUtils.extendUriWithQuery(uri, "name", containerName);
        }
        logger.info("Creating container: " + uri);

        sendPost(uri, input.getProperties(), ClientMode.DEFAULT, completionHandler);
    }

    @Override
    public void startContainer(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/containers/%s/start", input
                .getProperties().get(DOCKER_CONTAINER_ID_PROP_NAME));
        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        logger.info("Starting container: " + uri);

        sendPost(uri, null, ClientMode.DEFAULT, completionHandler);
    }

    @Override
    public void stopContainer(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/containers/%s/stop", input
                .getProperties().get(DOCKER_CONTAINER_ID_PROP_NAME));
        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        logger.info("Stopping container: " + uri);

        sendPost(uri, null, ClientMode.DEFAULT, completionHandler);
    }

    @Override
    public void inspectContainer(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/containers/%s/json", input
                .getProperties().get(DOCKER_CONTAINER_ID_PROP_NAME));
        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        logger.info("Inspecting container: " + uri);

        sendGet(uri, input.getProperties(), completionHandler);
    }

    @Override
    public void execContainer(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        createExec(input, completionHandler);
    }

    @Override
    public void fetchContainerStats(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/containers/%s/stats", input
                .getProperties().get(DOCKER_CONTAINER_ID_PROP_NAME));
        // add the stream=0 query parameter so the request returns immediately (requires docker 1.7)
        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        uri = UriUtils.extendUriWithQuery(uri, "stream", "0");
        logger.info("Fetch stats for container: " + uri);

        sendGet(uri, input.getProperties(), completionHandler);
    }

    @Override
    public void removeContainer(CommandInput input, CompletionHandler completionHandler) {

        createOrUpdateTargetSsl(input);

        String path = String.format("/containers/%s", input
                .getProperties().get(DOCKER_CONTAINER_ID_PROP_NAME));

        // add v=1 and force=1 query parameters
        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        uri = UriUtils.extendUriWithQuery(uri, "v", "1", "force", "1");
        logger.info("Removing container: " + uri);

        sendDelete(uri, completionHandler);
    }

    @Override
    public void fetchContainerLog(CommandInput input, CompletionHandler completionHandler) {
        // Update certificates
        createOrUpdateTargetSsl(input);

        // Construct the request path for logs
        String requestPath = String.format("/containers/%s/logs",
                input.getProperties().remove(DOCKER_CONTAINER_ID_PROP_NAME));
        URI uri = UriUtils.extendUri(input.getDockerUri(), requestPath);
        // append all the query parameters which are sent as input.
        uri = extendUriWithQuery(uri, input);
        logger.info("Fetching logs for container: " + uri);

        sendGet(uri, null, completionHandler);
    }

    @Override
    public void listContainers(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        URI uri = UriUtils.extendUri(input.getDockerUri(), "/containers/json");
        uri = UriUtils.extendUriWithQuery(uri, "all", "1");
        logger.info("List containers: " + uri);

        sendGet(uri, null, completionHandler);
    }

    // host operations -----------------------------------------------------------------------------

    @Override
    public void hostPing(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        DelegatingX509KeyManager keyM = new DelegatingX509KeyManager();
        ServerX509TrustManager trustM = ServerX509TrustManager.create(host);
        createOrUpdateTargetSsl(input, keyM, trustM);
        ServiceClient serviceClientPing = ServiceClientFactory.createServiceClient(trustM, keyM);

        Operation op = Operation
                .createGet(UriUtils.extendUri(input.getDockerUri(), "/_ping"))
                .setCompletion((o, e) -> {
                    try {
                        completionHandler.handle(o, e);
                    } finally {
                        try {
                            serviceClientPing.stop();
                        } catch (Exception ee) {
                            logger.warning(" Exception while closing ping ServiceClient. " + ee
                                    .getMessage());
                        }
                    }
                });

        prepareRequest(op, false);
        op.setExpiration(ServiceUtils.getExpirationTimeFromNowInMicros(
                TimeUnit.SECONDS.toMicros(10)));

        setConnectionTag(input.getCredentials(), op);
        logger.info("Ping host: " + op.getUri());

        if (isSecure(input.getDockerUri())) {
            // Make sure that the trusted certificate is loaded before proceeding to avoid
            // SSLHandshakeException and getting hosts in DISABLED state
            ensureTrustDelegateExists(input, SSL_TRUST_RETRIES_COUNT, () -> {
                serviceClientPing.send(op);
            });
        } else {
            serviceClientPing.send(op);
        }
    }

    @Override
    public void hostInfo(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        Operation op = Operation
                .createGet(UriUtils.extendUri(input.getDockerUri(), "/info"))
                .setCompletion(completionHandler);

        prepareRequest(op, false);

        setConnectionTag(input.getCredentials(), op);
        logger.info("Get info for host: " + op.getUri());

        if (isSecure(input.getDockerUri())) {
            // Make sure that the trusted certificate is loaded before proceeding to avoid
            // SSLHandshakeException and getting hosts in DISABLED state
            ensureTrustDelegateExists(input, SSL_TRUST_RETRIES_COUNT, () -> {
                serviceClient.send(op);
            });
        } else {
            serviceClient.send(op);
        }
    }

    @Override
    public void hostVersion(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        URI uri = UriUtils.extendUri(input.getDockerUri(), "/version");
        logger.info("Get version for host: " + uri);

        sendGet(uri, null, completionHandler);
    }

    @Override
    public void hostSubscribeForEvents(CommandInput input, Operation op, ComputeState computeState) {
        URI baseUri = UriUtils.extendUri(input.getDockerUri(), "/events");
        logger.info("Subscribing for events: " + baseUri);

        if (runningThreads.get(baseUri.getAuthority()) != null) {
            logger.info("Connection is already opened: " + baseUri.getAuthority());
            return;
        }

        // add filter for containers type
        input.withProperty("filters", "{\"type\":[\"container\"]}");

        // append all the query parameters which are sent as input.
        URI extendedUri = extendUriWithQuery(baseUri, input);

        if (isSecure(input.getDockerUri())) {
            // Make sure that the trusted certificate is loaded before proceeding to avoid
            // SSLHandshakeException and getting hosts in DISABLED state
            ensureTrustDelegateExists(input, SSL_TRUST_RETRIES_COUNT, () -> {
                makeSubscription(input, op, computeState, extendedUri, null);
            });
        } else {
            logger.info("Host is not secured: " + baseUri.toString());
            makeSubscription(input, op, computeState, extendedUri, null);
        }
    }

    @Override
    public void hostUnsubscribeForEvents(CommandInput input, ComputeState computeState) {
        URI baseUri = UriUtils.extendUri(input.getDockerUri(), "/events");
        logger.info("Unsubscribing for events: " + baseUri);

        Thread runningThread = runningThreads.get(input.getDockerUri().getAuthority());

        if (runningThread == null) {
            logger.info("Connection already closed!");
            return;
        }

        runningThread.interrupt();
    }

    // network operations

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/#create-a-network
     * Mandatory properties for <code>input</code>:
     * <li>{@link DockerAdapterCommandExecutor#DOCKER_CONTAINER_NETWORK_NAME_PROP_NAME}
     */
    @Override
    public void createNetwork(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        URI uri = UriUtils.extendUri(input.getDockerUri(), "/networks/create");
        logger.info("Create network: " + uri);

        sendPost(uri, input.getProperties(), ClientMode.DEFAULT, completionHandler);
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/#remove-a-network
     */
    @Override
    public void removeNetwork(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String idEncoded = UriEncoder.encode(input
                .getProperties().get(DOCKER_CONTAINER_NETWORK_ID_PROP_NAME).toString());

        String path = String.format("/networks/%s", idEncoded);

        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        logger.info("Remove network: " + uri);
        // TODO there is no force remove for networks. All connected containers must be disconnected
        // or stopped for this to succeed

        sendDelete(uri, completionHandler);
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/#/list-networks
     */
    @Override
    public void listNetworks(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        URI uri = UriUtils.extendUri(input.getDockerUri(), "/networks");
        logger.info("List networks: " + uri);

        sendGet(uri, null, completionHandler);
    }

    @Override
    public void inspectNetwork(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String idEncoded = UriEncoder.encode(input
                .getProperties().get(DOCKER_CONTAINER_NETWORK_ID_PROP_NAME).toString());
        String path = String.format("/networks/%s", idEncoded);
        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        logger.info("Inspect network: " + uri);

        sendGet(uri, input.getProperties(), completionHandler);
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/#/connect-a-container-to-a-network
     */
    @Override
    public void connectContainerToNetwork(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String idEncoded = UriEncoder.encode(input
                .getProperties().get(DOCKER_CONTAINER_NETWORK_ID_PROP_NAME).toString());

        String path = String.format("/networks/%s/connect", idEncoded);
        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        logger.info("Connect container to network: " + uri);

        sendPost(uri, input.getProperties(), ClientMode.DEFAULT, completionHandler);
    }

    // volume operations ---------------------------------------------------------------------------

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/ Section 3.4 Volumes -
     * Create a volume Mandatory properties for <code>input</code>:
     * <li>{@link DockerAdapterCommandExecutor#DOCKER_VOLUME_NAME_PROP_NAME}
     */
    @Override
    public void createVolume(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        URI uri = UriUtils.extendUri(input.getDockerUri(), "/volumes/create");
        logger.info("Creating volume: " + uri);

        sendPost(uri, input.getProperties(), ClientMode.DEFAULT, completionHandler);
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/ Section 3.4 Volumes -
     * Remove a volume
     */
    @Override
    public void removeVolume(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/volumes/%s",
                input.getProperties().get(DOCKER_VOLUME_NAME_PROP_NAME));
        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        logger.info("Removing volume: " + uri);

        sendDelete(uri, completionHandler);
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/ Section 3.4 Volumes -
     * List volumes
     */
    @Override
    public void listVolumes(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        URI uri = UriUtils.extendUri(input.getDockerUri(), "/volumes");
        logger.info("Listing volumes: " + uri);

        sendGet(uri, null, completionHandler);
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/ Section 3.4 Volumes -
     * Inspect a volume
     */
    @Override
    public void inspectVolume(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/volumes/%s", input
                .getProperties().get(DOCKER_VOLUME_NAME_PROP_NAME));
        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        logger.info("Inspecting volume: " + uri);

        sendGet(uri, input.getProperties(), completionHandler);
    }

    @Override
    public void closeConnection(URLConnection con) {
        if (con == null) {
            logger.warning("No connection provided!");
            return;
        }

        logger.info(String.format("Closing connection to host [%s]", con.getURL().getAuthority()));

        ((HttpURLConnection) con).disconnect();
    }

    @Override
    public URLConnection openConnection(CommandInput input, URL url) throws NoSuchAlgorithmException, KeyManagementException, IOException {
        if (isSecure(URI.create(url.toString()))) {

            String clientKey = null;
            String clientCert = null;

            if (input != null && input.getCredentials() != null) {
                clientKey = EncryptionUtils.decrypt(input.getCredentials().privateKey);
                clientCert = input.getCredentials().publicKey;
            }

            // TODO use an LRU cache to limit the number of stored
            // KeyManagers while minimizing time wasted repeatedly
            // recreating them
            KeyManager[] keytManagers = null;
            if (clientKey != null && !clientKey.isEmpty()) {
                X509ExtendedKeyManager delegateKeyManager;
                delegateKeyManager = (X509ExtendedKeyManager) CertificateUtil
                        .getKeyManagers("default", clientKey, clientCert)[0];
                keytManagers = new KeyManager[]{delegateKeyManager};
            }

            TrustManager[] trustManagers = new TrustManager[]{ServerX509TrustManager.init(null)};

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keytManagers, trustManagers, new SecureRandom());

            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setHostnameVerifier((s, sslSession) -> true);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setReadTimeout(0);
            conn.setSSLSocketFactory(sslContext.getSocketFactory());

            return conn;
        }

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoInput(true);
        conn.setDoOutput(false);
        conn.setUseCaches(false);
        conn.setReadTimeout(0);

        return conn;
    }

    /**
     * Common settings on all outgoing requests to the docker server
     */
    protected void prepareRequest(Operation op, boolean longRunningRequest) {
        op.setReferer(URI.create("/"))
                .addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, "")
                .forceRemote();

        if (op.getExpirationMicrosUtc() == 0) {
            long timeout = longRunningRequest ?
                    TimeUnit.SECONDS.toMicros(DOCKER_IMAGE_REQUEST_TIMEOUT_SECONDS) :
                    TimeUnit.SECONDS.toMicros(DOCKER_REQUEST_TIMEOUT_SECONDS);

            op.setExpiration(ServiceUtils.getExpirationTimeFromNowInMicros(timeout));
        }
    }

    private void createExec(CommandInput input, CompletionHandler completionHandler) {
        String containerId = (String) input.getProperties().remove(DOCKER_CONTAINER_ID_PROP_NAME);
        if (containerId == null || containerId.isEmpty()) {
            completionHandler.handle(null,
                    new IllegalArgumentException("Container id not provided"));
            return;
        }

        String[] command = (String[]) input.getProperties().remove(DOCKER_EXEC_COMMAND_PROP_NAME);

        URI execUri = UriUtils.extendUri(input.getDockerUri(),
                String.format("/containers/%s/exec", containerId));

        String attachStdErr = (String) input.getProperties().remove(
                DOCKER_EXEC_ATTACH_STDERR_PROP_NAME);
        String attachStdOut = (String) input.getProperties().remove(
                DOCKER_EXEC_ATTACH_STDOUT_PROP_NAME);

        Map<String, Object> create = new HashMap<>();
        create.put(DOCKER_EXEC_ATTACH_STDIN_PROP_NAME, false);
        create.put(DOCKER_EXEC_ATTACH_STDERR_PROP_NAME,
                attachStdErr != null ? Boolean.valueOf(attachStdErr) : true);
        create.put(DOCKER_EXEC_ATTACH_STDOUT_PROP_NAME,
                attachStdOut != null ? Boolean.valueOf(attachStdOut) : true);
        create.put(DOCKER_EXEC_TTY_PROP_NAME, false);
        create.put(DOCKER_EXEC_COMMAND_PROP_NAME, command);
        logger.info("Creating Exec command for container: " + execUri);

        sendPost(execUri, create, ClientMode.DEFAULT, (o, e) -> {
            if (e != null) {
                completionHandler.handle(o, e);
            } else {
                @SuppressWarnings("unchecked")
                Map<String, String> result = o.getBody(Map.class);
                String execId = result.get(DOCKER_EXEC_ID_PROP_NAME);
                startExec(input.getDockerUri(), execId, completionHandler);
            }
        });
    }

    private void startExec(URI dockerUri, String execId, CompletionHandler completionHandler) {
        Map<String, Object> startBody = new HashMap<>();
        startBody.put(DOCKER_EXEC_TTY_PROP_NAME, false);
        startBody.put(DOCKER_EXEC_DETACH_PROP_NAME, false);

        URI startUri = UriUtils.extendUri(dockerUri, String.format("/exec/%s/start", execId));

        sendPostAttach(startUri, startBody, (op, ex) -> {
            if (ex != null) {
                completionHandler.handle(null, ex);
            } else {
                String output = null;
                if (op.hasBody()) {
                    Object rawBody = op.getBodyRaw();
                    /* when the shell command does not return anything */
                    if (startBody.equals(rawBody)) {
                        output = "";
                    } else if (rawBody instanceof byte[]) {
                        try {
                            output = DockerStreamUtil.decodeFullRawResponse((byte[]) rawBody);
                        } catch (Exception decodeEx) {
                            logger.severe(decodeEx.getMessage());
                            completionHandler.handle(null, decodeEx);
                            return;
                        }
                    } else {
                        String err = "Unexpected response body of docker exec: " + rawBody;
                        logger.severe(err);
                        completionHandler.handle(null, new RuntimeException(err));
                        return;
                    }
                }
                inspectExec(dockerUri, execId, output, completionHandler);
            }
        });
    }

    private void inspectExec(URI dockerUri, String execId, String output, CompletionHandler c) {
        URI inspectUri = UriUtils.extendUri(dockerUri, String.format("/exec/%s/json", execId));

        sendGet(inspectUri, null, (op, ex) -> {
            if (ex != null) {
                logger.severe("Cannot inspect execution " + execId + " : " + ex.getMessage());
                c.handle(null, ex);
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = op.getBody(Map.class);
                result.put(DOCKER_EXEC_OUTPUT, output);
                op.setBodyNoCloning(result);
                c.handle(op, null);
            }
        });
    }

    /**
     * Constructs the URI query parameters with the set of input properties from the command.
     */
    private URI extendUriWithQuery(URI targetUri, CommandInput input) {
        List<String> parameters = new ArrayList<>();
        for (Map.Entry<String, Object> property : input.getProperties().entrySet()) {
            parameters.add(property.getKey());
            parameters.add(String.valueOf(property.getValue()));
        }

        return UriUtils.extendUriWithQuery(targetUri, parameters.toArray(new String[0]));
    }

    /**
     * Update the dynamic KeyManager and TrustManager with the client and server certs for the
     * current request
     */

    private void createOrUpdateTargetSsl(CommandInput input) {
        createOrUpdateTargetSsl(input,  keyManager, trustManager);

    }

    private void createOrUpdateTargetSsl(CommandInput input, DelegatingX509KeyManager keyM,
            ServerX509TrustManager trustM) {
        if (input.getCredentials() == null) {
            return;
        }

        if (!isSecure(input.getDockerUri())) {
            return;
        }

        String sslTrust = (String) input.getProperties().get(SSL_TRUST_CERT_PROP_NAME);
        String trustAlias = (String) input.getProperties().get(SSL_TRUST_ALIAS_PROP_NAME);
        if (trustAlias == null) {
            logger.warning("No trust alias property set, not using certificate.");
            return;
        }

        if (sslTrust != null && trustM != null) {
            trustM.putDelegate(trustAlias, sslTrust);
        }

        String clientKey = EncryptionUtils.decrypt(input.getCredentials().privateKey);
        String clientCert = input.getCredentials().publicKey;

        // TODO use an LRU cache to limit the number of stored
        // KeyManagers while minimizing time wasted repeatedly
        // recreating them
        if (clientKey != null && !clientKey.isEmpty()) {
            X509ExtendedKeyManager delegateKeyManager = (X509ExtendedKeyManager) CertificateUtil
                    .getKeyManagers(trustAlias, clientKey, clientCert)[0];
            keyM.putDelegate(trustAlias, delegateKeyManager);
        }
    }

    private boolean isSecure(URI dockerUri) {
        AssertUtil.assertNotNull(dockerUri, "dockerUri");

        return UriUtils.HTTPS_SCHEME.equalsIgnoreCase(dockerUri.getScheme());
    }

    private void ensureTrustDelegateExists(CommandInput input, int retryCount, Runnable callback) {
        if (trustManager != null) {
            String trustAlias = (String) input.getProperties().get(SSL_TRUST_ALIAS_PROP_NAME);
            if (trustAlias == null) {
                // trustAlias not defined, continue anyways, might fail later
                logger.warning("Trust alias not defined");
                callback.run();
                return;
            }
            X509TrustManager delegate = trustManager.getDelegate(trustAlias);
            if (delegate != null) {
                callback.run();
                return;
            }
            if (retryCount > 0) {
                host.schedule(() -> {
                    logger.info("Waiting for the trust delegate for " + trustAlias
                            + " to be populated");
                    ensureTrustDelegateExists(input, retryCount - 1, callback);
                }, SSL_TRUST_RETRIES_WAIT, TimeUnit.MILLISECONDS);
            } else {
                // trust not found, continue anyways, might fail later
                logger.warning("Trust delegate for " + trustAlias + " not found");
                callback.run();
            }
        } else {
            callback.run();
        }
    }

    private void sendGet(URI uri, Object body, CompletionHandler completionHandler) {
        sendRequest(Service.Action.GET, uri, body, completionHandler, ClientMode.DEFAULT);
    }

    private void sendPost(URI uri, Object body, ClientMode mode,
            CompletionHandler completionHandler) {
        if (ClientMode.LARGE_DATA != mode) {
            String msg = String.format("Sending POST to %s with body (possibly truncated):"
                    + "%n---%n%1.1024s%n---%n", uri, Utils.toJsonHtml(body));
            logger.finest(msg);
        } else {
            String msg = String.format("Sending POST to %s with large body", uri);
            logger.finest(msg);
        }
        sendRequest(Service.Action.POST, uri, body, completionHandler, mode);
    }

    private void sendPostAttach(URI uri, Object body, CompletionHandler completionHandler) {
        String msg = String.format("Sending POST for attach to %s with body (possibly truncated):"
                + "%n---%n%1.1024s%n---%n", uri, Utils.toJsonHtml(body));
        logger.finest(msg);
        sendRequest(Service.Action.POST, uri, body, completionHandler, ClientMode.ATTACH);
    }

    private void sendDelete(URI uri, CompletionHandler completionHandler) {
        sendRequest(Service.Action.DELETE, uri, null, completionHandler, ClientMode.DEFAULT);
    }

    private void sendRequest(Service.Action action, URI uri, Object body,
            CompletionHandler completionHandler, ClientMode mode) {
        // with createGet the authorization context is propagated to the completion handler...
        Operation op = Operation.createGet(uri)
                .setAction(action)
                .setCompletion(completionHandler);

        if (ClientMode.LARGE_DATA == mode) {
            op.setBodyNoCloning(body);
            prepareRequest(op, true);
            largeDataClient.send(op);
        } else if (ClientMode.ATTACH == mode) {
            op.setBody(body);
            prepareRequest(op, false);
            attachServiceClient.send(op);
        } else {
            op.setBody(body);
            prepareRequest(op, false);
            serviceClient.send(op);
        }
    }

    private void setConnectionTag(AuthCredentialsServiceState credentials, Operation op) {
        // Avoid reusing an open channel to this host to ensure certs validation.
        if (credentials != null) {
            op.setConnectionTag(credentials.documentSelfLink +
                    String.valueOf(credentials.documentUpdateTimeMicros));
        }
    }

    /**
     * Used for creation of a new thread for each host event subscription.
     */
    private class EventsMonitor implements Runnable {

        public Operation op;
        public URI hostUri;
        public CommandInput input;
        public ComputeState computeState;
        public boolean simulatedIOException;

        public EventsMonitor(Operation op,
                URI hostUri,
                CommandInput input,
                ComputeState computeState,
                boolean simulatedIOException) {
            this.op = op;
            this.hostUri = hostUri;
            this.input = input;
            this.computeState = computeState;
            this.simulatedIOException = simulatedIOException;
        }

        @Override
        public void run() {
            logger.info(String.format("Simulation of IOException enabled: [%s]",
                    simulatedIOException));
            OperationContext childContext = OperationContext.getOperationContext();

            boolean shouldRestoreContext = true;

            try {
                // set system user context
                OperationContext.setFrom(op);

                URL url = new URL(hostUri.toString());
                URLConnection con = openConnection(input, url);

                // setting "read timeout" to reset the blocking stream (reader.readLine()).
                // Otherwise there is no way to close the connection.
                con.setReadTimeout(URL_CONNECTION_READ_TIMEOUT);

                final String hostName = url.getAuthority();
                runningThreads.put(hostName, Thread.currentThread());

                try (BufferedReader in = new BufferedReader(new InputStreamReader(
                        con.getInputStream()))) {
                    readData(in, simulatedIOException);
                } catch (InterruptedException e) {
                    logger.fine(String.format("[%s] is thrown.", e.getClass().getName()));
                } catch (IOException e) {
                    logger.info(String.format("IOException when listening [%s]. Error: [%s]",
                            hostName, e.getMessage()));

                    shouldRestoreContext = false;

                    ComputeState state = new ComputeState();
                    state.powerState = ComputeService.PowerState.UNKNOWN;

                    patchComputeState(computeState.documentSelfLink, state)
                            .thenCompose((ignore) -> {
                                // changing the power state of containers to UNKNOWN
                                return queryExistingContainerStates(computeState.documentSelfLink);
                            })
                            .whenComplete((o, ex) -> {
                                OperationContext.restoreOperationContext(childContext);
                            });
                } catch (Exception e) {
                    logger.warning(String.format("Exception in subscription to [%s]. Error: [%s]",
                            hostName, e.getMessage()));
                } finally {
                    runningThreads.remove(hostName);
                    closeConnection(con);
                }
            } catch (Throwable t) {
                logger.warning(Utils.toString(t));
            } finally {
                if (shouldRestoreContext) {
                    OperationContext.restoreOperationContext(childContext);
                }
            }
        }
    }

    private void makeSubscription(CommandInput input, Operation op, ComputeState computeState, URI uri,
            Boolean simulateIOExceptionPropertyValue) {
        boolean maxAllowedNumberOfThreadsExceeded = maxAllowedNumberOfThreadsExceeded();

        if (maxAllowedNumberOfThreadsExceeded) {
            return;
        }

        if (simulateIOExceptionPropertyValue == null) {
            ConfigurationUtil.getConfigProperty(host, ConfigurationUtil.THROW_IO_EXCEPTION,
                    (prop) -> {
                        Boolean b = Boolean.valueOf(prop);
                        makeSubscription(input, op, computeState, uri, b);
                    });
            return;
        }

        EventsMonitor eventsMonitor = new EventsMonitor(
                op,
                uri,
                input,
                computeState,
                simulateIOExceptionPropertyValue);

        executor.submit(eventsMonitor);
    }

    private DeferredResult<ComputeState> requestComputeState(String selfLink) {
        URI uri = UriUtils.buildUri(host, selfLink);

        Operation op = Operation.createGet(uri)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logger.warning(String.format("Failed to retrieve compute state: [%s]", Utils.toString(e)));
                        return;
                    }
                });

        prepareRequest(op, false);

        return host.sendWithDeferredResult(op, ComputeState.class);
    }

    private DeferredResult patchComputeState(String documentSelfLink, ComputeState computeState) {
        URI uri = UriUtils.buildUri(host, documentSelfLink);

        Operation op = Operation.createPatch(uri)
                .setBody(computeState)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logger.warning(String.format("Failed to patch compute state: [%s]", Utils.toString(e)));
                        return;
                    }

                    logger.info(String.format("Successfully patched compute state [%s]", documentSelfLink));
                });

        return host.sendWithDeferredResult(op);
    }

    private void readData(BufferedReader in, boolean simulatedIOException) throws IOException, InterruptedException {

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            if (simulatedIOException) {
                throw new IOException("Simulated IOException from an IT test.");
            }

            try {
                String inputLine  = in.readLine();

                ObjectMapper mapper = new ObjectMapper();
                Events event = mapper.readValue(inputLine, Events.class);

                if (EVENT_TYPE_CONTAINER.equals(event.getType())) {
                    ContainerState cs = new ContainerState();
                    if (EVENT_TYPE_CONTAINER_DIE.equals(event.getAction())) {
                        logger.fine(inputLine);

                        cs.powerState = ContainerState.PowerState.STOPPED;
                    } else if (EVENT_TYPE_CONTAINER_START.equals(event.getAction())) {
                        logger.fine(inputLine);

                        cs.powerState = ContainerState.PowerState.RUNNING;
                        cs.started = TimeUnit.NANOSECONDS.toMillis(event.getTimeNano());
                    } else {
                        continue;
                    }

                    String containerId = event.getId();

                    QueryTask queryTask = QueryUtil
                            .buildPropertyQuery(ContainerState.class, ContainerState.FIELD_NAME_ID, containerId);

                    new ServiceDocumentQuery<ContainerState>(host, ContainerState.class).query(queryTask, (r) -> {
                        if (r.hasException()) {
                            logger.warning(String.format("Failed to query resource container state with id [%s]",
                                    containerId));
                        } else if (r.hasResult()) {
                            cs.documentSelfLink = r.getDocumentSelfLink();
                            patchContainerState(cs);
                        }
                    });
                }
            } catch (SocketTimeoutException e) {
                logger.fine("No events have been received. Unblocking the reader.");
            }
        }
    }

    private boolean maxAllowedNumberOfThreadsExceeded() {
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
        if (tpe.getActiveCount() >= tpe.getMaximumPoolSize()) {
            logger.warning(String.format("Maximum allowed number of threads exceeded! Use [\"%s\"] property to increase it.",
                    THREAD_POOL_QUEUE_SIZE_PROP_NAME));
            return true;
        }

        return false;
    }

    private DeferredResult<Void> queryExistingContainerStates(String containerHostLink) {
        QueryTask queryTask = QueryUtil.buildPropertyQuery(ContainerState.class,
                ContainerState.FIELD_NAME_PARENT_LINK, containerHostLink);

        DeferredResult<Void> df = new DeferredResult<>();

        new ServiceDocumentQuery<ContainerState>(host, ContainerState.class)
                .query(queryTask, processContainerStatesQueryResults(df));

        return df;
    }

    private Consumer<ServiceDocumentQuery.ServiceDocumentQueryElementResult<ContainerState>> processContainerStatesQueryResults(DeferredResult<?> df) {
        List<String> existingContainerStateLinks = new ArrayList<>();

        return (ServiceDocumentQuery.ServiceDocumentQueryElementResult<ContainerState> r) -> {
            if (r.hasException()) {
                logger.warning(
                        String.format("Failed to query resource container state [%s]", r.getDocumentSelfLink()));

                df.fail(r.getException());
            } else if (r.hasResult()) {
                existingContainerStateLinks.add(r.getDocumentSelfLink());
            } else {
                List<DeferredResult<Operation>> dfs = new ArrayList<>();

                for (String csSelfLink : existingContainerStateLinks) {
                    ContainerState cs = new ContainerState();
                    cs.documentSelfLink = csSelfLink;
                    cs.powerState = ContainerState.PowerState.UNKNOWN;

                    dfs.add(patchContainerState(cs));
                }

                DeferredResult.allOf(dfs).whenComplete((o, e) -> {
                    df.complete(null);
                });
            }
        };
    }

    private DeferredResult<Operation> patchContainerState(ContainerState cs) {
        Operation operation = Operation.createPatch(host, UriUtils.buildUriPath(cs.documentSelfLink))
                .setBody(cs)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logger.warning(String.format("Error patching container state [%s]. Error: [%s]", cs.documentSelfLink, ex.getMessage()));
                    }
                });

        return host.sendWithDeferredResult(operation);
    }
}