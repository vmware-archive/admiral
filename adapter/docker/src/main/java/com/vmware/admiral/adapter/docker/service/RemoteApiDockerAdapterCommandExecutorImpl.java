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

import static com.vmware.admiral.compute.ContainerHostService.SSL_TRUST_ALIAS_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.SSL_TRUST_CERT_PROP_NAME;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import com.vmware.admiral.adapter.docker.util.DockerStreamUtil;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.CertificateUtil;
import com.vmware.admiral.common.util.DelegatingX509KeyManager;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.service.common.SslTrustImportService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Docker command executor implementation based on DCP and the docker remote API
 */
public class RemoteApiDockerAdapterCommandExecutorImpl implements
        DockerAdapterCommandExecutor {

    private static final Logger logger = Logger
            .getLogger(RemoteApiDockerAdapterCommandExecutorImpl.class.getName());

    private static final int SSL_TRUST_RETRIES_COUNT = Integer.getInteger(
            "com.vmware.admiral.adapter.ssltrust.delegate.retries", 5);
    private static final long SSL_TRUST_RETRIES_WAIT = Long.getLong(
            "com.vmware.admiral.adapter.ssltrust.delegate.retries.wait.millis", 500);

    private static RemoteApiDockerAdapterCommandExecutorImpl INSTANCE;

    private static final Pattern ERROR_PATTERN = Pattern.compile("\"error\":\"(.*)\"");
    private final ServiceHost host;
    private final ServiceClient serviceClient;
    // Used for commands like exec start
    private final ServiceClient attachServiceClient;
    private final DelegatingX509KeyManager keyManager = new DelegatingX509KeyManager();
    private ServerX509TrustManager trustManager;

    private final int DOCKER_REQUEST_PAYLOAD_SIZE_LIMIT = Integer.getInteger(
            "adapter.docker.api.client.request_payload_limit", 1024 * 1024 * 256);

    public final int DOCKER_REQUEST_TIMEOUT_SECONDS = Integer.getInteger(
            "adapter.docker.api.client.request_timeout_seconds", 60 * 2);

    private final int DOCKER_IMAGE_REQUEST_TIMEOUT_SECONDS = Integer.getInteger(
            "adapter.docker.api.client.image_request_timeout_seconds", 60 * 10);

    protected RemoteApiDockerAdapterCommandExecutorImpl(ServiceHost host,
            final TrustManager trustManager) {
        this.host = host;
        this.serviceClient = ServiceClientFactory.createServiceClient(
                trustManager, keyManager, DOCKER_REQUEST_PAYLOAD_SIZE_LIMIT);
        this.attachServiceClient = ServiceClientFactory.createServiceClient(
                trustManager, keyManager, DOCKER_REQUEST_PAYLOAD_SIZE_LIMIT);

        if (trustManager instanceof ServerX509TrustManager) {
            this.trustManager = (ServerX509TrustManager) trustManager;
        }
    }

    public static synchronized RemoteApiDockerAdapterCommandExecutorImpl create(
            ServiceHost host, final TrustManager trustManager) {
        if (INSTANCE == null) {
            INSTANCE = new RemoteApiDockerAdapterCommandExecutorImpl(host, trustManager);
        }
        return INSTANCE;
    }

    @Override
    public void loadImage(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        Object imageData = input.getProperties().get(DOCKER_IMAGE_DATA_PROP_NAME);
        URI targetUri = UriUtils.extendUri(input.getDockerUri(), "/images/load");
        sendPost(targetUri, imageData, true, completionHandler);
    }

    @Override
    public void createImage(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        URI targetUri = UriUtils.extendUri(input.getDockerUri(), "/images/create");
        // extract X-Registry-Auth as a request header
        String registryAuth = (String) input.getProperties().remove(DOCKER_IMAGE_REGISTRY_AUTH);

        // convert the properties to URL parameters, the way /images/create likes them
        // expect fromImage or fromSrc
        targetUri = extendUriWithQuery(targetUri, input);

        Operation op = Operation.createPost(targetUri)
                .setBody("")
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

        prepareRequest(op, true);
        serviceClient.send(op);
    }

    @Override
    public void createContainer(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        URI targetUri = UriUtils.extendUri(input.getDockerUri(), "/containers/create");
        String containerName = (String) input.getProperties().get(DOCKER_CONTAINER_NAME_PROP_NAME);
        if (containerName != null) {
            targetUri = UriUtils.extendUriWithQuery(targetUri, "name",
                    containerName);
        }

        sendPost(targetUri, input.getProperties(), false, completionHandler);
    }

    @Override
    public void startContainer(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/containers/%s/start", input
                .getProperties().get(DOCKER_CONTAINER_ID_PROP_NAME));

        sendPost(UriUtils.extendUri(input.getDockerUri(), path), null, false, completionHandler);
    }

    @Override
    public void stopContainer(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/containers/%s/stop", input
                .getProperties().get(DOCKER_CONTAINER_ID_PROP_NAME));

        sendPost(UriUtils.extendUri(input.getDockerUri(), path), null, false, completionHandler);
    }

    @Override
    public void inspectContainer(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/containers/%s/json", input
                .getProperties().get(DOCKER_CONTAINER_ID_PROP_NAME));

        sendGet(UriUtils.extendUri(input.getDockerUri(), path), input.getProperties(),
                completionHandler);
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

        sendGet(uri, input.getProperties(), completionHandler);
    }

    @Override
    public void removeContainer(CommandInput input,
            CompletionHandler completionHandler) {

        createOrUpdateTargetSsl(input);

        String path = String.format("/containers/%s", input
                .getProperties().get(DOCKER_CONTAINER_ID_PROP_NAME));

        // add v=1 and force=1 query parameters
        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        uri = UriUtils.extendUriWithQuery(uri, "v", "1", "force", "1");

        sendDelete(uri, completionHandler);
    }

    @Override
    public void fetchContainerLog(CommandInput input, CompletionHandler completionHandler) {
        // Update certificates
        createOrUpdateTargetSsl(input);

        // Construct the request path for logs
        String requestPath = String.format("/containers/%s/logs",
                input.getProperties().remove(DOCKER_CONTAINER_ID_PROP_NAME));
        URI targetUri = UriUtils.extendUri(input.getDockerUri(), requestPath);
        // append all the query parameters which are sent as input.
        targetUri = extendUriWithQuery(targetUri, input);

        sendGet(targetUri, null, completionHandler);
    }

    @Override
    public void hostPing(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        Operation op = Operation
                .createGet(UriUtils.extendUri(input.getDockerUri(), "/_ping"))
                .setCompletion(completionHandler);

        prepareRequest(op, false);
        op.setExpiration(ServiceUtils.getExpirationTimeFromNowInMicros(
                TimeUnit.SECONDS.toMicros(10)));
        serviceClient.send(op);
    }

    @Override
    public void hostInfo(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        // Make sure that the trusted certificate is loaded before proceeding to avoid
        // SSLHandshakeException and getting hosts in DISABLED state
        ensureTrustDelegateExists(input, SSL_TRUST_RETRIES_COUNT, () -> {
            sendGet(UriUtils.extendUri(input.getDockerUri(), "/info"), null, completionHandler);
        });
    }

    @Override
    public void hostVersion(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        sendGet(UriUtils.extendUri(input.getDockerUri(), "/version"), null, completionHandler);
    }

    @Override
    public void listContainers(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        URI uri = UriUtils.extendUri(input.getDockerUri(), "/containers/json");
        uri = UriUtils.extendUriWithQuery(uri, "all", "1");
        sendGet(uri, null, completionHandler);
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/#create-a-network
     * Mandatory properties for <code>input</code>:
     * <li>{@link DockerAdapterCommandExecutor#DOCKER_NETWORK_NAME_PROP_NAME}
     */
    @Override
    public void createNetwork(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        URI targetUri = UriUtils.extendUri(input.getDockerUri(), "/networks/create");

        sendPost(targetUri, input.getProperties(), false, completionHandler);
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/#remove-a-network
     */
    @Override
    public void removeNetwork(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/networks/%s", input
                .getProperties().get(DOCKER_NETWORK_ID_PROP_NAME));

        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        // TODO there is no force remove for networks. All connected containers must be disconnected
        // or stopped for this to succeed

        sendDelete(uri, completionHandler);
    }

    @Override
    public void stop() {
        if (this.serviceClient != null) {
            this.serviceClient.stop();
        }
    }

    @Override
    public void handleMaintenance(Operation post) {
        if (attachServiceClient != null) {
            attachServiceClient.handleMaintenance(post);
        }
        if (serviceClient != null) {
            serviceClient.handleMaintenance(post);
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

        URI createtUri = UriUtils.extendUri(input.getDockerUri(),
                String.format("/containers/%s/exec", containerId));

        String attachStdErr = (String) input.getProperties().remove(
                DOCKER_EXEC_ATTACH_STDERR_PROP_NAME);
        String attachStdOut = (String) input.getProperties().remove(
                DOCKER_EXEC_ATTACH_STDOUT_PROP_NAME);

        Map<String, Object> create = new HashMap<String, Object>();
        create.put(DOCKER_EXEC_ATTACH_STDIN_PROP_NAME, false);
        create.put(DOCKER_EXEC_ATTACH_STDERR_PROP_NAME,
                attachStdErr != null ? Boolean.valueOf(attachStdErr) : true);
        create.put(DOCKER_EXEC_ATTACH_STDOUT_PROP_NAME,
                attachStdOut != null ? Boolean.valueOf(attachStdOut) : true);
        create.put(DOCKER_EXEC_TTY_PROP_NAME, false);
        create.put(DOCKER_EXEC_COMMAND_PROP_NAME, command);

        sendPost(createtUri, create, false, (o, e) -> {
            if (e != null) {
                completionHandler.handle(o, e);
            } else {
                @SuppressWarnings("unchecked")
                Map<String, String> result = o.getBody(Map.class);
                String execId = result.get(DOCKER_EXEC_ID_PROP_NAME);
                startExec(input, execId, completionHandler);
            }
        });
    }

    private void startExec(CommandInput input, String execId, CompletionHandler completionHandler) {
        Map<String, Object> startBody = new HashMap<String, Object>();
        startBody.put(DOCKER_EXEC_TTY_PROP_NAME, false);
        startBody.put(DOCKER_EXEC_DETACH_PROP_NAME, false);

        URI startUri = UriUtils.extendUri(input.getDockerUri(),
                String.format("/exec/%s/start", execId));

        sendPostAttach(startUri, startBody, (op, ex) -> {
            if (ex != null) {
                completionHandler.handle(null, ex);
            } else {
                if (op.hasBody()) {
                    String body;

                    Object rawBody = op.getBodyRaw();
                    /* when the shell command does not return anything */
                    if (startBody.equals(rawBody)) {
                        body = "";
                    } else if (rawBody instanceof byte[]) {
                        try {
                            body = DockerStreamUtil.decodeFullRawResponce((byte[]) rawBody);
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
                    op.setBody(body);
                }
                completionHandler.handle(op, null);
            }
        });
    }

    /**
     * Constructs the URI query parameters with the set of input properties from the command.
     */
    private URI extendUriWithQuery(URI targetUri, CommandInput input) {
        List<String> parameters = new ArrayList<String>();
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
        if (input.getCredentials() == null) {
            return;
        }

        if (!isSecure(input.getDockerUri())) {
            return;
        }

        String clientKey = input.getCredentials().privateKey;
        String clientCert = input.getCredentials().publicKey;
        String alias = input.getDockerUri().toString().toLowerCase();

        // TODO use an LRU cache to limit the number of stored
        // KeyManagers while minimizing time wasted repeatedly
        // recreating them
        if (clientKey != null && !clientKey.isEmpty()) {
            X509ExtendedKeyManager delegateKeyManager = (X509ExtendedKeyManager) CertificateUtil
                    .getKeyManagers(alias, clientKey, clientCert)[0];
            keyManager.putDelegate(alias, delegateKeyManager);
        }

        String sslTrust = (String) input.getProperties().get(SSL_TRUST_CERT_PROP_NAME);

        if (sslTrust != null && trustManager != null) {
            String trustAlias = (String) input.getProperties().get(SSL_TRUST_ALIAS_PROP_NAME);

            trustManager.putDelegate(trustAlias, sslTrust);
        }
    }

    private boolean isSecure(URI dockerUri) {
        AssertUtil.assertNotNull(dockerUri, "dockerUri");

        return UriUtils.HTTPS_SCHEME.equalsIgnoreCase(dockerUri.getScheme());
    }

    private void ensureTrustDelegateExists(CommandInput input, int retryCount, Runnable callback) {
        if (trustManager != null) {
            String trustAlias = SslTrustImportService.getCertSelfLink(input.getDockerUri());
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
        sendRequest(Service.Action.GET, uri, body, completionHandler, false, false);
    }

    private void sendPost(URI uri, Object body, boolean largeBody,
            CompletionHandler completionHandler) {

        if (!largeBody) {
            logger.finest(() -> String.format(
                    "Sending POST to %s with body (possibly truncated):\n---\n%1.1024s\n---\n",
                    uri, Utils.toJsonHtml(body)));
        } else {
            logger.finest(String.format("Sending POST to %s with large body", uri));
        }
        sendRequest(Service.Action.POST, uri, body, completionHandler, false, largeBody);
    }

    private void sendPostAttach(URI uri, Object body, CompletionHandler completionHandler) {
        logger.finest(() -> String
                .format(
                        "Sending POST for attach to %s with body (possibly truncated):\n---\n%1.1024s\n---\n",
                        uri, Utils.toJsonHtml(body)));
        sendRequest(Service.Action.POST, uri, body, completionHandler, true, false);
    }

    private void sendDelete(URI uri, CompletionHandler completionHandler) {
        sendRequest(Service.Action.DELETE, uri, null, completionHandler, false, false);
    }

    private void sendRequest(Service.Action action, URI uri, Object body,
            CompletionHandler completionHandler, boolean useAttach, boolean longRunningRequest) {
        // with createGet the authorization context is propagated to the completion handler...
        Operation op = Operation.createGet(uri)
                .setAction(action)
                .setBody(body)
                .setCompletion(completionHandler);

        prepareRequest(op, longRunningRequest);
        if (useAttach) {
            attachServiceClient.send(op);
        } else {
            serviceClient.send(op);
        }
    }

    /* Common settings on all outgoing requests to the docker server */
    protected void prepareRequest(Operation op, boolean longRunningRequest) {
        op.setReferer(URI.create("/"));
        op.forceRemote();

        if (op.getExpirationMicrosUtc() == 0) {
            long timeout;
            if (longRunningRequest) {
                timeout = TimeUnit.SECONDS.toMicros(DOCKER_IMAGE_REQUEST_TIMEOUT_SECONDS);
            } else {
                timeout = TimeUnit.SECONDS.toMicros(DOCKER_REQUEST_TIMEOUT_SECONDS);
            }

            logger.fine(String.format("Timeout for %s is %s ms", op.getUri(), timeout));
            op.setExpiration(ServiceUtils.getExpirationTimeFromNowInMicros(timeout));
        }
    }
}
